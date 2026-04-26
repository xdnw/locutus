package link.locutus.discord.web.commands.binding.value_types;

public record BlitzLegalEdge(
        int declarerNationId,
        int targetNationId,
        boolean legal,
        int[] blockedReasonOrdinals
) {
}
