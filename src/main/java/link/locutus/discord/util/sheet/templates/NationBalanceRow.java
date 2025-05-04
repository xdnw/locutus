package link.locutus.discord.util.sheet.templates;

import java.util.List;

public record NationBalanceRow(List<Object> row, double[] total, double[] normalized) {};