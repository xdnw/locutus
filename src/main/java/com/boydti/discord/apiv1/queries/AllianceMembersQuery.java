package com.boydti.discord.apiv1.queries;

import com.boydti.discord.apiv1.core.UrlBuilder;
import com.boydti.discord.apiv1.domains.AllianceMembers;
import com.boydti.discord.apiv1.enums.QueryURL;

public class AllianceMembersQuery extends Query {

    public AllianceMembersQuery(Integer aid, String apiKey) {
        super(Integer.toString(aid), apiKey);
    }

    @Override
    public ApiQuery build() {
        String url = UrlBuilder.build(QueryURL.ALLIANCE_MEMBERS_URL, args);

        return new ApiQuery<>(url, new AllianceMembers());
    }
}
