package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.db.entities.metric.AllianceMetric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RankingPresentationSupport {
    private RankingPresentationSupport() {
    }

    public static String fallbackTitle(RankingResult result) {
        return humanizeTokens(result.kind().name());
    }

    public static String sectionLabel(RankingSectionKind kind) {
        return humanizeTokens(kind.name());
    }

    public static String valueLabel(RankingValueKind kind) {
        return kind == RankingValueKind.PRIMARY ? null : humanizeTokens(kind.name());
    }

    public static String title(AllianceRankingService.MetricRequest request) {
        return metricLabel(request.metric()) + " by Alliance";
    }

    public static String title(AllianceRankingService.AttributeRequest request) {
        return capitalize(request.attribute().getName()) + " by Alliance";
    }

    public static String title(AllianceRankingService.DeltaRequest request) {
        return metricLabel(request.metric()) + " change by Alliance";
    }

    public static String title(AllianceRankingService.LootRequest request) {
        return request.showTotal() ? "Alliance bank loot" : "Alliance bank loot per score";
    }

    public static String title(BaseballRankingService.Request request) {
        String subject = switch (request.valueMode()) {
            case GAMES -> "baseball games";
            case EARNINGS -> "baseball earnings";
        };
        String prefix = request.challengeOnly() ? "Challenge " : "";
        return prefix + subject + " by " + enumLabel(request.byAlliance() ? RankingEntityType.ALLIANCE : RankingEntityType.NATION);
    }

    public static String title(IncentiveRankingService.Request request) {
        return "Incentive rewards by Nation";
    }

    public static String title(NationValueRankingService.AttributeRequest request) {
        String subject = capitalize(request.attribute().getName());
        if (!request.groupByAlliance()) {
            return subject + " by " + enumLabel(RankingEntityType.NATION);
        }
        String prefix = request.total() ? "Total " : "Average ";
        return prefix + decapitalize(subject) + " by " + enumLabel(RankingEntityType.ALLIANCE);
    }

    public static String title(NationValueRankingService.ProductionRequest request) {
        RankingEntityType entityType = request.listByNation() ? RankingEntityType.NATION : RankingEntityType.ALLIANCE;
        String subject = productionSubject(request.resources());
        if (entityType == RankingEntityType.ALLIANCE) {
            String aggregationPrefix = request.listAverage() ? "Average " : "Total ";
            return aggregationPrefix + decapitalize(subject) + " by " + enumLabel(RankingEntityType.ALLIANCE);
        }
        return subject + " by " + enumLabel(RankingEntityType.NATION);
    }

    public static String title(OffshoreRankingService.PotentialRequest request) {
        return "Potential offshores for " + allianceLabel(request.allianceId())
                + " by transfer " + (request.transferCount() ? "count" : "value");
    }

    public static String title(OffshoreRankingService.ProlificRequest request) {
        return "Prolific offshores by transfer value";
    }

    public static String title(RecruitmentRankingService.Request request) {
        return "New members by Alliance";
    }

    public static String title(TradeRankingService.Request request) {
        String resource = resourceDisplayName(request.resource());
        String direction = enumLabel(request.direction()).toLowerCase(Locale.ROOT);
        String subject = request.absoluteTransfersOnly()
                ? resource + " " + direction
                : "Net " + resource + " " + direction;
        return subject + " by " + enumLabel(request.groupByAlliance() ? RankingEntityType.ALLIANCE : RankingEntityType.NATION);
    }

    public static String title(TradeProfitRankingService.Request request) {
        return "Trade profit by " + enumLabel(request.groupByAlliance() ? RankingEntityType.ALLIANCE : RankingEntityType.NATION);
    }

    public static String title(WarStatusRankingService.Request request) {
        return "War status by " + enumLabel(request.byAlliance() ? RankingEntityType.ALLIANCE : RankingEntityType.NATION);
    }

    public static String title(WarRankingService.WarCostRequest request) {
        String title = "War cost: " + enumLabel(request.type()) + " / " + warCostStatLabel(request.stat())
                + " by " + enumLabel(request.groupByAlliance() ? RankingEntityType.ALLIANCE : RankingEntityType.NATION);
        return title + normalizationSuffix(WarRankingService.normalizationMode(request.scalePerWar(), request.scalePerCity()));
    }

    public static String title(WarRankingService.WarCountRequest request) {
        List<String> qualifiers = new ArrayList<>();
        if (request.onlyOffensives()) {
            qualifiers.add("Offensive");
        } else if (request.onlyDefensives()) {
            qualifiers.add("Defensive");
        }
        if (request.warType() != null) {
            qualifiers.add(capitalize(request.warType().toString()));
        }
        if (request.statuses() != null && request.statuses().size() == 1) {
            qualifiers.add(statusLabel(request.statuses().iterator().next()));
        }
        if (request.onlyRankAttackers()) {
            qualifiers.add("attackers side");
        }

        String prefix = qualifiers.isEmpty() ? "War count" : "War count: " + String.join(" / ", qualifiers);
        return prefix + " by " + enumLabel(request.rankByNation() ? RankingEntityType.NATION : RankingEntityType.ALLIANCE)
            + (request.normalizePerMember() ? normalizationSuffix(RankingNormalizationMode.PER_MEMBER) : "");
    }

    public static String title(WarRankingService.AttackTypeRequest request) {
        String title = (request.percent() ? "Share of " : "") + attackTypeLabel(request.type());
        if (request.onlyOffWars()) {
            title += " in offensive wars";
        } else if (request.onlyDefWars()) {
            title += " in defensive wars";
        }
        return title + " by Alliance";
    }

    private static String metricLabel(AllianceMetric metric) {
        return humanizeTokens(metric.name());
    }

    private static String productionSubject(Set<ResourceType> resources) {
        if (resources.size() == 1) {
            return "Net " + resourceDisplayName(resources.iterator().next()) + " production";
        }
        return "Net production value for " + resourceScopeLabel(resources);
    }

    private static String resourceScopeLabel(Set<ResourceType> resources) {
        int realResources = 0;
        for (ResourceType type : ResourceType.values) {
            if (type != ResourceType.MONEY && type != ResourceType.CREDITS) {
                realResources++;
            }
        }
        if (resources.size() >= realResources) {
            return "all resources";
        }
        List<String> names = new ArrayList<>(resources.size());
        for (ResourceType resource : resources) {
            if (resource == ResourceType.MONEY || resource == ResourceType.CREDITS) {
                continue;
            }
            names.add(resourceDisplayName(resource));
        }
        names.sort(String::compareTo);
        return String.join(", ", names);
    }

    private static String attackTypeLabel(AttackType attackType) {
        return humanizeTokens(attackType.getName()) + " attacks";
    }

    private static String statusLabel(WarStatus status) {
        return humanizeTokens(status.name());
    }

    private static String allianceLabel(int allianceId) {
        DBAlliance alliance = DBAlliance.getOrCreate(allianceId);
        if (alliance == null || alliance.getName() == null || alliance.getName().isBlank()) {
            return "Alliance " + allianceId;
        }
        return alliance.getName();
    }

    private static String resourceDisplayName(ResourceType resourceType) {
        return capitalize(resourceType.getName());
    }

    private static String warCostStatLabel(link.locutus.discord.apiv1.enums.WarCostStat stat) {
        if (stat.unit() != null) {
            return humanizeTokens(stat.unit().name());
        }
        if (stat.resource() != null) {
            return capitalize(stat.resource().getName());
        }
        if (stat.isAttack()) {
            return attackStatLabel(stat);
        }
        return humanizeTokens(stat.name());
    }

    private static String attackStatLabel(link.locutus.discord.apiv1.enums.WarCostStat stat) {
        return switch (stat) {
            case GROUND -> attackTypeLabel(AttackType.GROUND);
            case VICTORY -> attackTypeLabel(AttackType.VICTORY);
            case FORTIFY -> attackTypeLabel(AttackType.FORTIFY);
            case A_LOOT -> attackTypeLabel(AttackType.A_LOOT);
            case AIRSTRIKE_INFRA -> attackTypeLabel(AttackType.AIRSTRIKE_INFRA);
            case AIRSTRIKE_SOLDIER -> attackTypeLabel(AttackType.AIRSTRIKE_SOLDIER);
            case AIRSTRIKE_TANK -> attackTypeLabel(AttackType.AIRSTRIKE_TANK);
            case AIRSTRIKE_MONEY -> attackTypeLabel(AttackType.AIRSTRIKE_MONEY);
            case AIRSTRIKE_SHIP -> attackTypeLabel(AttackType.AIRSTRIKE_SHIP);
            case AIRSTRIKE_AIRCRAFT -> attackTypeLabel(AttackType.AIRSTRIKE_AIRCRAFT);
            case NAVAL_SHIP -> attackTypeLabel(AttackType.NAVAL);
            case NAVAL_AIR -> attackTypeLabel(AttackType.NAVAL_AIR);
            case NAVAL_GROUND -> attackTypeLabel(AttackType.NAVAL_GROUND);
            case NAVAL_INFRA -> attackTypeLabel(AttackType.NAVAL_INFRA);
            case PEACE -> attackTypeLabel(AttackType.PEACE);
            case MISSILE_ATTACK -> attackTypeLabel(AttackType.MISSILE);
            case NUKE_ATTACK -> attackTypeLabel(AttackType.NUKE);
            case PROJECTILE_ATTACK -> humanizeTokens(stat.name());
            default -> humanizeTokens(stat.name());
        };
    }

    private static String enumLabel(Enum<?> value) {
        return humanizeTokens(value.name());
    }

    private static String normalizationSuffix(RankingNormalizationMode normalizationMode) {
        if (normalizationMode == RankingNormalizationMode.NONE) {
            return "";
        }
        return " " + humanizeTokens(normalizationMode.name()).toLowerCase(Locale.ROOT);
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Ranking";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String decapitalize(String value) {
        if (value == null || value.isBlank()) {
            return "ranking";
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static String humanizeTokens(String value) {
        if (value == null || value.isBlank()) {
            return "Ranking";
        }

        String normalized = value.replace('.', '_').replace('-', '_').replace(' ', '_');
        String[] parts = normalized.split("_+");
        List<String> words = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            words.add(humanizeToken(part));
        }
        return words.isEmpty() ? value : String.join(" ", words);
    }

    private static String humanizeToken(String token) {
        return switch (token) {
            case "AVG" -> "Average";
            case "PCT" -> "%";
            default -> Character.toUpperCase(token.charAt(0)) + token.substring(1).toLowerCase(Locale.ROOT);
        };
    }
}