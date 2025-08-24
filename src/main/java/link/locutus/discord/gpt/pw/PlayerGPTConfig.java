package link.locutus.discord.gpt.pw;

import com.google.gson.reflect.TypeToken;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.web.WebUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class PlayerGPTConfig {
    public Map<String, Map<String, String>> getConfiguration(DBNation nation) {
        ByteBuffer gptOptBuf = nation.getMeta(NationMeta.GPT_OPTIONS);
        Map<String, Map<String, String>> gptOptions = new HashMap<>();
        if (gptOptBuf != null) {
            String json = new String(gptOptBuf.array(), StandardCharsets.UTF_8);
            gptOptions = WebUtil.GSON.fromJson(json, new TypeToken<Map<String, Map<String, String>>>(){}.getType());
        }
        return gptOptions;
    }
//
//    public void setOptions(DBNation nation, Map<String, Map<String, String>> options) {
//        String json = WebUtil.GSON.toJson(options);
//        byte[] data = json.getBytes(StandardCharsets.UTF_8);
//        nation.setMeta(NationMeta.GPT_OPTIONS, data);
//    }
//
//    public Map<String, Map<String, String>> setAndValidateOptions(DBNation nation, String modelName, Map<String, String> options) {
//        Map<String, Map<String, String>> config = getConfiguration(nation);
//        config.put(modelName, options);
//        setOptions(nation, config);
//        return config;
//    }
//
//    public Map<String, String> getOptions(DBNation nation, String modelName) {
//        Map<String, Map<String, String>> allOptions = getConfiguration(nation);
//        return allOptions.getOrDefault(modelName, new HashMap<>());
//    }
}
