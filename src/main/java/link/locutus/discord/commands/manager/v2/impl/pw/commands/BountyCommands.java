package link.locutus.discord.commands.manager.v2.impl.pw.commands;

public class BountyCommands {
//
//    @Command
//    public String claimBounty(CustomBounty bounty) {
//
//    }
//
//    public String listBounties(@Me DBNation nation, @Me GuildDB db,
//                               boolean inMyScoreRange,
//                               @Switch("l") double scoreLeeway,
//                               @Switch("dnr") boolean ignoreDoNotRaid,
//                               @Switch("v") double minValue,
//                               @Switch("wt") Set<WarType> allowedWarTypes,
//                               @Switch("ws") Set<WarStatus> allowedWarStatus,
//                               @Switch("at") Set<AttackType> allowedAttackTypes,
//                               @Switch("ar") Set<SuccessType> allowedAttackRolls
//
//                               )
//    @Command
//    public void placeBounty(
//            @Me GuildDB db,
//            @Me DBNation me,
//            @Me User author,
//
//            @Arg("Amount to place the bounty for")
//            Map<ResourceType, Double> resources,
//            @Arg("The nations or alliances to bounty")
//            Set<NationOrAlliance> targets,
//            @Arg("The filters to apply to the targets\n" +
//                    "e.g. #color=green,#cities>30,#position>1,#active_m<2880")
//            @Default NationFilter filter,
//    @Arg("Required total damage inflicted")
//    @Switch("d") @Range(min=0) Long totalDamage,
//    @Arg("Required infrastructure damage inflicted")
//    @Switch("i") @Range(min=0) Long infraDamage,
//    @Arg("Required unit damage inflicted (market value)")
//    @Switch("u") @Range(min=0) Long unitDamage,
//
//    @Arg("Include attacks and damage from defensive wars")
//    @Switch("o") Boolean includeDefensives,
//
//    @Arg("Required enemy units killed")
//    @Switch("uk") Map<MilitaryUnit, Long> unitKills,
//
//    @Arg("Required units the bounty hunter has to use in their attacks\n" +
//            "Used to disqualify minimal unit attacks\n" +
//            "Note: Only units allowed by the attack type are checked\n" +
//            "Note: Attacks which match none of the unit types are disqualified" +
//            "i.e. If you include counts for tanks and planes, only plane counts will be checked in airstrikes")
//    @Switch("ua") Map<MilitaryUnit, Long> unitAttacks,
//
//    @Arg("Only allow wars of these types")
//    @Switch("wt") Set<WarType> allowedWarTypes,
//
//    @Arg("Only allow wars with these statuses\n" +
//            "e.g. If you want the war to end by expiry")
//    @Switch("ws") Set<WarStatus> allowedWarStatus,
//    @Switch("at") Set<AttackType> allowedAttackTypes,
//    @Switch("ar") Set<SuccessType> allowedAttackRolls,
//            @Switch("t") boolean payViaTrade,
//            @Switch("f") boolean force
//    ) {
//        if (targets.size() > 500) {
//            throw new IllegalArgumentException("You can only bounty up to 500 nations/alliances at a time");
//        }
//        Set<DBNation> nationsToTarget = new HashSet<>();
//        for (NationOrAlliance target : targets) {
//            if (target.isNation()) {
//                nationsToTarget.add(target.asNation());
//            } else {
//                nationsToTarget.addAll(target.asAlliance().getNations());
//            }
//        }
//        if (nationsToTarget.isEmpty()) {
//            throw new IllegalArgumentException("No nations or alliances specified");
//        }
//        if (filter != null) {
//            Predicate<DBNation> cached = filter.toCached(Long.MAX_VALUE);
//            nationsToTarget.removeIf(f -> !cached.test(f));
//            if (nationsToTarget.isEmpty()) {
//                throw new IllegalArgumentException("No nations or alliances match the filter: `" + filter + "`");
//            }
//            if (filter.getFilter().contains("|")) {
//                throw new IllegalArgumentException("Or filters not currently supported");
//            }
//        }
//        nationsToTarget.removeIf(f -> f.getVm_turns() > 0);
//        if (nationsToTarget.isEmpty()) {
//            throw new IllegalArgumentException("The nations you want to bounty are all in vacation mode. Nations in vacation mode cannot be declared on.");
//        }
//
//        CustomBounty bounty = new CustomBounty();
//        bounty.placedBy = me.getNation_id();
//        bounty.date = System.currentTimeMillis();
//        bounty.resources = PW.resourcesToArray(resources);
//
//        bounty.nations = targets.stream().filter(NationOrAlliance::isNation).map(NationOrAlliance::getId).collect(Collectors.toSet());
//        bounty.alliances = targets.stream().filter(NationOrAlliance::isAlliance).map(NationOrAlliance::getId).collect(Collectors.toSet());
//        bounty.filter = filter.getFilter();
//        bounty.totalDamage = totalDamage;
//        bounty.infraDamage = infraDamage;
//        bounty.unitDamage = unitDamage;
//        bounty.onlyOffensives = includeDefensives != Boolean.TRUE;
//        bounty.setUnitKills(unitKills);
//        bounty.setUnitAttacks(unitAttacks);
//        bounty.allowedWarTypes = allowedWarTypes;
//        bounty.allowedWarStatus = allowedWarStatus;
//        bounty.allowedAttackTypes = allowedAttackTypes;
//        bounty.allowedAttackRolls = allowedAttackRolls;
//
//        bounty.validateBounty();
//
//        Map.Entry<GuildDB, Integer> offshore = db.getOffshoreDB();
//        OffshoreInstance rootOffshore = Locutus.imp().getRootBank();
//
//        String offshoreError = null;
//        boolean sendToOffshore = false;
//
//        if (offshore == null) {
//            offshoreError = "Placing a bounty requires banking to be setup. A server admin can do so via: " + CM.offshore.add.cmd.toSlashMention();
//        } else if (GuildKey.ALLIANCE_ID.getOrNull(db) == null) {
//            offshoreError = "Placing a bounty requires an alliance to be setup. A server admin can do so via: " + GuildKey.ALLIANCE_ID.getCommandMention();
//        } else if (db.isAllianceId(me.getAlliance_id())) {
//            offshoreError = "You are not a member of this alliance (id: " + db.getAllianceIds() + ").";
//        } else if (GuildKey.MEMBER_CAN_WITHDRAW.getOrNull(db, true) != Boolean.TRUE) {
//            offshoreError = "";
//        } else {
//            // check role
//
//            // check deposits
//
//            if (offshore.getKey().getIdLong() != rootOffshore.getGuildDB().getIdLong()) {
//                sendToOffshore = true;
//            }
//        }
//
//        if (offshore == null || offshore) {
//            // send link to locutus server
//
//            // todo setup autorole and auto deposit reset on locutus server
//
//            // if member can withdraw is enabled
//            // if the alliance has the funds
//            // if the member has the funds
//        }
//
//
//
//
//
//    }
}
