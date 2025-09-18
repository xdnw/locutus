package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_attacktype {
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.AttackType.class,method="canDamage")
        public static class canDamage extends CommandRef {
            public static final canDamage cmd = new canDamage();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.AttackType.class,method="getAttackerAvgCasualties")
        public static class getAttackerAvgCasualties extends CommandRef {
            public static final getAttackerAvgCasualties cmd = new getAttackerAvgCasualties();
        public getAttackerAvgCasualties unit(String value) {
            return set("unit", value);
        }

        public getAttackerAvgCasualties attacker(String value) {
            return set("attacker", value);
        }

        public getAttackerAvgCasualties defender(String value) {
            return set("defender", value);
        }

        public getAttackerAvgCasualties victory(String value) {
            return set("victory", value);
        }

        public getAttackerAvgCasualties warType(String value) {
            return set("warType", value);
        }

        public getAttackerAvgCasualties defAirControl(String value) {
            return set("defAirControl", value);
        }

        public getAttackerAvgCasualties attAirControl(String value) {
            return set("attAirControl", value);
        }

        public getAttackerAvgCasualties defFortified(String value) {
            return set("defFortified", value);
        }

        public getAttackerAvgCasualties equipAttackerSoldiers(String value) {
            return set("equipAttackerSoldiers", value);
        }

        public getAttackerAvgCasualties equipDefenderSoldiers(String value) {
            return set("equipDefenderSoldiers", value);
        }

        public getAttackerAvgCasualties attGroundControl(String value) {
            return set("attGroundControl", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.AttackType.class,method="getAttackerMaxCasualties")
        public static class getAttackerMaxCasualties extends CommandRef {
            public static final getAttackerMaxCasualties cmd = new getAttackerMaxCasualties();
        public getAttackerMaxCasualties unit(String value) {
            return set("unit", value);
        }

        public getAttackerMaxCasualties attacker(String value) {
            return set("attacker", value);
        }

        public getAttackerMaxCasualties defender(String value) {
            return set("defender", value);
        }

        public getAttackerMaxCasualties victory(String value) {
            return set("victory", value);
        }

        public getAttackerMaxCasualties warType(String value) {
            return set("warType", value);
        }

        public getAttackerMaxCasualties defAirControl(String value) {
            return set("defAirControl", value);
        }

        public getAttackerMaxCasualties attAirControl(String value) {
            return set("attAirControl", value);
        }

        public getAttackerMaxCasualties defFortified(String value) {
            return set("defFortified", value);
        }

        public getAttackerMaxCasualties equipAttackerSoldiers(String value) {
            return set("equipAttackerSoldiers", value);
        }

        public getAttackerMaxCasualties equipDefenderSoldiers(String value) {
            return set("equipDefenderSoldiers", value);
        }

        public getAttackerMaxCasualties attGroundControl(String value) {
            return set("attGroundControl", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.AttackType.class,method="getAttackerMinCasualties")
        public static class getAttackerMinCasualties extends CommandRef {
            public static final getAttackerMinCasualties cmd = new getAttackerMinCasualties();
        public getAttackerMinCasualties unit(String value) {
            return set("unit", value);
        }

        public getAttackerMinCasualties attacker(String value) {
            return set("attacker", value);
        }

        public getAttackerMinCasualties defender(String value) {
            return set("defender", value);
        }

        public getAttackerMinCasualties victory(String value) {
            return set("victory", value);
        }

        public getAttackerMinCasualties warType(String value) {
            return set("warType", value);
        }

        public getAttackerMinCasualties defAirControl(String value) {
            return set("defAirControl", value);
        }

        public getAttackerMinCasualties attAirControl(String value) {
            return set("attAirControl", value);
        }

        public getAttackerMinCasualties defFortified(String value) {
            return set("defFortified", value);
        }

        public getAttackerMinCasualties equipAttackerSoldiers(String value) {
            return set("equipAttackerSoldiers", value);
        }

        public getAttackerMinCasualties equipDefenderSoldiers(String value) {
            return set("equipDefenderSoldiers", value);
        }

        public getAttackerMinCasualties attGroundControl(String value) {
            return set("attGroundControl", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.AttackType.class,method="getDefenderAvgCasualties")
        public static class getDefenderAvgCasualties extends CommandRef {
            public static final getDefenderAvgCasualties cmd = new getDefenderAvgCasualties();
        public getDefenderAvgCasualties unit(String value) {
            return set("unit", value);
        }

        public getDefenderAvgCasualties attacker(String value) {
            return set("attacker", value);
        }

        public getDefenderAvgCasualties defender(String value) {
            return set("defender", value);
        }

        public getDefenderAvgCasualties victory(String value) {
            return set("victory", value);
        }

        public getDefenderAvgCasualties warType(String value) {
            return set("warType", value);
        }

        public getDefenderAvgCasualties defAirControl(String value) {
            return set("defAirControl", value);
        }

        public getDefenderAvgCasualties attAirControl(String value) {
            return set("attAirControl", value);
        }

        public getDefenderAvgCasualties defFortified(String value) {
            return set("defFortified", value);
        }

        public getDefenderAvgCasualties equipAttackerSoldiers(String value) {
            return set("equipAttackerSoldiers", value);
        }

        public getDefenderAvgCasualties equipDefenderSoldiers(String value) {
            return set("equipDefenderSoldiers", value);
        }

        public getDefenderAvgCasualties attGroundControl(String value) {
            return set("attGroundControl", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.AttackType.class,method="getDefenderMaxCasualties")
        public static class getDefenderMaxCasualties extends CommandRef {
            public static final getDefenderMaxCasualties cmd = new getDefenderMaxCasualties();
        public getDefenderMaxCasualties unit(String value) {
            return set("unit", value);
        }

        public getDefenderMaxCasualties attacker(String value) {
            return set("attacker", value);
        }

        public getDefenderMaxCasualties defender(String value) {
            return set("defender", value);
        }

        public getDefenderMaxCasualties victory(String value) {
            return set("victory", value);
        }

        public getDefenderMaxCasualties warType(String value) {
            return set("warType", value);
        }

        public getDefenderMaxCasualties defAirControl(String value) {
            return set("defAirControl", value);
        }

        public getDefenderMaxCasualties attAirControl(String value) {
            return set("attAirControl", value);
        }

        public getDefenderMaxCasualties defFortified(String value) {
            return set("defFortified", value);
        }

        public getDefenderMaxCasualties equipAttackerSoldiers(String value) {
            return set("equipAttackerSoldiers", value);
        }

        public getDefenderMaxCasualties equipDefenderSoldiers(String value) {
            return set("equipDefenderSoldiers", value);
        }

        public getDefenderMaxCasualties attGroundControl(String value) {
            return set("attGroundControl", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.AttackType.class,method="getDefenderMinCasualties")
        public static class getDefenderMinCasualties extends CommandRef {
            public static final getDefenderMinCasualties cmd = new getDefenderMinCasualties();
        public getDefenderMinCasualties unit(String value) {
            return set("unit", value);
        }

        public getDefenderMinCasualties attacker(String value) {
            return set("attacker", value);
        }

        public getDefenderMinCasualties defender(String value) {
            return set("defender", value);
        }

        public getDefenderMinCasualties victory(String value) {
            return set("victory", value);
        }

        public getDefenderMinCasualties warType(String value) {
            return set("warType", value);
        }

        public getDefenderMinCasualties defAirControl(String value) {
            return set("defAirControl", value);
        }

        public getDefenderMinCasualties attAirControl(String value) {
            return set("attAirControl", value);
        }

        public getDefenderMinCasualties defFortified(String value) {
            return set("defFortified", value);
        }

        public getDefenderMinCasualties equipAttackerSoldiers(String value) {
            return set("equipAttackerSoldiers", value);
        }

        public getDefenderMinCasualties equipDefenderSoldiers(String value) {
            return set("equipDefenderSoldiers", value);
        }

        public getDefenderMinCasualties attGroundControl(String value) {
            return set("attGroundControl", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.AttackType.class,method="getMapUsed")
        public static class getMapUsed extends CommandRef {
            public static final getMapUsed cmd = new getMapUsed();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.AttackType.class,method="getName")
        public static class getName extends CommandRef {
            public static final getName cmd = new getName();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.AttackType.class,method="getResistance")
        public static class getResistance extends CommandRef {
            public static final getResistance cmd = new getResistance();
        public getResistance success(String value) {
            return set("success", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.AttackType.class,method="getResistanceIT")
        public static class getResistanceIT extends CommandRef {
            public static final getResistanceIT cmd = new getResistanceIT();

        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.binding.DefaultPlaceholders.class,method="getResource")
        public static class getResource extends CommandRef {
            public static final getResource cmd = new getResource();
        public getResource resources(String value) {
            return set("resources", value);
        }

        public getResource resource(String value) {
            return set("resource", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.binding.DefaultPlaceholders.class,method="getResourceValue")
        public static class getResourceValue extends CommandRef {
            public static final getResourceValue cmd = new getResourceValue();
        public getResourceValue resources(String value) {
            return set("resources", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.AttackType.class,method="getUnitType")
        public static class getUnitType extends CommandRef {
            public static final getUnitType cmd = new getUnitType();
        public getUnitType index(String value) {
            return set("index", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.AttackType.class,method="isVictory")
        public static class isVictory extends CommandRef {
            public static final isVictory cmd = new isVictory();

        }

}
