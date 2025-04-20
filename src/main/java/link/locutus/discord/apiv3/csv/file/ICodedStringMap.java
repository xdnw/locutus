package link.locutus.discord.apiv3.csv.file;

public interface ICodedStringMap {
    boolean insert(String value);
    String get(int index);
    int size();
    default void finishLoad() {};
}