package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_war {
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getAirControl")
        public static class getAirControl extends CommandRef {
            public static final getAirControl cmd = new getAirControl();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getAttCities")
        public static class getAttCities extends CommandRef {
            public static final getAttCities cmd = new getAttCities();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getAttResearch")
        public static class getAttResearch extends CommandRef {
            public static final getAttResearch cmd = new getAttResearch();
        public getAttResearch research(String value) {
            return set("research", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getAttResearchBits")
        public static class getAttResearchBits extends CommandRef {
            public static final getAttResearchBits cmd = new getAttResearchBits();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getAttacker_aa")
        public static class getAttacker_aa extends CommandRef {
            public static final getAttacker_aa cmd = new getAttacker_aa();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getAttacker_id")
        public static class getAttacker_id extends CommandRef {
            public static final getAttacker_id cmd = new getAttacker_id();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getBlockader")
        public static class getBlockader extends CommandRef {
            public static final getBlockader cmd = new getBlockader();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getCostBits")
        public static class getCostBits extends CommandRef {
            public static final getCostBits cmd = new getCostBits();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getDate")
        public static class getDate extends CommandRef {
            public static final getDate cmd = new getDate();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getDefCities")
        public static class getDefCities extends CommandRef {
            public static final getDefCities cmd = new getDefCities();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getDefResearch")
        public static class getDefResearch extends CommandRef {
            public static final getDefResearch cmd = new getDefResearch();
        public getDefResearch research(String value) {
            return set("research", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getDefResearchBits")
        public static class getDefResearchBits extends CommandRef {
            public static final getDefResearchBits cmd = new getDefResearchBits();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getDefender_aa")
        public static class getDefender_aa extends CommandRef {
            public static final getDefender_aa cmd = new getDefender_aa();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getDefender_id")
        public static class getDefender_id extends CommandRef {
            public static final getDefender_id cmd = new getDefender_id();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getGroundControl")
        public static class getGroundControl extends CommandRef {
            public static final getGroundControl cmd = new getGroundControl();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getNation")
        public static class getNation extends CommandRef {
            public static final getNation cmd = new getNation();
        public getNation attacker(String value) {
            return set("attacker", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getResearchBits")
        public static class getResearchBits extends CommandRef {
            public static final getResearchBits cmd = new getResearchBits();

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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getStatus")
        public static class getStatus extends CommandRef {
            public static final getStatus cmd = new getStatus();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getTurnsLeft")
        public static class getTurnsLeft extends CommandRef {
            public static final getTurnsLeft cmd = new getTurnsLeft();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getWarId")
        public static class getWarId extends CommandRef {
            public static final getWarId cmd = new getWarId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="getWarType")
        public static class getWarType extends CommandRef {
            public static final getWarType cmd = new getWarType();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="isActive")
        public static class isActive extends CommandRef {
            public static final isActive cmd = new isActive();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="possibleEndDate")
        public static class possibleEndDate extends CommandRef {
            public static final possibleEndDate cmd = new possibleEndDate();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="shouldBeExpired")
        public static class shouldBeExpired extends CommandRef {
            public static final shouldBeExpired cmd = new shouldBeExpired();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBWar.class,method="toUrl")
        public static class toUrl extends CommandRef {
            public static final toUrl cmd = new toUrl();

        }

}
