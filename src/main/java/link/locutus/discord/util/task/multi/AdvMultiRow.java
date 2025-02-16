package link.locutus.discord.util.task.multi;

public record AdvMultiRow(
        int id,
        String Nation,
        int alliance_id,
        String alliance,
        int age,
        int cities,
        int shared_ips,
        Double shared_percent,
        Double shared_nation_percent,
        boolean same_ip,
        boolean banned,
        Long login_diff,
        Double same_activity_percent,
        Double percentOnline,
        String discord,
        boolean discord_linked,
        boolean irl_verified,
        int customization
) {}