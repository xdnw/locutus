package link.locutus.discord.commands.manager.v2.impl.discord;

import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.IPermissionContainer;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.managers.channel.ChannelManager;
import net.dv8tion.jda.api.managers.channel.attribute.ICategorizableChannelManager;
import net.dv8tion.jda.api.managers.channel.attribute.IPermissionContainerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CategorizableChannelManagerDelegate extends ChannelManagerDelegate implements ICategorizableChannelManager {
    public CategorizableChannelManagerDelegate(ChannelManager manager) {
        super(manager);
    }

    @NotNull
    @Override
    public ICategorizableChannelManager setParent(@Nullable Category category) {
        if (parent instanceof ICategorizableChannelManager) {
            ((ICategorizableChannelManager<?, ?>) parent).setParent(category);
        }
        return this;
    }

    @NotNull
    @Override
    public ICategorizableChannelManager sync(@NotNull IPermissionContainer syncSource) {
        if (parent instanceof ICategorizableChannelManager) {
            ((ICategorizableChannelManager<?, ?>) parent).sync(syncSource);
        }
        return this;
    }

    @NotNull
    @Override
    public IPermissionContainerManager clearOverridesAdded() {
        if (parent instanceof ICategorizableChannelManager) {
            ((ICategorizableChannelManager<?, ?>) parent).clearOverridesAdded();
        }
        return this;
    }

    @NotNull
    @Override
    public IPermissionContainerManager clearOverridesRemoved() {
        if (parent instanceof ICategorizableChannelManager) {
            ((ICategorizableChannelManager<?, ?>) parent).clearOverridesRemoved();
        }
        return this;
    }

    @NotNull
    @Override
    public IPermissionContainerManager putPermissionOverride(@NotNull IPermissionHolder permHolder, long allow, long deny) {
        if (parent instanceof ICategorizableChannelManager) {
            ((ICategorizableChannelManager<?, ?>) parent).putPermissionOverride(permHolder, allow, deny);
        }
        return this;
    }

    @NotNull
    @Override
    public IPermissionContainerManager putRolePermissionOverride(long roleId, long allow, long deny) {
        if (parent instanceof ICategorizableChannelManager) {
            ((ICategorizableChannelManager<?, ?>) parent).putRolePermissionOverride(roleId, allow, deny);
        }
        return this;
    }

    @NotNull
    @Override
    public IPermissionContainerManager putMemberPermissionOverride(long memberId, long allow, long deny) {
        if (parent instanceof ICategorizableChannelManager) {
            ((ICategorizableChannelManager<?, ?>) parent).putMemberPermissionOverride(memberId, allow, deny);
        }
        return this;
    }

    @NotNull
    @Override
    public IPermissionContainerManager removePermissionOverride(@NotNull IPermissionHolder permHolder) {
        if (parent instanceof ICategorizableChannelManager) {
            ((ICategorizableChannelManager<?, ?>) parent).removePermissionOverride(permHolder);
        }
        return this;
    }

    @NotNull
    @Override
    public IPermissionContainerManager removePermissionOverride(long id) {
        if (parent instanceof ICategorizableChannelManager) {
            ((ICategorizableChannelManager<?, ?>) parent).removePermissionOverride(id);
        }
        return this;
    }
}
