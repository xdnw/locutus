package link.locutus.discord.pnw.json;

import com.google.gson.Gson;

public class CityBuildRange {
    private final CityBuild gson;
    private int min;
    private int max;
    private String build;

    public CityBuildRange(int min, int max, String buildJson) {
        this.min = min;
        this.max = max;
        this.gson = new Gson().fromJson(buildJson, CityBuild.class);
        this.build = buildJson;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public String getBuildJson() {
        return build;
    }

    public CityBuild getBuildGson() {
        return gson;
    }
}
