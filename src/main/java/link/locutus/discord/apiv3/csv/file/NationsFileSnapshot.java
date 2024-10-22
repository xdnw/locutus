package link.locutus.discord.apiv3.csv.file;

import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.db.INationSnapshot;
import link.locutus.discord.db.entities.DBNation;

import java.io.IOException;
import java.text.ParseException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class NationsFileSnapshot implements INationSnapshot {
    private final Map<Integer, DBNation> nations;

    public NationsFileSnapshot(DataDumpParser dumper, long day, boolean loadCities) throws IOException, ParseException {
        this.nations = dumper.getNations(day, loadCities, true, f -> true, f -> true, f -> true);
    }

    @Override
    public DBNation getNationById(int id) {
        return nations.get(id);
    }

    @Override
    public Set<DBNation> getNationsByAlliance(Set<Integer> alliances) {
        return nations.values().stream().filter(n -> alliances.contains(n.getAlliance_id())).collect(Collectors.toSet());
    }

    @Override
    public DBNation getNationByLeader(String input) {
        return nations.values().stream().filter(n -> n.getLeader().equalsIgnoreCase(input)).findFirst().orElse(null);
    }

    public DBNation getNationByName(String input) {
        return nations.values().stream().filter(n -> n.getName().equalsIgnoreCase(input)).findFirst().orElse(null);
    }

    @Override
    public Set<DBNation> getAllNations() {
        return Set.copyOf(nations.values());
    }

    @Override
    public Set<DBNation> getNationsByBracket(int taxId) {
        return nations.values().stream().filter(n -> n.getTax_id() == taxId).collect(Collectors.toSet());
    }

    @Override
    public Set<DBNation> getNationsByAlliance(int id) {
        return nations.values().stream().filter(n -> n.getAlliance_id() == id).collect(Collectors.toSet());
    }
}
