package link.locutus.discord.db.entities.metric;

import link.locutus.discord.db.entities.DBAlliance;

import java.util.List;
import java.util.Map;

public interface IAllianceMetric {
    public Double apply(DBAlliance alliance);
    public void setupReaders(AllianceMetric metric, DataDumpImporter importer);
    public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day);
    public List<AllianceMetricValue> getAllValues();
}
