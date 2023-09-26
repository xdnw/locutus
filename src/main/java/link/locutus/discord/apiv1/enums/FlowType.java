package link.locutus.discord.apiv1.enums;

import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.PnwUtil;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public enum FlowType {
    INTERNAL {
        @Override
        public double[] getTotal(List<Map.Entry<Integer, Transaction2>> transfers, int nationId) {
            return ResourceType.builder().forEach(transfers, new BiConsumer<ResourceType.ResourcesBuilder, Map.Entry<Integer, Transaction2>>() {
                @Override
                public void accept(ResourceType.ResourcesBuilder r, Map.Entry<Integer, Transaction2> signTx) {
                    Transaction2 tx = signTx.getValue();
                    if (tx.sender_id == 0 || tx.receiver_id == 0) {
                        r.add(PnwUtil.multiply(tx.resources, signTx.getKey()));
                    }
                }
            }).build();
        }
    },
    WITHDRAWAL {
        @Override
        public double[] getTotal(List<Map.Entry<Integer, Transaction2>> transfers, int nationId) {
            return ResourceType.builder().forEach(transfers, new BiConsumer<ResourceType.ResourcesBuilder, Map.Entry<Integer, Transaction2>>() {
                @Override
                public void accept(ResourceType.ResourcesBuilder r, Map.Entry<Integer, Transaction2> signTx) {
                    Transaction2 tx = signTx.getValue();
                    if (tx.receiver_id == nationId && tx.isReceiverNation() && tx.sender_id != 0) {
                        r.add(PnwUtil.multiply(tx.resources, -signTx.getKey()));
                    }
                }
            }).build();
        }
    },
    DEPOSIT {
        @Override
        public double[] getTotal(List<Map.Entry<Integer, Transaction2>> transfers, int nationId) {
            return ResourceType.builder().forEach(transfers, new BiConsumer<ResourceType.ResourcesBuilder, Map.Entry<Integer, Transaction2>>() {
                @Override
                public void accept(ResourceType.ResourcesBuilder r, Map.Entry<Integer, Transaction2> signTx) {
                    Transaction2 tx = signTx.getValue();
                    if (tx.sender_id == nationId && tx.isSenderNation() && tx.receiver_id != 0) {
                        r.add(PnwUtil.multiply(tx.resources, signTx.getKey()));
                    }
                }
            }).build();
        }
    },

    ;

    public abstract double[] getTotal(List<Map.Entry<Integer, Transaction2>> transfers, int nationId);

}
