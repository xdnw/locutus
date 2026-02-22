package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.db.INationSnapshot;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;

import java.text.ParseException;
import java.util.List;

public class NationModifier {
    // (Long timestamp, boolean allow_deleted, boolean load_snapshot_vm) {
    public final Long timestamp;
    public final boolean allow_deleted;
    public final boolean load_snapshot_vm;
    // Lazily populated by NationPlaceholders.getSnapshot; avoids re-resolving per leaf callback
    INationSnapshot resolvedSnapshot;

    public NationModifier(Long timestamp, boolean allow_deleted, boolean load_snapshot_vm) {
        this.timestamp = timestamp;
        this.allow_deleted = allow_deleted;
        this.load_snapshot_vm = load_snapshot_vm;
    }

    public static NationModifier parse(String modifier) {
        if (modifier == null || modifier.isEmpty()) return null;
        try {
            List<String> args = StringMan.split(modifier, ",");
            Long timestamp = args.size() > 0 ? PrimitiveBindings.timestamp(args.get(0)) : null;
            Long day = timestamp != null ? TimeUtil.getDay(timestamp) : null;
            boolean includeVm = args.size() > 1 ? PrimitiveBindings.Boolean(args.get(1)) : false;
            return new NationModifier(timestamp, day != null && day > 0, includeVm);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid modifier: " + modifier + ": " + e.getMessage());
        }
    }
}