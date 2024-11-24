package link.locutus.discord.web.commands.binding.protos;

import org.checkerframework.checker.nullness.qual.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import link.locutus.discord.util.task.ia.IACheckup.AuditType;
import link.locutus.discord.util.task.ia.IACheckup.AuditSeverity;
public class AuditType_Web {
    @Nullable public String getName;
    @Nullable public double getResourceValue;
    @Nullable public String getEmoji;
    @Nullable public AuditType getRequired;
    @Nullable public double getResource;
    @Nullable public AuditSeverity getSeverity;
}