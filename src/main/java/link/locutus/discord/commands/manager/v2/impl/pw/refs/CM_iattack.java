package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_iattack {
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getAllianceIdLooted")
        public static class getAllianceIdLooted extends CommandRef {
            public static final getAllianceIdLooted cmd = new getAllianceIdLooted();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getAttConsumptionValue")
        public static class getAttConsumptionValue extends CommandRef {
            public static final getAttConsumptionValue cmd = new getAttConsumptionValue();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getAttLootValue")
        public static class getAttLootValue extends CommandRef {
            public static final getAttLootValue cmd = new getAttLootValue();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getAttLossValue")
        public static class getAttLossValue extends CommandRef {
            public static final getAttLossValue cmd = new getAttLossValue();
        public getAttLossValue war(String value) {
            return set("war", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getAttUnitLossValue")
        public static class getAttUnitLossValue extends CommandRef {
            public static final getAttUnitLossValue cmd = new getAttUnitLossValue();
        public getAttUnitLossValue war(String value) {
            return set("war", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getAttUnitLosses")
        public static class getAttUnitLosses extends CommandRef {
            public static final getAttUnitLosses cmd = new getAttUnitLosses();
        public getAttUnitLosses unit(String value) {
            return set("unit", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getAtt_gas_used")
        public static class getAtt_gas_used extends CommandRef {
            public static final getAtt_gas_used cmd = new getAtt_gas_used();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getAtt_mun_used")
        public static class getAtt_mun_used extends CommandRef {
            public static final getAtt_mun_used cmd = new getAtt_mun_used();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getAttack_type")
        public static class getAttack_type extends CommandRef {
            public static final getAttack_type cmd = new getAttack_type();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getAttacker_id")
        public static class getAttacker_id extends CommandRef {
            public static final getAttacker_id cmd = new getAttacker_id();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getAttcas1")
        public static class getAttcas1 extends CommandRef {
            public static final getAttcas1 cmd = new getAttcas1();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getAttcas2")
        public static class getAttcas2 extends CommandRef {
            public static final getAttcas2 cmd = new getAttcas2();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getCity_id")
        public static class getCity_id extends CommandRef {
            public static final getCity_id cmd = new getCity_id();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getCity_infra_before")
        public static class getCity_infra_before extends CommandRef {
            public static final getCity_infra_before cmd = new getCity_infra_before();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getDate")
        public static class getDate extends CommandRef {
            public static final getDate cmd = new getDate();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getDefConsumptionValue")
        public static class getDefConsumptionValue extends CommandRef {
            public static final getDefConsumptionValue cmd = new getDefConsumptionValue();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getDefLootValue")
        public static class getDefLootValue extends CommandRef {
            public static final getDefLootValue cmd = new getDefLootValue();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getDefLossValue")
        public static class getDefLossValue extends CommandRef {
            public static final getDefLossValue cmd = new getDefLossValue();
        public getDefLossValue war(String value) {
            return set("war", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getDefUnitLossValue")
        public static class getDefUnitLossValue extends CommandRef {
            public static final getDefUnitLossValue cmd = new getDefUnitLossValue();
        public getDefUnitLossValue war(String value) {
            return set("war", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getDefUnitLosses")
        public static class getDefUnitLosses extends CommandRef {
            public static final getDefUnitLosses cmd = new getDefUnitLosses();
        public getDefUnitLosses unit(String value) {
            return set("unit", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getDef_gas_used")
        public static class getDef_gas_used extends CommandRef {
            public static final getDef_gas_used cmd = new getDef_gas_used();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getDef_mun_used")
        public static class getDef_mun_used extends CommandRef {
            public static final getDef_mun_used cmd = new getDef_mun_used();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getDefcas1")
        public static class getDefcas1 extends CommandRef {
            public static final getDefcas1 cmd = new getDefcas1();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getDefcas2")
        public static class getDefcas2 extends CommandRef {
            public static final getDefcas2 cmd = new getDefcas2();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getDefcas3")
        public static class getDefcas3 extends CommandRef {
            public static final getDefcas3 cmd = new getDefcas3();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getDefender_id")
        public static class getDefender_id extends CommandRef {
            public static final getDefender_id cmd = new getDefender_id();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getId")
        public static class getId extends CommandRef {
            public static final getId cmd = new getId();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getImprovements_destroyed")
        public static class getImprovements_destroyed extends CommandRef {
            public static final getImprovements_destroyed cmd = new getImprovements_destroyed();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getInfra_destroyed")
        public static class getInfra_destroyed extends CommandRef {
            public static final getInfra_destroyed cmd = new getInfra_destroyed();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getInfra_destroyed_percent")
        public static class getInfra_destroyed_percent extends CommandRef {
            public static final getInfra_destroyed_percent cmd = new getInfra_destroyed_percent();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getInfra_destroyed_value")
        public static class getInfra_destroyed_value extends CommandRef {
            public static final getInfra_destroyed_value cmd = new getInfra_destroyed_value();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getLootPercent")
        public static class getLootPercent extends CommandRef {
            public static final getLootPercent cmd = new getLootPercent();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getMoney_looted")
        public static class getMoney_looted extends CommandRef {
            public static final getMoney_looted cmd = new getMoney_looted();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getResistance")
        public static class getResistance extends CommandRef {
            public static final getResistance cmd = new getResistance();

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
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getSuccess")
        public static class getSuccess extends CommandRef {
            public static final getSuccess cmd = new getSuccess();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getVictor")
        public static class getVictor extends CommandRef {
            public static final getVictor cmd = new getVictor();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getWar")
        public static class getWar extends CommandRef {
            public static final getWar cmd = new getWar();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getWar_attack_id")
        public static class getWar_attack_id extends CommandRef {
            public static final getWar_attack_id cmd = new getWar_attack_id();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="getWar_id")
        public static class getWar_id extends CommandRef {
            public static final getWar_id cmd = new getWar_id();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="isAttackerIdGreater")
        public static class isAttackerIdGreater extends CommandRef {
            public static final isAttackerIdGreater cmd = new isAttackerIdGreater();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack.class,method="toUrl")
        public static class toUrl extends CommandRef {
            public static final toUrl cmd = new toUrl();

        }

}
