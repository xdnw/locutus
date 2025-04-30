package link.locutus.discord.util.sheet.templates;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TransferSheet {
    private final Map<NationOrAlliance, Map<ResourceType, Double>> transfers;
    private final Map<NationOrAlliance, String> notes = new LinkedHashMap<>();

    private final SpreadSheet parent;

    public TransferSheet(SpreadSheet other) throws GeneralSecurityException, IOException {
        this.parent = other;
        this.transfers = new LinkedHashMap<>();
    }

    public TransferSheet(String key) throws GeneralSecurityException, IOException {
        this(SpreadSheet.create(key));
    }

    public TransferSheet(GuildDB db) throws GeneralSecurityException, IOException {
        this(SpreadSheet.create(db, SheetKey.TRANSFER_SHEET));
    }

    public Map<NationOrAlliance, Map<ResourceType, Double>> getTransfers() {
        return transfers;
    }

    public Map<NationOrAlliance, String> getNotes() {
        return notes;
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
                fundsToSendNations.put(key.asNation(), ResourceType.resourcesToMap(entry.getValue()));
            } else if (key.isAlliance()) {
                fundsToSendAAs.put(key.asAlliance(), ResourceType.resourcesToMap(entry.getValue()));
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
        Set<String> invalidNationOrAlliance = new ObjectLinkedOpenHashSet<>();
        List<List<Object>> rows = parent.loadValuesCurrentTab(true);
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

            String note = null;
            Map<ResourceType, Double> transfer = new LinkedHashMap<>();
            for (int j = 1; j < row.size(); j++) {
                Object rssName = header.size() > j ? header.get(j) : null;
                if (rssName == null || rssName.toString().isEmpty()) continue;
                Object amtStr = row.get(j);
                if (amtStr == null || amtStr.toString().isEmpty()) continue;
                try {
                    ResourceType type = ResourceType.parse(rssName.toString());
                    if (type == null) {
                        throw new IllegalArgumentException("Invalid resource type: `" + rssName + "`");
                    }
                    Double amt = MathMan.parseDouble(amtStr.toString());
                    if (amt == null) {
                        throw new IllegalArgumentException("Invalid amount: `" + amtStr + "` for " + type);
                    }
                    transfer.put(type, transfer.getOrDefault(type, 0d) + amt);
                    continue;
                } catch (IllegalArgumentException ignore) {}
                if (rssName.toString().equalsIgnoreCase("cost_raw") || rssName.toString().equalsIgnoreCase("deposit_raw") || rssName.toString().equalsIgnoreCase("resources")) {
                    for (Map.Entry<ResourceType, Double> entry : ResourceType.parseResources(amtStr.toString()).entrySet()) {
                        transfer.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                } else if (rssName.toString().equalsIgnoreCase("note")) {
                    note = amtStr.toString();
                }
            }
            if (transfer.isEmpty()) continue;

            if (nameStr.contains("/alliance/")) {
                Integer allianceId = PW.parseAllianceId(nameStr);
                if (allianceId == null) invalidNationOrAlliance.add(nameStr);
                else {
                    DBAlliance entity = DBAlliance.getOrCreate(allianceId);
                    transfers.put(entity, transfer);
                    if (note != null) notes.put(entity, note);
                }
            } else {
                DBNation nation;
                if (useLeader) {
                    nation = Locutus.imp().getNationDB().getNationByLeader(nameStr);
                } else {
                    nation = Locutus.imp().getNationDB().getNationByName(nameStr);
                }
                if (nation == null) {
                    nation = DiscordUtil.parseNation(nameStr, false);
                }
                if (nation == null) invalidNationOrAlliance.add(nameStr);
                else {
                    transfers.put(nation, transfer);
                    if (note != null) notes.put(nation, note);
                }
            }
        }
        return invalidNationOrAlliance;
    }

    public TransferSheet write() {
        List<String> header = new ArrayList<>(Arrays.asList("nation", "alliance", "cities", "worth"));
        if (!notes.isEmpty()) {
            header.add("note");
        }
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
                row.add(MarkupUtil.sheetUrl(nation.getNation(), nation.getUrl()));
                row.add(MarkupUtil.sheetUrl(nation.getAllianceName(), nation.getAllianceUrl()));
                row.add(nation.getCities());
            }
            String note = notes.get(nationOrAA);
            if (note != null) {
                row.add(note);
            }
            Map<ResourceType, Double> transfer = entry.getValue();
            row.add(ResourceType.convertedTotal(transfer));
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
        parent.updateClearCurrentTab();
        parent.updateWrite();
        return this;
    }

    public SpreadSheet getSheet() {
        return parent;
    }
}
