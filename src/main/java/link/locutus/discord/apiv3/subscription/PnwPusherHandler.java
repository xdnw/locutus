package link.locutus.discord.apiv3.subscription;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.Channel;
import com.pusher.client.channel.SubscriptionEventListener;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import com.pusher.client.util.HttpChannelAuthorizer;
import com.pusher.client.util.HttpUserAuthenticator;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.io.PagePriority;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PnwPusherHandler {

    public static final String HOST = "socket.politicsandwar.com";
    public static final String AUTH_ENDPOINT = "https://api.politicsandwar.com/subscriptions/v1/auth";

    public static final String CHANNEL_ENDPOINT = "https://api.politicsandwar.com/subscriptions/v1/subscribe/{model}/{event}?api_key={key}";

    private Pusher pusher;
    private final ObjectMapper objectMapper;
    public PnwPusherHandler() {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        DateFormat df2 = new SimpleDateFormat("yyyy-MM-dd");
        this.objectMapper = Jackson2ObjectMapperBuilder.json().featuresToEnable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS).dateFormat(df).build();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Instant.class, new JsonDeserializer<Instant>() {
            @Override
            public Instant deserialize(com.fasterxml.jackson.core.JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
                String text = p.getText();
                try {
                    return df.parse(text).toInstant();
                } catch (ParseException e) {
                    return Instant.parse(text);
                }
            }
        });
        module.addDeserializer(Date.class, new JsonDeserializer<Date>() {
            @Override
            public Date deserialize(com.fasterxml.jackson.core.JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
                try {
                    return df2.parse(p.getText());
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        objectMapper.registerModule(module);
    }

    public <T> PnwPusherBuilder<T> subscribeBuilder(String pnwApiKey, Class<T> clazz, PnwPusherEvent event) {
        PnwPusherModel model = PnwPusherModel.valueOf(clazz);
        return new PnwPusherBuilder<T>(this, pnwApiKey, clazz, model, event);
    }

    private void bind(String channelName, PnwPusherModel model, PnwPusherEvent event, boolean bulk, SubscriptionEventListener listener) {
        PusherChannelType type = PusherChannelType.DEFAULT;
        String typeStr = channelName.split("-")[0];
        try {
            type = PusherChannelType.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignore) {
            ignore.printStackTrace();
        }

        Channel channel = type.subscribe(pusher, channelName);
        type.bind(channel, model, event, bulk, listener, new BiConsumer<String, Exception>() {
            @Override
            public void accept(String s, Exception e) {
                System.out.println("Error " + s);
                e.printStackTrace();
                AlertUtil.error(s, e);
            }
        });
    }

    public PnwPusherHandler connect() {
        return connect(null, null);
    }

    public PnwPusherHandler connect(BiConsumer<String, Exception> onError, Consumer<ConnectionStateChange> onChange) {
        if (pusher == null) {

            PusherOptions options = new PusherOptions()
                    .setUserAuthenticator(new HttpUserAuthenticator(AUTH_ENDPOINT))
                    .setHost(HOST)
                    .setChannelAuthorizer(new HttpChannelAuthorizer(AUTH_ENDPOINT))
                    ;
            this.pusher = new Pusher("a22734a47847a64386c8", options);

            pusher.connect(new ConnectionEventListener() {
                @Override
                public void onConnectionStateChange(ConnectionStateChange change) {
                    Logg.text("State changed to " + change.getCurrentState() +
                            " from " + change.getPreviousState());
                    if (onChange != null) onChange.accept(change);
                }

                @Override
                public void onError(String message, String code, Exception e) {
                    if (onError != null) onError.accept(message, e);
                    if (e != null) {
                        e.printStackTrace();
                    }
                    Logg.text("There was a problem connecting! " + message + " | " + code);
                }
            }, ConnectionState.ALL);
        } else {
            pusher.connect();
        }

        return this;
    }

    public PnwPusherHandler disconnect() {
        if (pusher != null) {
            pusher.disconnect();;
        }
        return this;
    }

    public static class PnwPusherBuilder<T> {
        private final PnwPusherModel model;
        private final PnwPusherEvent event;

        private final Map<PnwPusherFilter, List<Object>> filters;
        private final JsonParser parser;
        private final Class<T> type;
        private final String pnwKey;
        private final PnwPusherHandler parent;

        private boolean bulk;

        protected PnwPusherBuilder(PnwPusherHandler parent, String pnwKey, Class<T> type, PnwPusherModel model, PnwPusherEvent event) {
            this.parent = parent;
            this.model = model;
            this.event = event;
            this.filters = new LinkedHashMap<>();
            this.parser = new JsonParser();
            this.type = type;
            this.bulk = true;
            this.pnwKey = pnwKey.toLowerCase(Locale.ROOT);
        }

        /**
         * If events are bulk events or single (defaults to bulk)
         * @param bulk
         * @return
         */
        public PnwPusherBuilder<T> setBulk(boolean bulk) {
            this.bulk = bulk;
            return this;
        }

        public PnwPusherBuilder<T> addFilter(PnwPusherFilter filter, Object... values) {
            filters.put(filter, Arrays.asList(values));
            return this;
        }

        private String getEndpoint() {
            String url = CHANNEL_ENDPOINT
                    .replace("{model}", model.name().toLowerCase(Locale.ROOT))
                    .replace("{event}", event.name().toLowerCase(Locale.ROOT))
                    .replace("{key}", pnwKey);
            if (!filters.isEmpty()) {
                List<String> filterParams = filters.entrySet().stream().map(entry ->
                        entry.getKey().name().toLowerCase() + "=" +
                                StringMan.join(entry.getValue(), ",")).collect(Collectors.toList());
                url += "&" + StringMan.join(filterParams, ",");
            }
            Logg.text("Connecting on URL " + url);
            return url;
        }

        public PnwPusherHandler build(Consumer<List<T>> listener) {
            String channelName = getChannel();
            parent.bind(channelName, model, event, bulk, event -> {
                try {
                String data = event.getData();
                if (data.isEmpty()) return;
//                System.out.println("Received on " + channelName + ": " + data);
                    if (data.charAt(0) == '[') {
                        CollectionType listTypeRef = parent.objectMapper.getTypeFactory().constructCollectionType(List.class, type);
                        List<T> value = parent.objectMapper.readValue(data, listTypeRef);
                        listener.accept(value);
                    } else {
                        T value = parent.objectMapper.readValue(data, type);
                        listener.accept(List.of(value));
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                } catch (Throwable e) {
                    e.printStackTrace();
                    throw e;
                }
            });
            return parent;
        }

        private String getChannel() {
            try {
                String channelInfo = FileUtil.readStringFromURL(PagePriority.PUSHER, getEndpoint());
                JsonElement json = parser.parse(channelInfo);
                if (json.isJsonObject()) {
                    JsonElement channel = json.getAsJsonObject().get("channel");
                    if (!channel.isJsonNull()) {
                        return channel.getAsString();
                    }
                }
                String msg = StringUtils.replaceIgnoreCase(channelInfo, pnwKey, "XXX");
                msg = msg.replaceAll("(?i)[\\[\\]\"\\n^:\\s,\\.](?=.*[A-Za-z])(?=.*\\d)[0-9A-F]{14,}(?=[\\[\\]\"\\n$:\\s,\\.]|$)", "XXX");
                throw new PnwPusherError(msg);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class PnwPusherError extends RuntimeException {
        public PnwPusherError(String msg) {
            super(msg);
        }
    }
}
