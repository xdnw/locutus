package link.locutus.discord.apiv3.subscription;

import com.pusher.client.PusherOptions;

public class PusherOptions7 extends PusherOptions {
    @Override
    public String buildUrl(String apiKey) {
        return super.buildUrl(apiKey).replace("protocol=5", "protocol=7");
    }
}
