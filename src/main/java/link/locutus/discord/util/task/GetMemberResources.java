package link.locutus.discord.util.task;

import link.locutus.discord.Locutus;
import link.locutus.discord.util.MathMan;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.apiv1.domains.subdomains.AllianceMembersContainer;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class GetMemberResources implements Callable<Map<Integer, Map<ResourceType, Double>>> {
    private final int allianceId;
    private final PoliticsAndWarV2 api;

    public GetMemberResources(int allianceId) {
        this.allianceId = allianceId;
        this.api = Locutus.imp().getApi(allianceId);
    }

    @Override
    public Map<Integer, Map<ResourceType, Double>> call() throws IOException {
        List<AllianceMembersContainer> members = api.getAllianceMembers(allianceId).getNations();
        Map<Integer, Map<ResourceType, Double>> membersRss = new HashMap<>();

        for (AllianceMembersContainer member : members) {
            membersRss.put(member.getNationId(), adapt(member));
        }

        return membersRss;
    }

    public static Map<ResourceType, Double> adapt(AllianceMembersContainer member) {
        String memberJson = new Gson().toJson(member);
        JsonObject memberObj = new JsonParser().parse(memberJson).getAsJsonObject();

        Map<ResourceType, Double> rssMap = new HashMap<>();
        for (ResourceType type : ResourceType.values()) {
            JsonElement value = memberObj.get(type.getName());
            if (value != null) {
                double current = MathMan.parseDouble(value.getAsString());
                rssMap.put(type, Math.max(0, current));
            }
        }

        return rssMap;
    }
}