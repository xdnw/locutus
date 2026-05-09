package link.locutus.discord.apiv3.csv.header;

import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.db.entities.DataDumpDBAlliance;
import link.locutus.discord.db.entities.DBAlliance;

public class AllianceHeaderReader extends DataReader<AllianceHeader> {
    private DBAlliance cached;

    public AllianceHeaderReader(AllianceHeader header, long date) {
        super(header, date);
    }

    @Override
    public void clear() {
        cached = null;
    }

    public DBAlliance getAlliance() {
        int allianceId = header.alliance_id.get();
        if (cached != null && cached.getAlliance_id() == allianceId) {
            return cached;
        }

        NationColor color = header.color.get();
        if (color == null) {
            color = NationColor.GRAY;
        }
        cached = new DataDumpDBAlliance(
                allianceId,
                header.name.get(),
                header.acronym.get(),
                header.flag_url.get(),
                header.date_created.get(),
                color,
                null,
                header.score.get());
        return cached;
    }
}