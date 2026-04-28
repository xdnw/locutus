package link.locutus.discord.web.commands.binding.value_types;

public record BlitzPairLockout(
        int declarerNationId,
        int targetNationId,
        int warId,
        boolean active
) {
}