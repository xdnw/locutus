package com.boydti.discord.apiv1.entities;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ApiRecord {
    @SerializedName("api_key")
    @Expose
    private String apiKey;
    @SerializedName("nation_id")
    @Expose
    private Integer nationId;
    @SerializedName("alliance_id")
    @Expose
    private Integer allianceId;
    @SerializedName("alliance_position")
    @Expose
    private Integer alliancePosition;
    @SerializedName("daily_requests_maximum")
    @Expose
    private Integer dailyRequestsMaximum;
    @SerializedName("daily_requests_used")
    @Expose
    private Integer dailyRequestsUsed;
    @SerializedName("daily_requests_remaining")
    @Expose
    private Integer dailyRequestsRemaining;
    @SerializedName("requests_per_second_rate_limit")
    @Expose
    private Integer requestsPerSecondRateLimit;
    @SerializedName("requests_made_this_second")
    @Expose
    private Integer requestsMadeThisSecond;

    @SerializedName("api_key")
    public String getApiKey() {
        return apiKey;
    }

    @SerializedName("api_key")
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @SerializedName("nation_id")
    public Integer getNationId() {
        return nationId;
    }

    @SerializedName("nation_id")
    public void setNationId(Integer nationId) {
        this.nationId = nationId;
    }

    @SerializedName("alliance_id")
    public Integer getAllianceId() {
        return allianceId;
    }

    @SerializedName("alliance_id")
    public void setAllianceId(Integer allianceId) {
        this.allianceId = allianceId;
    }

    @SerializedName("alliance_position")
    public Integer getAlliancePosition() {
        return alliancePosition;
    }

    @SerializedName("alliance_position")
    public void setAlliancePosition(Integer alliancePosition) {
        this.alliancePosition = alliancePosition;
    }

    @SerializedName("daily_requests_maximum")
    public Integer getDailyRequestsMaximum() {
        return dailyRequestsMaximum;
    }

    @SerializedName("daily_requests_maximum")
    public void setDailyRequestsMaximum(Integer dailyRequestsMaximum) {
        this.dailyRequestsMaximum = dailyRequestsMaximum;
    }

    @SerializedName("daily_requests_used")
    public Integer getDailyRequestsUsed() {
        return dailyRequestsUsed;
    }

    @SerializedName("daily_requests_used")
    public void setDailyRequestsUsed(Integer dailyRequestsUsed) {
        this.dailyRequestsUsed = dailyRequestsUsed;
    }

    @SerializedName("daily_requests_remaining")
    public Integer getDailyRequestsRemaining() {
        return dailyRequestsRemaining;
    }

    @SerializedName("daily_requests_remaining")
    public void setDailyRequestsRemaining(Integer dailyRequestsRemaining) {
        this.dailyRequestsRemaining = dailyRequestsRemaining;
    }

    @SerializedName("requests_per_second_rate_limit")
    public Integer getRequestsPerSecondRateLimit() {
        return requestsPerSecondRateLimit;
    }

    @SerializedName("requests_per_second_rate_limit")
    public void setRequestsPerSecondRateLimit(Integer requestsPerSecondRateLimit) {
        this.requestsPerSecondRateLimit = requestsPerSecondRateLimit;
    }

    @SerializedName("requests_made_this_second")
    public Integer getRequestsMadeThisSecond() {
        return requestsMadeThisSecond;
    }

    @SerializedName("requests_made_this_second")
    public void setRequestsMadeThisSecond(Integer requestsMadeThisSecond) {
        this.requestsMadeThisSecond = requestsMadeThisSecond;
    }

    @Override
    public String toString() {
        return "ApiRecord{" +
                "apiKey='" + apiKey + '\'' +
                ", nationId=" + nationId +
                ", allianceId=" + allianceId +
                ", alliancePosition=" + alliancePosition +
                ", dailyRequestsMaximum=" + dailyRequestsMaximum +
                ", dailyRequestsUsed=" + dailyRequestsUsed +
                ", dailyRequestsRemaining=" + dailyRequestsRemaining +
                ", requestsPerSecondRateLimit=" + requestsPerSecondRateLimit +
                ", requestsMadeThisSecond=" + requestsMadeThisSecond +
                '}';
    }
}
