package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_treasure {
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTreasure.class,method="getBonus")
        public static class getBonus extends CommandRef {
            public static final getBonus cmd = new getBonus();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTreasure.class,method="getColor")
        public static class getColor extends CommandRef {
            public static final getColor cmd = new getColor();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTreasure.class,method="getContinent")
        public static class getContinent extends CommandRef {
            public static final getContinent cmd = new getContinent();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTreasure.class,method="getDaysRemaining")
        public static class getDaysRemaining extends CommandRef {
            public static final getDaysRemaining cmd = new getDaysRemaining();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTreasure.class,method="getId")
        public static class getId extends CommandRef {
            public static final getId cmd = new getId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTreasure.class,method="getName")
        public static class getName extends CommandRef {
            public static final getName cmd = new getName();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTreasure.class,method="getNation")
        public static class getNation extends CommandRef {
            public static final getNation cmd = new getNation();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTreasure.class,method="getNation_id")
        public static class getNation_id extends CommandRef {
            public static final getNation_id cmd = new getNation_id();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTreasure.class,method="getNationsInRange")
        public static class getNationsInRange extends CommandRef {
            public static final getNationsInRange cmd = new getNationsInRange();
        public getNationsInRange maxNationScore(String value) {
            return set("maxNationScore", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTreasure.class,method="getNumNationsInRange")
        public static class getNumNationsInRange extends CommandRef {
            public static final getNumNationsInRange cmd = new getNumNationsInRange();

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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTreasure.class,method="getSpawnDate")
        public static class getSpawnDate extends CommandRef {
            public static final getSpawnDate cmd = new getSpawnDate();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTreasure.class,method="getTimeUntilNextSpawn")
        public static class getTimeUntilNextSpawn extends CommandRef {
            public static final getTimeUntilNextSpawn cmd = new getTimeUntilNextSpawn();

        }

}
