package link.locutus.discord.apiv3.csv.file;

public interface ICodedStringMap {
    int insert(String value);
    String get(int index);
    int size();
    default void finishLoad() {};
    int stringLength();
    int countDuplicates();

    default long timeReadAll() {
        long start = System.nanoTime();
        int size = size();
        for (int i = 0; i < size; i++) {
            get(i);
        }
        return System.nanoTime() - start;
    }
}