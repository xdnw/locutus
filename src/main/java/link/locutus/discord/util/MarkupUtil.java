package link.locutus.discord.util;

import com.overzealous.remark.Remark;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.entities.grant.TemplateTypes;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.kefirsf.bb.BBProcessorFactory;
import org.kefirsf.bb.ConfigurationFactory;
import org.kefirsf.bb.TextProcessor;
import org.primeframework.transformer.domain.Document;
import org.primeframework.transformer.service.BBCodeParser;
import org.primeframework.transformer.service.BBCodeToHTMLTransformer;
import org.primeframework.transformer.service.Transformer;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkupUtil {
    public static String markdownUrl(String name, String url) {
        return String.format("[%s](%s)", name, url);
    }

    private static final Pattern QUOTED_COMMAND = Pattern.compile("`/([^`]+?)`");
    private static final Pattern MENTIONED_COMMAND = Pattern.compile("</([^>0-9:]+?):[0-9]{11,21}>");
    public static String formatQuotedCommands(String input) {
        Matcher m = QUOTED_COMMAND.matcher(input);
        StringBuffer sb = new StringBuffer();
        while(m.find()) {
            String found = m.group(1);
            List<String> split = StringMan.split(found, ' ');
            CommandCallable cmd = Locutus.cmd().getV2().getCommands().getCallable(split, true);
            if (cmd != null) {
                String url = WebRoot.REDIRECT + "/command/" + cmd.getFullPath("/");
                String remaining = found.substring(cmd.getFullPath().length()).trim();
                if (!remaining.isEmpty() && cmd instanceof ParametricCallable param) {
                    Map<String, String> args = CommandManager2.parseArguments(param.getUserParameterMap().keySet(), remaining, false);
                    List<BasicNameValuePair> pairs = args.entrySet().stream().map(entry -> new BasicNameValuePair(entry.getKey(), entry.getValue())).toList();
                    url += "?" + URLEncodedUtils.format(pairs, "UTF-8");
                }
                m.appendReplacement(sb, String.format("[/%s](%s)", found, url));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String formatMentionedCommands(String input) {
        Matcher m = MENTIONED_COMMAND.matcher(input);
        StringBuffer sb = new StringBuffer();
        while(m.find()) {
            String found = m.group(1);
            List<String> split = StringMan.split(found, ' ');
            CommandCallable cmd = Locutus.cmd().getV2().getCommands().getCallable(split, true);
            if (cmd != null) {
                String url = WebRoot.REDIRECT + "/command/" + cmd.getFullPath("/");
                if (!split.isEmpty() && cmd instanceof ParametricCallable param) {
                    Map<String, String> args = param.formatArgumentsToMap(WebRoot.getInstance().getPageHandler().getStore(), split);
                    List<BasicNameValuePair> pairs = args.entrySet().stream().map(entry -> new BasicNameValuePair(entry.getKey(), entry.getValue())).toList();
                    url += "?" + URLEncodedUtils.format(pairs, "UTF-8");
                }
                m.appendReplacement(sb, String.format("[/%s](%s)", found, url));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String formatDiscordMarkdown(String input) {
        input = formatQuotedCommands(input);
        input = formatMentionedCommands(input);
        return input;
    }

    public static String stripImageReferences(String markdown) {
        Pattern pattern = Pattern.compile("\\[.*?]:\\s.*?(\\r?\\n|$)");
        return pattern.matcher(markdown).replaceAll("");
    }

    public static String removeImages(String markdown) {
        return markdown.replaceAll("!\\[.*?]\\(.*?\\)", "").replaceAll("!\\[.*?]\\[\\]", "");
    }

    public static String htmlUrl(String name, String url) {
        return String.format("<a href=\"%s\">%s</a>", url, name);
    }

    public static String sheetUrl(String name, String url) {
        return "=HYPERLINK(\"" + url + "\", \"" + name + "\")";
    }

    public static String bbCodeToMarkdown(String source) {
        String result = bbcodeToHTML(htmlToMarkdown(source));
        return result == null ? source : result;
    }

    public static String spoiler(String title, String body) {
        return String.format("<details><summary>%s</summary>%s</details>", title, body);
    }

    public static String pathName(String string) {
        return string.replace(" ", "-").replaceAll("[^a-zA-Z0-9_-]", "");
    }

    public static String messageToHtml(MessageEmbed embed) {
        StringBuilder r = new StringBuilder();
        r.append("<div style=\"background-color:#EEE;padding:5px;border:4px groove #CCC;\" class=\"img-rounded\">");
        String title = embed.getTitle();
        if (embed.getUrl() != null && !embed.getUrl().isEmpty()) {
            title = htmlUrl(title, embed.getUrl());
        }
        r.append("<h4>" + title + "</h4>");
        r.append("<div class=\"row\">"); // content
        if (embed.getDescription() != null && !embed.getDescription().isEmpty()) {
            r.append("<div class=\"col-sm-12\">" + markdownToHTML(embed.getDescription()) + "</div>");
        }
        List<MessageEmbed.Field> fields = embed.getFields();
        if (!fields.isEmpty()) {
            for (MessageEmbed.Field field : fields) {
                String separator = field.isInline() ? ": " : "<br>";
                r.append("<div class=\"col-sm-4\"><b>" + field.getName() + "</b>" + separator + field.getValue() +"</div>");
            }
        }

        r.append("</div>");


        MessageEmbed.Footer footer = embed.getFooter();
        if (footer != null && footer.getText() != null) {
            r.append("<small>" + footer.getText() + "</small>");
        }
        r.append("</div>");
        return r.toString();
    }

    public static String messageToHtml(String content, List<MessageEmbed> embeds, Map<String, String> files) {
        List<String> responses = new ArrayList<>();
        if (content != null && !content.isEmpty()) {
            responses.add(markdownToHTML(transformURLIntoMarkup(content)));
        }
        if (embeds != null && !embeds.isEmpty()) {
            for (MessageEmbed embed : embeds) {
                responses.add(messageToHtml(embed));
            }
        }
        return StringMan.join(responses, "<br>");
    }

    public static String markdownToBBCode(String source) {
//        source = source.replace("https://politicsandwar.com", "https://tinyurl.com/borg404");
        source = source.replaceAll("([^`])`([^`])", "$1'$2");
        source = source.replaceAll("_", "\u200B");
        String result = htmlToBBCode(markdownToHTML(source));
        if (result == null) return source;
        result = StringEscapeUtils.unescapeHtml4(result);
        result = result.replace("\u200B", "_");
        return result;
    }

    public static String unescapeMarkdown(String input) {
        // Replace escaped characters
        String unescaped = input.replaceAll("\\\\([*_{}\\[\\]()#+.!|\\-])", "$1");

        // Replace HTML entities
        unescaped = unescaped.replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'");

        return unescaped;
    }

    public static String htmlColor(String color, String text) {
        return "<span style='color:" + color + "'>" + text + "</span>";
    }

    public static String bbcodeToHTML(String source) {
        Document document = new BBCodeParser().buildDocument(source, null);
        String html = new BBCodeToHTMLTransformer().transform(document, (node) -> {
            // transform predicate, returning false will cause this node to not be transformed
            return true;
        }, new Transformer.TransformFunction.HTMLTransformFunction(), null);
        return html;
    }

    public static String htmlToMarkdown(String source) {
        Remark remark = new Remark();
        return remark.convert(source);
    }

    public static String transformURLIntoLinks(String text){
        String urlValidationRegex = "\\b((https?|ftp):\\/\\/)?[-a-zA-Z0-9@:%._\\+~#=]{2,256}\\.[A-Za-z]{2,6}\\b(\\/[-a-zA-Z0-9@:%_\\+.~#?&//=]*)*(?:\\/|\\b)";
        Pattern p = Pattern.compile(urlValidationRegex);
        Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();
        while(m.find()){
            String found =m.group(0);
            if (m.start() > 0 && text.charAt(m.start() - 1) != '"') {
                m.appendReplacement(sb, "<a href=\"" + found + "\">" + found + "</a>");
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String transformURLIntoMarkup(String text){
        String urlValidationRegex = "\\b((https?|ftp):\\/\\/)?[-a-zA-Z0-9@:%._\\+~#=]{2,256}\\.[A-Za-z]{2,6}\\b(\\/[-a-zA-Z0-9@:%_\\+.~#?&//=]*)*(?:\\/|\\b)";
        Pattern p = Pattern.compile(urlValidationRegex);
        Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();
        while(m.find()){
            int start = m.start();
            int end = m.end();
            if (start > 0 && text.charAt(start - 1) == '<' && end < text.length() && text.charAt(end) == '>') {
                start--;
                end++;
            }
            String found = text.substring(start, end);
            if (start > 0 && text.charAt(start - 1) != '"') {
                m.appendReplacement(sb, "[" + found + "](" + found + ")");
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String markdownToHTML(String source) {
        source = source.replace("_", "\u200B\t").replace(" * ", "\u200B\r");
        source = source.replaceAll("```", "`");
        TextProcessor processor = BBProcessorFactory.getInstance()
                .createFromResource(ConfigurationFactory.MARKDOWN_CONFIGURATION_FILE);
        source = processor.process(source);
        source = source.replace("\n", "<br>").replace("\u200B\r", " * ").replace("\u200B\t", "_");
        source = transformURLIntoLinks(source);
        return source;
    }

    public static String htmlToBBCode(String source) {
        source = StringEscapeUtils.unescapeHtml4(source);

        for (Map.Entry<String, String> entry: htmlMap.entrySet())
        {
            source = source.replaceAll(entry.getKey(), entry.getValue());
        }

        return source;
    }

    private static final Map<String, String> htmlMap = new HashMap<String, String>();

    static {
        /* lowercase */

        // br
        htmlMap.put("<br/>", "\n");
        htmlMap.put("<br>", "\n");
        htmlMap.put("<p>", "");
        htmlMap.put("</p>", "");
        htmlMap.put("<strong>", "[b]");
        htmlMap.put("</strong>", "[/b]");
        htmlMap.put("<em>", "[i]");
        htmlMap.put("</em>", "[/i]");
        htmlMap.put("<code>", "[quote]");
        htmlMap.put("</code>", "[/quote]");

        // hr
        htmlMap.put("<hr />", "\n------\n");
        htmlMap.put("<hr>", "\n------\n");

        // strong
        htmlMap.put("<strong>(.+?)</strong>", "\\[b\\]$1\\[/b\\]");
        htmlMap.put("<b>(.+?)</b>", "\\[b\\]$1\\[/b\\]");

        // italic
        htmlMap.put("<i>(.+?)</i>", "\\[i\\]$1\\[/i\\]");
        htmlMap.put("<span style='font-style:italic;'>(.+?)</span>", "\\[i\\]$1\\[/i\\]");
        htmlMap.put("<span style=\"font-style:italic;\">(.+?)</span>", "\\[i\\]$1\\[/i\\]");

        // underline
        htmlMap.put("<u>(.+?)</u>", "\\[u\\]$1\\[/u\\]");
        htmlMap.put("<span style='text-decoration:underline;'>(.+?)</span>", "\\[u\\]$1\\[/u\\]");
        htmlMap.put("<span style=\"text-decoration:underline;\">(.+?)</span>", "\\[u\\]$1\\[/u\\]");

        // h title
        htmlMap.put("<h1>(.+?)</h1>", "\\[h1\\]$1\\[/h1\\]");
        htmlMap.put("<h2>(.+?)</h2>", "\\[h2\\]$1\\[/h2\\]");
        htmlMap.put("<h3>(.+?)</h3>", "\\[h3\\]$1\\[/h3\\]");
        htmlMap.put("<h4>(.+?)</h4>", "\\[h4\\]$1\\[/h4\\]");
        htmlMap.put("<h5>(.+?)</h5>", "\\[h5\\]$1\\[/h5\\]");
        htmlMap.put("<h6>(.+?)</h6>", "\\[h6\\]$1\\[/h6\\]");

        // blockquote
        htmlMap.put("<blockquote>(.+?)</blockquote>", "\\[quote\\]$1\\[/quote\\]");

        // p & aligns
        htmlMap.put("<p>(.+?)</p>", "\\[p\\](.+?)\\[/p\\]");
        htmlMap.put("<p style='text-indent:(.+?)px;line-height:(.+?)%;'>(.+?)</p>", "\\[p=$1,$2\\]$3\\[/p\\]");
        htmlMap.put("<div align='center'>(.+?)</div>", "\\[center\\]$1\\[/center\\]");
        htmlMap.put("<div align=\"center\">(.+?)</div>", "\\[center\\]$1\\[/center\\]");
        htmlMap.put("<p align='center'>(.+?)</p>", "\\[center\\]$1\\[/center\\]");
        htmlMap.put("<p align=\"center\">(.+?)</p>", "\\[center\\]$1\\[/center\\]");
        htmlMap.put("<div align='(.+?)'>(.+?)", "\\[align=$1\\]$2\\[/align\\]");
        htmlMap.put("<div align=\"(.+?)\">(.+?)", "\\[align=$1\\]$2\\[/align\\]");

        // fonts
        htmlMap.put("<span style='color:(.+?);'>(.+?)</span>", "\\[color=$1\\]$2\\[/color\\]");
        htmlMap.put("<span style=\"color:(.+?);\">(.+?)</span>", "\\[color=$1\\]$2\\[/color\\]");
        htmlMap.put("<span style='font-size:(.+?);'>(.+?)</span>", "\\[size=$1\\]$2\\[/size\\]");
        htmlMap.put("<span style=\"font-size:(.+?);\">(.+?)</span>", "\\[size=$1\\]$2\\[/size\\]");
        htmlMap.put("<font color=\"(.+?);\">(.+?)</span>", "\\[color=$1\\]$2\\[/color\\]");
        htmlMap.put("<font color='(.+?);'>(.+?)</span>", "\\[color=$1\\]$2\\[/color\\]");
        htmlMap.put("<font face=\"(.+?);\">(.+?)</span>", "$2");
        htmlMap.put("<font face='(.+?);'>(.+?)</span>", "$2]");
        htmlMap.put("<font face='(.+?);' color=\"(.+?);\">(.+?)</span>", "\\[color=$2\\]$3\\[/color\\]");
        htmlMap.put("<font face='(.+?);' color='(.+?);'>(.+?)</span>", "\\[color=$2\\]$3\\[/color\\]");
        htmlMap.put("<font color=\"(.+?);\" face=\"(.+?)\">(.+?)</span>", "\\[color=$1\\]$3\\[/color\\]");
        htmlMap.put("<font color='(.+?);' face='(.+?);'>(.+?)</span>", "\\[color=$1\\]$3\\[/color\\]");

        // images
        htmlMap.put("<img src='(.+?)' />", "\\[img\\]$1\\[/img\\]");
        htmlMap.put("<img src=\"(.+?)\" />", "\\[img\\]$1\\[/img\\]");
        htmlMap.put("<img width='(.+?)' height='(.+?)' src='(.+?)' />", "\\[img=$1,$2\\]$3\\[/img\\]");
        htmlMap.put("<img width=\"(.+?)\" height=\"(.+?)\" src=\"(.+?)\" />", "\\[img=$1,$2\\]$3\\[/img\\]");
        htmlMap.put("<img src='(.+?)'>", "\\[img\\]$1\\[/img\\]");
        htmlMap.put("<img src=\"(.+?)\">", "\\[img\\]$1\\[/img\\]");
        htmlMap.put("<img width='(.+?)' height='(.+?)' src='(.+?)'>", "\\[img=$1,$2\\]$3\\[/img\\]");
        htmlMap.put("<img width=\"(.+?)\" height=\"(.+?)\" src=\"(.+?)\">", "\\[img=$1,$2\\]$3\\[/img\\]");

        // links & mails
        htmlMap.put("<a href='mailto:(.+?)'>(.+?)</a>", "\\[email=$1\\]$2\\[/email\\]");
        ;
        htmlMap.put("<a href=\"mailto:(.+?)\">(.+?)</a>", "\\[email=$1\\]$2\\[/email\\]");
        ;
        htmlMap.put("<a href='(.+?)'>(.+?)</a>", "\\[url=$1\\]$2\\[/url\\]");
        htmlMap.put("<a href=\"(.+?)\">(.+?)</a>", "\\[url=$1\\]$2\\[/url\\]");

        // videos
        htmlMap.put("<object width='(.+?)' height='(.+?)'><param name='(.+?)' value='http://www.youtube.com/v/(.+?)'></param><embed src='http://www.youtube.com/v/(.+?)' type='(.+?)' width='(.+?)' height='(.+?)'></embed></object>", "\\[youtube\\]$4\\[/youtube\\]");
        htmlMap.put("<object width=\"(.+?)\" height=\"(.+?)\"><param name=\"(.+?)\" value=\"http://www.youtube.com/v/(.+?)\"></param><embed src=\"http://www.youtube.com/v/(.+?)\" type=\"(.+?)\" width=\"(.+?)\" height=\"(.+?)\"></embed></object>", "\\[youtube\\]$4\\[/youtube\\]");
        htmlMap.put("<video src='(.+?)' />", "\\[video\\]$1\\[/video\\]");
        htmlMap.put("<video src=\"(.+?)\" />", "\\[video\\]$1\\[/video\\]");
        htmlMap.put("<video src='(.+?)'>", "\\[video\\]$1\\[/video\\]");
        htmlMap.put("<video src=\"(.+?)\">", "\\[video\\]$1\\[/video\\]");


        /* UPPERCASE */

        // BR
        htmlMap.put("<BR />", "\n");
        htmlMap.put("<BR>", "\n");

        // HR
        htmlMap.put("<HR>", "[HR]");
        htmlMap.put("<HR />", "[HR]");

        // STRONG
        htmlMap.put("<STRONG>(.+?)</STRONG>", "\\[B\\]$1\\[/B\\]");
        htmlMap.put("<B>(.+?)</B>", "\\[B\\]$1\\[/B\\]");

        // ITALIC
        htmlMap.put("<I>(.+?)</I>", "\\[I\\]$1\\[/I\\]");
        htmlMap.put("<SPAN STYLE='font-style:italic;'>(.+?)</SPAN>", "\\[I\\]$1\\[/I\\]");
        htmlMap.put("<SPAN STYLE=\"font-style:italic;\">(.+?)</SPAN>", "\\[I\\]$1\\[/I\\]");

        // UNDERLINE
        htmlMap.put("<U>(.+?)</U>", "\\[U\\]$1\\[/U\\]");
        htmlMap.put("<SPAN STYLE='text-decoration:underline;'>(.+?)</SPAN>", "\\[U\\]$1\\[/U\\]");
        htmlMap.put("<SPAN STYLE=\"text-decoration:underline;\">(.+?)</SPAN>", "\\[U\\]$1\\[/U\\]");

        // H TITLE
        htmlMap.put("<H1>(.+?)</H1>", "\\[H1\\]$1\\[/H1\\]");
        htmlMap.put("<H2>(.+?)</H2>", "\\[H2\\]$1\\[/H2\\]");
        htmlMap.put("<H3>(.+?)</H3>", "\\[H3\\]$1\\[/H3\\]");
        htmlMap.put("<H4>(.+?)</H4>", "\\[H4\\]$1\\[/H4\\]");
        htmlMap.put("<H5>(.+?)</H5>", "\\[H5\\]$1\\[/H5\\]");
        htmlMap.put("<H6>(.+?)</H6>", "\\[H6\\]$1\\[/H6\\]");

        // BLOCKQUOTE
        htmlMap.put("<BLOCKQUOTE>(.+?)</BLOCKQUOTE>", "\\[QUOTE\\]$1\\[/QUOTE\\]");

        // P & ALIGNS
        htmlMap.put("<P>(.+?)</P>", "\\[P\\](.+?)\\[/P\\]");
        htmlMap.put("<P STYLE='text-indent:(.+?)px;line-height:(.+?)%;'>(.+?)</P>", "\\[P=$1,$2\\]$3\\[/P\\]");
        htmlMap.put("<DIV ALIGN='CENTER'>(.+?)</DIV>", "\\[CENTER\\]$1\\[/CENTER\\]");
        htmlMap.put("<DIV ALIGN=\"CENTER\">(.+?)</DIV>", "\\[CENTER\\]$1\\[/CENTER\\]");
        htmlMap.put("<P ALIGN='CENTER'>(.+?)</P>", "\\[CENTER\\]$1\\[/CENTER\\]");
        htmlMap.put("<P ALIGN=\"CENTER\">(.+?)</P>", "\\[CENTER\\]$1\\[/CENTER\\]");
        htmlMap.put("<DIV ALIGN='(.+?)'>(.+?)", "\\[ALIGN=$1\\]$2\\[/ALIGN\\]");
        htmlMap.put("<DIV ALIGN=\"(.+?)\">(.+?)", "\\[ALIGN=$1\\]$2\\[/ALIGN\\]");

        // FONTS
        htmlMap.put("<SPAN STYLE='color:(.+?);'>(.+?)</SPAN>", "\\[COLOR=$1\\]$2\\[/COLOR\\]");
        htmlMap.put("<SPAN STYLE=\"color:(.+?);\">(.+?)</SPAN>", "\\[COLOR=$1\\]$2\\[/COLOR\\]");
        htmlMap.put("<SPAN STYLE='font-size:(.+?);'>(.+?)</SPAN>", "\\[SIZE=$1\\]$2\\[/SIZE\\]");
        htmlMap.put("<SPAN STYLE=\"font-size:(.+?);\">(.+?)</SPAN>", "\\[SIZE=$1\\]$2\\[/SIZE\\]");
        htmlMap.put("<FONT COLOR=\"(.+?);\">(.+?)</SPAN>", "\\[COLOR=$1\\]$2\\[/COLOR\\]");
        htmlMap.put("<FONT COLOR='(.+?);'>(.+?)</SPAN>", "\\[COLOR=$1\\]$2\\[/COLOR\\]");
        htmlMap.put("<FONT FACE=\"(.+?);\">(.+?)</SPAN>", "$2");
        htmlMap.put("<FONT FACE='(.+?);'>(.+?)</SPAN>", "$2]");
        htmlMap.put("<FONT FACE='(.+?);' COLOR=\"(.+?);\">(.+?)</SPAN>", "\\[COLOR=$2\\]$3\\[/COLOR\\]");
        htmlMap.put("<FONT FACE='(.+?);' COLOR='(.+?);'>(.+?)</SPAN>", "\\[COLOR=$2\\]$3\\[/COLOR\\]");
        htmlMap.put("<FONT COLOR=\"(.+?);\" FACE=\"(.+?)\">(.+?)</SPAN>", "\\[COLOR=$1\\]$3\\[/COLOR\\]");
        htmlMap.put("<FONT COLOR='(.+?);' FACE='(.+?);'>(.+?)</SPAN>", "\\[COLOR=$1\\]$3\\[/COLOR\\]");

        // IMAGES
        htmlMap.put("<IMG SRC='(.+?)' />", "\\[IMG\\]$1\\[/IMG\\]");
        htmlMap.put("<IMG SRC=\"(.+?)\" />", "\\[IMG\\]$1\\[/IMG\\]");
        htmlMap.put("<IMG WIDTH='(.+?)' HEIGHT='(.+?)' SRC='(.+?)' />", "\\[IMG=$1,$2\\]$3\\[/IMG\\]");
        htmlMap.put("<IMG WIDTH=\"(.+?)\" HEIGHT=\"(.+?)\" SRC=\"(.+?)\" />", "\\[IMG=$1,$2\\]$3\\[/IMG\\]");
        htmlMap.put("<IMG SRC='(.+?)'>", "\\[IMG\\]$1\\[/IMG\\]");
        htmlMap.put("<IMG SRC=\"(.+?)\">", "\\[IMG\\]$1\\[/IMG\\]");
        htmlMap.put("<IMG WIDTH='(.+?)' HEIGHT='(.+?)' SRC='(.+?)'>", "\\[IMG=$1,$2\\]$3\\[/IMG\\]");
        htmlMap.put("<IMG WIDTH=\"(.+?)\" HEIGHT=\"(.+?)\" SRC=\"(.+?)\">", "\\[IMG=$1,$2\\]$3\\[/IMG\\]");

        // LINKS & MAILS
        htmlMap.put("<A HREF='mailto:(.+?)'>(.+?)</A>", "\\[EMAIL=$1\\]$2\\[/EMAIL\\]");
        ;
        htmlMap.put("<A HREF=\"mailto:(.+?)\">(.+?)</A>", "\\[EMAIL=$1\\]$2\\[/EMAIL\\]");
        ;
        htmlMap.put("<A HREF='(.+?)'>(.+?)</A>", "\\[URL=$1\\]$2\\[/URL\\]");
        htmlMap.put("<A HREF=\"(.+?)\">(.+?)</A>", "\\[URL=$1\\]$2\\[/URL\\]");

        // VIDEOS
        htmlMap.put("<OBJECT WIDTH='(.+?)' HEIGHT='(.+?)'><PARAM NAME='(.+?)' VALUE='HTTP://WWW.YOUTUBE.COM/V/(.+?)'></PARAM><EMBED SRC='http://www.youtube.com/v/(.+?)' TYPE='(.+?)' WIDTH='(.+?)' HEIGHT='(.+?)'></EMBED></OBJECT>", "\\[YOUTUBE\\]$4\\[/YOUTUBE\\]");
        htmlMap.put("<OBJECT WIDTH=\"(.+?)\" HEIGHT=\"(.+?)\"><PARAM NAME=\"(.+?)\" VALUE=\"HTTP://WWW.YOUTUBE.COM/V/(.+?)\"></PARAM><EMBED SRC=\"http://www.youtube.com/v/(.+?)\" TYPE=\"(.+?)\" WIDTH=\"(.+?)\" HEIGHT=\"(.+?)\"></EMBED></OBJECT>", "\\[YOUTUBE\\]$4\\[/YOUTUBE\\]");
        htmlMap.put("<VIDEO SRC='(.+?)' />", "\\[VIDEO\\]$1\\[/VIDEO\\]");
        htmlMap.put("<VIDEO SRC=\"(.+?)\" />", "\\[VIDEO\\]$1\\[/VIDEO\\]");
        htmlMap.put("<VIDEO SRC='(.+?)'>", "\\[VIDEO\\]$1\\[/VIDEO\\]");
        htmlMap.put("<VIDEO SRC=\"(.+?)\">", "\\[VIDEO\\]$1\\[/VIDEO\\]");
    }

    private static final Map<String, String> bbMap = new HashMap<String, String>();

    static {
        /* lowercase */

        bbMap.put("\n", "<br />");
        bbMap.put("\\[b\\](.+?)\\[/b\\]", "<strong>$1</strong>");
        bbMap.put("\\[i\\](.+?)\\[/i\\]", "<i>$1</i>");
        bbMap.put("\\[u\\](.+?)\\[/u\\]", "<u>$1</u>");
        bbMap.put("\\[h1\\](.+?)\\[/h1\\]", "<h1>$1</h1>");
        bbMap.put("\\[h2\\](.+?)\\[/h2\\]", "<h2>$1</h2>");
        bbMap.put("\\[h3\\](.+?)\\[/h3\\]", "<h3>$1</h3>");
        bbMap.put("\\[h4\\](.+?)\\[/h4\\]", "<h4>$1</h4>");
        bbMap.put("\\[h5\\](.+?)\\[/h5\\]", "<h5>$1</h5>");
        bbMap.put("\\[h6\\](.+?)\\[/h6\\]", "<h6>$1</h6>");
        bbMap.put("\\[quote\\](.+?)\\[/quote\\]", "<blockquote>$1</blockquote>");
        bbMap.put("\\[p\\](.+?)\\[/p\\]", "<p>$1</p>");
        bbMap.put("\\[p=(.+?),(.+?)\\](.+?)\\[/p\\]", "<p style=\"text-indent:$1px;line-height:$2%;\">$3</p>");
        bbMap.put("\\[center\\](.+?)\\[/center\\]", "<div align=\"center\">$1");
        bbMap.put("\\[align=(.+?)\\](.+?)\\[/align\\]", "<div align=\"$1\">$2");
        bbMap.put("\\[color=(.+?)\\](.+?)\\[/color\\]", "<span style=\"color:$1;\">$2</span>");
        bbMap.put("\\[size=(.+?)\\](.+?)\\[/size\\]", "<span style=\"font-size:$1;\">$2</span>");
        bbMap.put("\\[img\\](.+?)\\[/img\\]", "<img src=\"$1\" />");
        bbMap.put("\\[img=(.+?),(.+?)\\](.+?)\\[/img\\]", "<img width=\"$1\" height=\"$2\" src=\"$3\" />");
        bbMap.put("\\[email\\](.+?)\\[/email\\]", "<a href=\"mailto:$1\">$1</a>");
        bbMap.put("\\[email=(.+?)\\](.+?)\\[/email\\]", "<a href=\"mailto:$1\">$2</a>");
        bbMap.put("\\[url\\](.+?)\\[/url\\]", "<a href=\"$1\">$1</a>");
        bbMap.put("\\[url=(.+?)\\](.+?)\\[/url\\]", "<a href=\"$1\">$2</a>");
        bbMap.put("\\[youtube\\](.+?)\\[/youtube\\]", "<object width=\"640\" height=\"380\"><param name=\"movie\" value=\"http://www.youtube.com/v/$1\"></param><embed src=\"http://www.youtube.com/v/$1\" type=\"application/x-shockwave-flash\" width=\"640\" height=\"380\"></embed></object>");
        bbMap.put("\\[video\\](.+?)\\[/video\\]", "<video src=\"$1\" />");


        /* UPPERCASE */

        bbMap.put("\\[B\\](.+?)\\[/B\\]", "<STRONG>$1</STRONG>");
        bbMap.put("\\[I\\](.+?)\\[/I\\]", "<I>$1</I>");
        bbMap.put("\\[U\\](.+?)\\[/U\\]", "<U>$1</U>");
        bbMap.put("\\[H1\\](.+?)\\[/H1\\]", "<H1>$1</H1>");
        bbMap.put("\\[H2\\](.+?)\\[/H2\\]", "<H2>$1</H2>");
        bbMap.put("\\[H3\\](.+?)\\[/H3\\]", "<H3>$1</H3>");
        bbMap.put("\\[H4\\](.+?)\\[/H4\\]", "<H4>$1</H4>");
        bbMap.put("\\[H5\\](.+?)\\[/H5\\]", "<H5>$1</H5>");
        bbMap.put("\\[H6\\](.+?)\\[/H6\\]", "<H6>$1</H6>");
        bbMap.put("\\[QUOTE\\](.+?)\\[/QUOTE\\]", "<BLOCKQUOTE>$1</BLOCKQUOTE>");
        bbMap.put("\\[P\\](.+?)\\[/P\\]", "<P>$1</P>");
        bbMap.put("\\[P=(.+?),(.+?)\\](.+?)\\[/P\\]", "<P STYLE=\"TEXT-INDENT:$1PX;LINE-HEIGHT:$2%;\">$3</P>");
        bbMap.put("\\[CENTER\\](.+?)\\[/CENTER\\]", "<DIV ALIGN=\"CENTER\">$1");
        bbMap.put("\\[ALIGN=(.+?)\\](.+?)\\[/ALIGN\\]", "<DIV ALIGN=\"$1\">$2");
        bbMap.put("\\[COLOR=(.+?)\\](.+?)\\[/COLOR\\]", "<SPAN STYLE=\"COLOR:$1;\">$2</SPAN>");
        bbMap.put("\\[SIZE=(.+?)\\](.+?)\\[/SIZE\\]", "<SPAN STYLE=\"FONT-SIZE:$1;\">$2</SPAN>");
        bbMap.put("\\[IMG\\](.+?)\\[/IMG\\]", "<IMG SRC=\"$1\" />");
        bbMap.put("\\[IMG=(.+?),(.+?)\\](.+?)\\[/IMG\\]", "<IMG WIDTH=\"$1\" HEIGHT=\"$2\" SRC=\"$3\" />");
        bbMap.put("\\[EMAIL\\](.+?)\\[/EMAIL\\]", "<A HREF=\"MAILTO:$1\">$1</A>");
        bbMap.put("\\[EMAIL=(.+?)\\](.+?)\\[/EMAIL\\]", "<A HREF=\"MAILTO:$1\">$2</A>");
        bbMap.put("\\[URL\\](.+?)\\[/URL\\]", "<A HREF=\"$1\">$1</A>");
        bbMap.put("\\[URL=(.+?)\\](.+?)\\[/URL\\]", "<A HREF=\"$1\">$2</A>");
        bbMap.put("\\[YOUTUBE\\](.+?)\\[/YOUTUBE\\]", "<OBJECT WIDTH=\"640\" HEIGHT=\"380\"><PARAM NAME=\"MOVIE\" VALUE=\"HTTP://WWW.YOUTUBE.COM/V/$1\"></PARAM><EMBED SRC=\"HTTP://WWW.YOUTUBE.COM/V/$1\" TYPE=\"APPLICATION/X-SHOCKWAVE-FLASH\" WIDTH=\"640\" HEIGHT=\"380\"></EMBED></OBJECT>");
        bbMap.put("\\[VIDEO\\](.+?)\\[/VIDEO\\]", "<VIDEO SRC=\"$1\" />");
    }

    public static String list(Object... values) {
        return "- " + StringMan.join(values, "\n- ");
    }
}
