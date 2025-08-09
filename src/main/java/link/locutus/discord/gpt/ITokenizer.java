package link.locutus.discord.gpt;

public interface ITokenizer {
    int getSize(String text);
    int getSizeCap();
}
