package link.locutus.discord.util.sheet.templates;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TransferSheet {
    private final Map<NationOrAlliance, Map<ResourceType, Double>> transfers;

    private final SpreadSheet parent;

    public TransferSheet(SpreadSheet other) throws GeneralSecurityException, IOException {
        this.parent = other;
        this.transfers = new LinkedHashMap<>();
    }

    public TransferSheet(String key) throws GeneralSecurityException, IOException {
        this(SpreadSheet.create(key));
    }

    public TransferSheet(GuildDB db) throws GeneralSecurityException, IOException {
        this(SpreadSheet.create(db, SheetKeys.TRANSFER_SHEET));
    }

    public Map<NationOrAlliance, Map<ResourceType, Double>> getTransfers() {
        return transfers;
    }

    public Map<DBNation, Map<ResourceType, Double>> getNationTransfers() {
        Map<DBNation, Map<ResourceType, Double>> result = new LinkedHashMap<>();
        for (Map.Entry<NationOrAlliance, Map<ResourceType, Double>> entry : transfers.entrySet()) {
            if (entry.getKey().isNation()) {
                result.put(entry.getKey().asNation(), entry.getValue());
            }
        }
        return result;
    }

    public Map<DBAlliance, Map<ResourceType, Double>> getAllianceTransfers() {
        Map<DBAlliance, Map<ResourceType, Double>> result = new LinkedHashMap<>();
        for (Map.Entry<NationOrAlliance, Map<ResourceType, Double>> entry : transfers.entrySet()) {
            if (entry.getKey().isAlliance()) {
                result.put(entry.getKey().asAlliance(), entry.getValue());
            }
        }
        return result;
    }

    public TransferSheet write(Map<NationOrAlliance, double[]> transfer) {
        Map<DBNation, Map<ResourceType, Double>> fundsToSendNations = new LinkedHashMap<>();
        Map<DBAlliance, Map<ResourceType, Double>> fundsToSendAAs = new LinkedHashMap<>();

        for (Map.Entry<NationOrAlliance, double[]> entry : transfer.entrySet()) {
            NationOrAlliance key = entry.getKey();
            if (key.isNation()) {
                fundsToSendNations.put(key.asNation(), PnwUtil.resourcesToMap(entry.getValue()));
            } else if (key.isAlliance()) {
                fundsToSendAAs.put(key.asAlliance(), PnwUtil.resourcesToMap(entry.getValue()));
            }
        }
        return write(fundsToSendNations, fundsToSendAAs);
    }

    public TransferSheet write(Map<DBNation, Map<ResourceType, Double>> fundsToSendNations, Map<DBAlliance, Map<ResourceType, Double>> fundsToSendAAs) {
        transfers.putAll(fundsToSendNations);
        transfers.putAll(fundsToSendAAs);
        return write();
    }

    public void clear() {
        transfers.clear();
    }

    /**
     * read the transfers in the sheet
     * @return list of failed transfers
     */
    public Set<String> read() {
        Set<String> invalidNationOrAlliance = new LinkedHashSet<>();
        List<List<Object>> rows = parent.getAll();
        List<Object> header = rows.get(0);

        boolean useLeader = false;
        if (header.size() > 0) {
            Object name = header.get(0);
            useLeader = name != null && name.toString().toLowerCase().contains("leader");
        }

        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.isEmpty() || row.size() < 2) continue;

            Object name = row.get(0);
            if (name == null) continue;
            String nameStr = name + "";
            if (nameStr.isEmpty()) continue;

            Map<ResourceType, Double> transfer = new LinkedHashMap<>();
            for (int j = 1; j < row.size(); j++) {
                Object rssName = header.size() > j ? header.get(j) : null;
                if (rssName == null || rssName.toString().isEmpty()) continue;
                Object amtStr = row.get(j);
                if (amtStr == null || amtStr.toString().isEmpty()) continue;
                try {
                    ResourceType type = ResourceType.parse(rssName.toString());
                    Double amt = MathMan.parseDouble(amtStr.toString());
                    transfer.put(type, transfer.getOrDefault(type, 0d) + amt);
                    continue;
                } catch (IllegalArgumentException ignore) {}
                if (rssName.toString().equalsIgnoreCase("cost_raw") || rssName.toString().equalsIgnoreCase("deposit_raw")) {
                    for (Map.Entry<ResourceType, Double> entry : PnwUtil.parseResources(amtStr.toString()).entrySet()) {
                        transfer.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (transfer.isEmpty()) continue;

            if (nameStr.contains("/alliance/")) {
                Integer allianceId = PnwUtil.parseAllianceId(nameStr);
                if (allianceId == null) invalidNationOrAlliance.add(nameStr);
                else {
                    transfers.put(DBAlliance.getOrCreate(allianceId), transfer);
                }
            } else {
                DBNation nation;
                if (useLeader) {
                    nation = Locutus.imp().getNationDB().getNationByLeader(nameStr);
                } else {
                    nation = Locutus.imp().getNationDB().getNationByName(nameStr);
                }
                if (nation == null) {
                    nation = DiscordUtil.parseNation(nameStr);
                }
                if (nation == null) invalidNationOrAlliance.add(nameStr);
                else {
                    transfers.put(nation, transfer);
                }
            }
        }
        return invalidNationOrAlliance;
    }

    public TransferSheet write() {
        List<String> header = new ArrayList<>(Arrays.asList("nation", "alliance", "cities"));
        for (ResourceType value : ResourceType.values) {
            if (value != ResourceType.CREDITS) header.add(value.name());
        }
        parent.setHeader(header);

        for (Map.Entry<NationOrAlliance, Map<ResourceType, Double>> entry : transfers.entrySet()) {
            ArrayList<Object> row = new ArrayList<>();
            NationOrAlliance nationOrAA = entry.getKey();

            if (nationOrAA.isAlliance()) {
                DBAlliance aa = nationOrAA.asAlliance();
                row.add(aa.getUrl());
                row.add(MarkupUtil.sheetUrl(aa.getName(), aa.getUrl()));
                row.add(-1);
            } else {
                DBNation nation = nationOrAA.asNation();
                row.add(MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));
                row.add(MarkupUtil.sheetUrl(nation.getAllianceName(), nation.getAllianceUrl()));
                row.add(nation.getCities());
            }
            Map<ResourceType, Double> transfer = entry.getValue();
            for (ResourceType type : ResourceType.values) {
                if (type != ResourceType.CREDITS) {
                    double amt = transfer.getOrDefault(type, 0d);
                    row.add(amt);
                }
            }

            parent.addRow(row);
        }
        return this;
    }

    public TransferSheet build() throws IOException {
        parent.clear("A:Z");
        parent.set(0, 0);
        return this;
    }

    public SpreadSheet getSheet() {
        return parent;
    }
}
