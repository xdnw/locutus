package link.locutus.discord.gpt;

import link.locutus.discord.gpt.ModerationResult;

import java.util.List;

public interface IModerator {
    List<ModerationResult> moderate(List<String> inputs);
}
