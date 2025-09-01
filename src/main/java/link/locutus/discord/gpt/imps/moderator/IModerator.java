package link.locutus.discord.gpt.imps.moderator;

import link.locutus.discord.gpt.Chunker;
import link.locutus.discord.gpt.ITokenizer;
import link.locutus.discord.gpt.ModerationResult;

import java.util.List;

public interface IModerator extends ITokenizer {
    List<ModerationResult> moderate(List<String> inputs);

    default List<ModerationResult> moderate(String input) {
        int sizeCap = this.getSizeCap();
        List<String> chunks = Chunker.getChunks(input, sizeCap, this::getSize);
        return moderate(chunks);
    }
}
