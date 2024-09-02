package link.locutus.discord.apiv3.subscription;

import com.pusher.client.Pusher;
import com.pusher.client.channel.*;
import link.locutus.discord.Logg;

import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;

public enum PusherChannelType {
    DEFAULT {
        @Override
        public Channel subscribe(Pusher pusher, String channelName) {
            return pusher.subscribe(channelName);
        }

        @Override
        public void bind(Channel channel, PnwPusherModel model, PnwPusherEvent event, boolean bulk, SubscriptionEventListener listener, BiConsumer<String, Exception> error) {
            String name = getEventName(model, event, bulk);
            channel.bind(name, listener);
        }
    },
    PRIVATE {
        @Override
        public Channel subscribe(Pusher pusher, String channelName) {
            return pusher.subscribePrivate(channelName, new PrivateChannelEventListener() {
                @Override
                public void onAuthenticationFailure(String message, Exception e) {
                    e.printStackTrace();
                    Logg.text("Pusher: Private channel authentication failure: " + message + " | " + e);
                }

                @Override
                public void onSubscriptionSucceeded(String channelName) {
                    Logg.text("Pusher: Private channel subscription succeeded: " + channelName);
                }

                @Override
                public void onEvent(PusherEvent event) {

                }
            });
        }

        @Override
        public void bind(Channel channel, PnwPusherModel model, PnwPusherEvent event, boolean bulk, SubscriptionEventListener listener, BiConsumer<String, Exception> error) {
            String name = getEventName(model, event, bulk);
            if (!(listener instanceof PrivateChannelEventListener)) {
                SubscriptionEventListener parent = listener;
                listener = new PrivateChannelEventListener() {
                    @Override
                    public void onAuthenticationFailure(String message, Exception e) {
                        error.accept(message, e);
                    }

                    @Override
                    public void onSubscriptionSucceeded(String channelName) {
                        Logg.text("channel connected: " + channelName);
                    }

                    @Override
                    public void onEvent(PusherEvent event) {
                        parent.onEvent(event);
                    }
                };
            }
            channel.bind(name, listener);
        }
    },
    PRESENCE {
        @Override
        public Channel subscribe(Pusher pusher, String channelName) {
            return pusher.subscribePresence(channelName);
        }

        @Override
        public void bind(Channel channel, PnwPusherModel model, PnwPusherEvent event, boolean bulk, SubscriptionEventListener listener, BiConsumer<String, Exception> error) {
            String name = getEventName(model, event, bulk);
            if (!(listener instanceof PrivateChannelEventListener)) {
                SubscriptionEventListener parent = listener;
                listener = new PresenceChannelEventListener() {
                    @Override
                    public void onUsersInformationReceived(String channelName, Set<User> users) {
                        // TODO
                    }

                    @Override
                    public void userSubscribed(String channelName, User user) {
                        // TODO
                    }

                    @Override
                    public void userUnsubscribed(String channelName, User user) {
                        // TODO
                    }

                    @Override
                    public void onAuthenticationFailure(String message, Exception e) {
                        error.accept(message, e);
                    }

                    @Override
                    public void onSubscriptionSucceeded(String channelName) {
                        Logg.text("channel connected: " + channelName);
                    }

                    @Override
                    public void onEvent(PusherEvent event) {
                        parent.onEvent(event);
                    }
                };
            }
            channel.bind(name, listener);
        }
    },

    ;

    public static String getEventName(PnwPusherModel model, PnwPusherEvent event, boolean bulk) {
        return (bulk ? "BULK_" : "") + model.name().toUpperCase(Locale.ROOT) + "_" + event.name().toUpperCase(Locale.ROOT);
    }

    PusherChannelType() {

    }

    public abstract Channel subscribe(Pusher pusher, String channelName);

    public abstract void bind(Channel channel, PnwPusherModel model, PnwPusherEvent event, boolean bulk, SubscriptionEventListener listener, BiConsumer<String, Exception> error);
}
