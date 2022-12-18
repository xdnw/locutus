//package link.locutus.discord.apiv3.models;
//
//import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
//import link.locutus.discord.apiv1.enums.AttackType;
//import link.locutus.discord.db.entities.DBWar;
//import link.locutus.discord.util.IOUtil;
//
//import java.io.ByteArrayOutputStream;
//import java.io.DataOutputStream;
//import java.io.IOException;
//import java.nio.ByteBuffer;
//
//public class AttackInfo {
//    private byte[] data;
//    /*
//    4 + 4 + 1 + 8
//    // => 200mb
//
//    byte - index in war
//    byte - turn offset
//    bool - isAttacker
//    byte - attackType / success - pair
//
//    (if it's an attack type that can destroy improvements)
//    byte - improvements
//
//    (if is type that can have loot)
//    char - loot_pct
//        (if ground attack)
//        [byte type, varint amt]
//        (if victory)
//        varint[] amounts
//
//    (if it's an attack that can use munitions)
//    att_munitions varint
//    def_munitions varint
//
//    (if attack type can use gas)
//    att_gas varint
//    def_gas varint
//     */
//    public void loadFrom(DBWar war, DBAttack attack) throws IOException {
//        ByteArrayOutputStream out = new ByteArrayOutputStream();
//        DataOutputStream dos = new DataOutputStream(out);
//        out.reset();
//
////        IOUtil.writeVarInt(out, attack.war_id);
//
//
//        AttackType type = attack.attack_type;
//        if (type.getUnits().length > 0) {
//            len++;
//        }
//        if (type.isVictory()) {
//
//        }
//
////        Map<Integer, AttackInfo[]>
//
//        ByteBuffer buf = ByteBuffer.allocate(len);
//
//
//    }
//
//    public void writeTo(DBWar war, DBAttack attack) {
//
//    }
//
//}
