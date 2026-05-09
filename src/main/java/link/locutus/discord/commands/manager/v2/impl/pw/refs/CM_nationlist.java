package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_nationlist {
        @AutoRegister(clazz=link.locutus.discord.pnw.NationList.class,method="countMembers")
        public static class countMembers extends CommandRef {
            public static final countMembers cmd = new countMembers();

        }
        @AutoRegister(clazz=link.locutus.discord.pnw.NationList.class,method="countNations")
        public static class countNations extends CommandRef {
            public static final countNations cmd = new countNations();
        public countNations filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.pnw.NationList.class,method="getAverage")
        public static class getAverage extends CommandRef {
            public static final getAverage cmd = new getAverage();
        public getAverage attribute(String value) {
            return set("attribute", value);
        }

        public getAverage filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.pnw.NationList.class,method="getAverageMMR")
        public static class getAverageMMR extends CommandRef {
            public static final getAverageMMR cmd = new getAverageMMR();
        public getAverageMMR update(String value) {
            return set("update", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.pnw.NationList.class,method="getAverageMMRUnit")
        public static class getAverageMMRUnit extends CommandRef {
            public static final getAverageMMRUnit cmd = new getAverageMMRUnit();

        }
        @AutoRegister(clazz=link.locutus.discord.pnw.NationList.class,method="getAveragePer")
        public static class getAveragePer extends CommandRef {
            public static final getAveragePer cmd = new getAveragePer();
        public getAveragePer attribute(String value) {
            return set("attribute", value);
        }

        public getAveragePer per(String value) {
            return set("per", value);
        }

        public getAveragePer filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.pnw.NationList.class,method="getNations")
        public static class getNations extends CommandRef {
            public static final getNations cmd = new getNations();
        public getNations filter(String value) {
            return set("filter", value);
        }

        public getNations timestamp(String value) {
            return set("timestamp", value);
        }

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
        @AutoRegister(clazz=link.locutus.discord.pnw.NationList.class,method="getRevenue")
        public static class getRevenue extends CommandRef {
            public static final getRevenue cmd = new getRevenue();

        }
        @AutoRegister(clazz=link.locutus.discord.pnw.NationList.class,method="getRevenueConverted")
        public static class getRevenueConverted extends CommandRef {
            public static final getRevenueConverted cmd = new getRevenueConverted();

        }
        @AutoRegister(clazz=link.locutus.discord.pnw.NationList.class,method="getTotal")
        public static class getTotal extends CommandRef {
            public static final getTotal cmd = new getTotal();
        public getTotal attribute(String value) {
            return set("attribute", value);
        }

        public getTotal filter(String value) {
            return set("filter", value);
        }

        }

}
