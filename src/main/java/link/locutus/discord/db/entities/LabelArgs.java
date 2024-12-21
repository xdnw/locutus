package link.locutus.discord.db.entities;

import java.util.Map;

public record LabelArgs(String label, Map<String, String> arguments) {
}
