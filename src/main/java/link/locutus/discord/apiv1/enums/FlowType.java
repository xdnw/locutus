package link.locutus.discord.apiv1.enums;

import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.PW;

import java.util.List;
import java.util.Map;

public enum FlowType {
    INTERNAL {
        @Override
        public double[] addTotal(double[] total, int sign, Transaction2 tx, int nationId) {
            if (tx.sender_id == 0 || tx.receiver_id == 0) {
                return ResourceType.add(total, PW.multiply(tx.resources.clone(), sign));
            }
            return total;
        }
    },
    WITHDRAWAL {
        @Override
        public double[] addTotal(double[] total, int sign, Transaction2 tx, int nationId) {
            if (tx.receiver_id == nationId && tx.isReceiverNation() && tx.sender_id != 0) {
                return ResourceType.add(total, PW.multiply(tx.resources.clone(), -sign));
            }
            return total;
        }
    },
    DEPOSIT {
        @Override
        public double[] addTotal(double[] total, int sign, Transaction2 tx, int nationId) {
            if (tx.sender_id == nationId && tx.isSenderNation() && tx.receiver_id != 0) {
                return ResourceType.add(total, PW.multiply(tx.resources.clone(), sign));
            }
            return total;
        }
    };

    public static final FlowType[] VALUES = values();

    public abstract double[] addTotal(double[] total, int sign, Transaction2 transfer, int nationId);

    public double[] getTotal(List<Map.Entry<Integer, Transaction2>> transfers, int nationId) {
        double[] total = ResourceType.getBuffer();
        for (Map.Entry<Integer, Transaction2> entry : transfers) {
            total = addTotal(total, entry.getKey(), entry.getValue(), nationId);
        }
        return total;
    }
}