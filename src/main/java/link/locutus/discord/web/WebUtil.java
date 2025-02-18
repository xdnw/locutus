package link.locutus.discord.web;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import in.wilsonl.minifyhtml.Configuration;
import in.wilsonl.minifyhtml.MinifyHtml;
import io.javalin.http.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.commands.manager.v2.binding.annotation.Filter;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.scheduler.QuadConsumer;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import link.locutus.discord.util.task.mail.Mail;
import link.locutus.discord.util.task.mail.MailApiResponse;
import link.locutus.discord.util.task.mail.MailApiSuccess;
import link.locutus.discord.util.task.mail.SearchMailTask;
import link.locutus.discord.web.commands.binding.DBAuthRecord;
import link.locutus.discord.web.jooby.WebRoot;
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WebUtil {
    public static final Gson GSON = new Gson();

    public static <T> String generateSearchableDropdown(ParameterData param, Collection<T> objects, QuadConsumer<T, JsonArray, JsonArray, JsonArray> consumeObjectNamesValueSubtext) {
        return generateSearchableDropdown(param, objects, consumeObjectNamesValueSubtext, null);
    }

    public static String mailLogin(DBNation nation, boolean backend, boolean allowExisting) throws IOException {
        ApiKeyPool pool = ApiKeyPool.create(Locutus.loader().getNationId(), Locutus.loader().getApiKey());
        return mailLogin(pool, nation, backend, allowExisting);
    }

    public static Map<String, Object> convertToMap(Object obj) {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        return gson.fromJson(gson.toJson(obj), type);
    }

    public static String mailLogin(ApiKeyPool pool, DBNation nation, boolean backend, boolean allowExisting) throws IOException {
        DBAuthRecord existing = null;
        if (allowExisting) {
            existing = WebRoot.db().get(nation.getNation_id());
        }
        if (existing == null) {
            UUID uuid = WebUtil.generateSecureUUID();
            existing = WebRoot.db().updateToken(uuid, nation.getNation_id(), null);
        }
        UUID uuid = existing.token;
        String authUrl;
        if (backend) {
            authUrl = WebRoot.REDIRECT + "/page/login?token=" + uuid;
        } else {
            authUrl = Settings.INSTANCE.WEB.FRONTEND_DOMAIN + "/#login/" + uuid;
        }
        String title = "Login | timestamp:" + System.currentTimeMillis();
        String body = "<b>DO NOT SHARE THIS URL OR OPEN IT IF YOU DID NOT REQUEST IT:</b><br>" +
                MarkupUtil.htmlUrl(WebRoot.REDIRECT + " | Verify Login", authUrl);

        MailApiResponse result = nation.sendMail(pool, title, body, true);
        if (result.status() == MailApiSuccess.SUCCESS) {
            List<Mail> mails = new SearchMailTask(Locutus.imp().getRootAuth(), title, true, true, false, null).call();
            String mailUrl;
            if (mails.size() > 0) {
                mailUrl = Settings.INSTANCE.PNW_URL() + "/inbox/message/id=" + mails.get(0).id;
            } else {
                mailUrl = Settings.INSTANCE.PNW_URL() + "/inbox/";
            }
            return mailUrl;
        } else {
            throw new IllegalArgumentException("Could not send mail to nation: " + nation.getNation_id() + " | " + result.status() + " | " + result.error());
        }
    }

    public static int getPortOrSchemeDefault(String url) {
        try {
            URL obj = new URL(url);
            int result = obj.getPort();
            if (result == -1) {
                return obj.getProtocol().equals("https") ? 443 : 80;
            } else {
                return result;
            }
        } catch (MalformedURLException e) {
            return 443;
        }
    }

    public static UUID generateSecureUUID() {
        SecureRandom random = new SecureRandom();
        return new UUID(random.nextLong(), random.nextLong());
    }

    public static void setCookie(Context context, String key, String value, int duration) {
        Cookie cookie = new Cookie(key, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(context.scheme().equals("https"));
        try {
            String domain = new URI(Settings.INSTANCE.WEB.BACKEND_DOMAIN).getHost();
            cookie.setDomain("." + domain);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        cookie.setPath("/");
        cookie.setSameSite(SameSite.STRICT);
        cookie.setMaxAge(duration);

        context.cookie(cookie);
    }

    public static void setCookieViaHeader(Context context, String key, String value, int duration, boolean secure, String domain) {
        String newCookie = key + "=" + value + "; " + (duration <= 0 ? "" : "Max-Age=" + duration + "; ") + "Path=/; ";
        if (secure) {
            newCookie += (context.scheme().equals("https") ? "Secure; " : "") + "HttpOnly; ";
            newCookie += "SameSite=Strict;";
        } else {
            newCookie += "SameSite=Lax;";
        }
        if (domain != null) {
            newCookie += " Domain=" + domain.replaceFirst("^https?://", "") + ";";
        }
        context.header("Set-Cookie", newCookie);
    }

    public static <T> String generateSearchableDropdown(ParameterData param, Collection<T> objects, QuadConsumer<T, JsonArray, JsonArray, JsonArray> consumeObjectNamesValueSubtext, Boolean multiple) {
        String name = param.getName();
        String desc = param.getExpandedDescription();
        String def = param.getDefaultValueString();
        boolean required = !param.isOptional() && !param.isFlag();
        Type type = param.getType();
        if (multiple == null) {
            multiple = type instanceof Class && Collection.class.isAssignableFrom((Class) type);
            if (type instanceof ParameterizedType) {
                Type rawType = ((ParameterizedType) type).getRawType();
                multiple = rawType instanceof Class && Collection.class.isAssignableFrom((Class) rawType);
            }
        }

        return generateSearchableDropdown(name, desc, def, required, objects, consumeObjectNamesValueSubtext, multiple);
    }

    public static <T> String generateSearchableDropdown(String name, String desc, String def, boolean required, Collection<T> objects, QuadConsumer<T, JsonArray, JsonArray, JsonArray> consumeObjectNamesValueSubtext) {
        return generateSearchableDropdown(name, desc, def, required, objects, consumeObjectNamesValueSubtext, false);
    }

    public static <T> String generateSearchableDropdown(String name, String desc, String def, boolean required, Collection<T> objects, QuadConsumer<T, JsonArray, JsonArray, JsonArray> consumeObjectNamesValueSubtext, boolean multiple) {
        JsonArray names = new JsonArray();
        JsonArray values = new JsonArray();
        JsonArray subtext = new JsonArray();
        for (T object : objects) {
            consumeObjectNamesValueSubtext.consume(object, names, values, subtext);
        }
        if (names.equals(values)) {
            values = new JsonArray();
        }
        JsonObject dataJson = new JsonObject();
        if (names.size() > 0) dataJson.add("names", names);
        if (values.size() > 0) dataJson.add("values", values);
        if (subtext.size() > 0) dataJson.add("subtext", subtext);

        String valueStr = def != null ? " value=\"" + def + "\"" : "";
        UUID uuid = UUID.randomUUID();
        String jsonStr = dataJson.toString().replace("'", "&#39;").replace("&", "&amp;");
        return wrapLabel(null, desc, uuid, "<select id=\"" + uuid + "\" name=\"" + name + "\" class=\"select-inline-data form-control form-control-sm\" " + valueStr + " data-json='" + jsonStr + "' " + (required ? "required" : "") + (multiple ? " multiple" : "") + " ></select>", InlineMode.NONE);
    }

    public static String getColorHex(Color color) {
        return "#" + Integer.toHexString(color.getRGB()).substring(2);
    }

    public static String wrapLabel(ParameterData param, UUID uuid, String input, InlineMode inline) {
        return wrapLabel(null, param.getExpandedDescription(), uuid, input, inline);
    }

    public static String wrapLabel(String name, String desc, UUID uuid, String input, InlineMode mode) {
        desc = MarkupUtil.markdownToHTML(desc);
        if (desc.contains("<br>")) {
            mode = InlineMode.NONE;
        } else if (mode != InlineMode.NONE) {
            desc = Jsoup.parse(desc).text();
        }
        switch (mode) {
            default:
            case NONE:
                return "<div class=\"form-group bg-light mt-1 p-1 rounded shadow-sm border\"><label class=\"col-form-label-sm\" " + (uuid != null ? "for=\"" + uuid.toString() + "\"" : "") + ">" + (name != null && !name.isEmpty() ? ("<b>" + name + ": </b>") : "") + desc + "</label><div class=\"col-sm\">" + input + "</div></div>";
            case BEFORE:
                return "<div class=\"form-group bg-light mt-1 rounded form-floating shadow-sm border\"><label class=\"col-form-label-sm\" " + (uuid != null ? "for=\"" + uuid.toString() + "\"" : "") + ">" + (name != null && !name.isEmpty() ? ("<b>" + name + ": </b>") : "") + desc + "</label>" + input + "</div>";
            case AFTER:
                return "<div class=\"form-group bg-light mt-1 rounded shadow-sm border\"><div class=\"col-sm d-inline\">" + input + "</div><label class=\"col-form-label-sm d-inline\" " + (uuid != null ? "for=\"" + uuid.toString() + "\"" : "") + ">" + (name != null && !name.isEmpty() ? ("<b>" + name + ": </b>") : "") + desc + "</label></div>";
        }
    }

    private static Configuration cfg;

    public static String minify(String msg) {
        if (!Settings.INSTANCE.WEB.MINIFY) return msg;
        if (cfg == null) {
            cfg = new Configuration.Builder()
                    .setKeepHtmlAndHeadOpeningTags(false)
                    .setMinifyCss(true)
                    .setDoNotMinifyDoctype(false)
                    .setEnsureSpecCompliantUnquotedAttributeValues(false)
                    .setKeepClosingTags(false)
                    .setKeepInputTypeTextAttr(false)
                    .setKeepSpacesBetweenAttributes(false)
                    .setKeepComments(false)
                    .setKeepSsiComments(false)
                    .setMinifyJs(true)
                    .setPreserveBraceTemplateSyntax(false)
                    .setPreserveChevronPercentTemplateSyntax(false)
                    .build();
        }
        return MinifyHtml.minify(msg, cfg);
    }

    public enum InlineMode {
        NONE,
        BEFORE,
        AFTER
    }

    public enum InputType {
        checkbox(InlineMode.AFTER),
        radio(InlineMode.AFTER),
        number(InlineMode.BEFORE),
        color(InlineMode.BEFORE),
        text(InlineMode.BEFORE),
        date(InlineMode.NONE),
        textarea(InlineMode.NONE)
        ;

        public final InlineMode inline;

        InputType(InlineMode mode) {
            this.inline = mode;
        }
    }

    public static String createInput(InputType type, ParameterData param, String... attributes) {
        return createInput("input", type, param, attributes);
    }

    public static String createInputWithClass(String tag, InputType type, ParameterData param, String clazzes, boolean close, String... attributes) {
        String attributesStr = StringMan.join(attributes, " ");
        String def = param.getDefaultValueString();
        if ("%epoch%".equals(def)) def = "0";
        String valueStr = def != null ? "value=\"" + def + "\"" : "";

        String prefix = "";
        String suffix = "";
        String clazz;
        if (type == InputType.checkbox || type == InputType.radio) {
            clazz = "form-check-input";
            prefix = "<label class=\"switch\"><input type=\"hidden\" name=\"" + param.getName() + "\" value=\"0\">";
            suffix = "<span class=\"slider round\"></span></label>";
            valueStr = "value=\"1\" ";
            if (def != null && def.equalsIgnoreCase("true")) valueStr += " checked";
            valueStr += " onclick=\"this.previousSibling.value=1-this.previousSibling.value\"";
        } else {
            prefix = "";
            clazz = "form-control form-control-sm";
        }
        if (clazzes != null) clazz += "," + clazzes;

        Filter filter = param.getAnnotation(Filter.class);
        if (filter != null) {
            valueStr += " pattern=\"" + filter.value() + "\"";
        }

        UUID uuid = UUID.randomUUID();
        return wrapLabel(param, uuid, prefix + "<" + tag + " type='" + type + "' id=\"" + uuid + "\" name='" + param.getName() + "' " + valueStr + " " + (param.isOptional() || param.isFlag() ? "" : "required") + " " + attributesStr + " class=\"" + clazz + "\" " + (close ? "" : "/") + ">" + suffix + (close ? "</" + tag + ">" : ""), type.inline);
    }

    public static String createInput(String tag, InputType type, ParameterData param, String... attributes) {
        return createInputWithClass(tag, type, param, null, false, attributes);
    }

    public static byte[] downloadToBytes(CompletableFuture<InputStream> future) throws IOException {
        try (InputStream inputStream = future.get();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        } catch (Exception e) {
            throw new IOException("Failed to download and convert to bytes", e);
        }
    }

    public static String tableUrl(Class type, String sel, List<String> columns) {
        List<String> params = new ObjectArrayList<>(List.of("type", type.getSimpleName(), "sel", sel));
        for (String column : columns) {
            params.add("col");
            params.add(column);
        }
        return WebUtil.frontendUrl("view_table", params);
    }

    public static String frontendUrl(String path, List<String> params) {
        return frontendUrl(path, params.toArray(new String[0]));
    }

    public static String frontendUrl(String path, String... params) {
        String url = Settings.INSTANCE.WEB.FRONTEND_DOMAIN;
        if (path != null) url += "/#/" + path + "/";
        if (params != null) {
            URIBuilder uriBuilder = new URIBuilder();
            for (int i = 0; i < params.length; i += 2) {
                uriBuilder.addParameter(params[i], params[i + 1]);
            }
            url += uriBuilder.toString();
        }
        return url;
    }

    public static String frontendUrl(String path, JSONObject args) {
        String url = Settings.INSTANCE.WEB.FRONTEND_DOMAIN;
        if (path != null) url += "/#/" + path + "/";
        if (args != null) {
            URIBuilder uriBuilder = new URIBuilder();
            for (String key : args.keySet()) {
                if (key.isEmpty()) continue;
                uriBuilder.addParameter(key, args.getString(key));
            }
            url += uriBuilder.toString();
        }
        return url;
    }
}
