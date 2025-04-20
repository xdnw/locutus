package link.locutus.discord.apiv3.csv.file;

public interface ICodedStringMap {
    int insert(String value);
    String get(int index);
    int size();
    default void finishLoad() {};
}