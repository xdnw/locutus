package link.locutus.discord.util.task.multi;

public class NetworkRow {
    public int id;
    public long lastAccessFromSharedIP;
    public int numberOfSharedIPs;
    public long lastActiveMs;
    public int allianceId;
    public long dateCreated;

    public NetworkRow(int id, long lastAccessFromSharedIP, int numberOfSharedIPs, long lastActiveMs, int allianceId, long dateCreated) {
        this.id = id;
        this.lastAccessFromSharedIP = lastAccessFromSharedIP;
        this.numberOfSharedIPs = numberOfSharedIPs;
        this.lastActiveMs = lastActiveMs;
        this.allianceId = allianceId;
        this.dateCreated = dateCreated;
    }
}
