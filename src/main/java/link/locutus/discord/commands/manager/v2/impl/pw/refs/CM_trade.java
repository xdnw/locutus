package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_trade {
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTrade.class,method="getBuyer")
        public static class getBuyer extends CommandRef {
            public static final getBuyer cmd = new getBuyer();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTrade.class,method="getBuyerNation")
        public static class getBuyerNation extends CommandRef {
            public static final getBuyerNation cmd = new getBuyerNation();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTrade.class,method="getDate")
        public static class getDate extends CommandRef {
            public static final getDate cmd = new getDate();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTrade.class,method="getDate_accepted")
        public static class getDate_accepted extends CommandRef {
            public static final getDate_accepted cmd = new getDate_accepted();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTrade.class,method="getParent_id")
        public static class getParent_id extends CommandRef {
            public static final getParent_id cmd = new getParent_id();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTrade.class,method="getPpu")
        public static class getPpu extends CommandRef {
            public static final getPpu cmd = new getPpu();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTrade.class,method="getQuantity")
        public static class getQuantity extends CommandRef {
            public static final getQuantity cmd = new getQuantity();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTrade.class,method="getResource")
        public static class getResource extends CommandRef {
            public static final getResource cmd = new getResource();

        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.binding.DefaultPlaceholders.class,method="getResourceValue")
        public static class getResourceValue extends CommandRef {
            public static final getResourceValue cmd = new getResourceValue();
        public getResourceValue resources(String value) {
            return set("resources", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTrade.class,method="getSeller")
        public static class getSeller extends CommandRef {
            public static final getSeller cmd = new getSeller();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTrade.class,method="getSellerNation")
        public static class getSellerNation extends CommandRef {
            public static final getSellerNation cmd = new getSellerNation();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTrade.class,method="getTradeId")
        public static class getTradeId extends CommandRef {
            public static final getTradeId cmd = new getTradeId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTrade.class,method="getType")
        public static class getType extends CommandRef {
            public static final getType cmd = new getType();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBTrade.class,method="isBuy")
        public static class isBuy extends CommandRef {
            public static final isBuy cmd = new isBuy();

        }

}
