package com.boydti.discord.commands.manager.v2.command;

import com.boydti.discord.commands.manager.v2.binding.ValueStore;
import com.boydti.discord.commands.manager.v2.perm.PermissionHandler;
import com.boydti.discord.util.StringMan;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

public interface CommandCallable {
    CommandCallable getParent();

    Object call(ArgumentStack stack);

    String help(ValueStore store);

    String desc(ValueStore store);

    String simpleDesc();

    String simpleHelp();

    List<String> aliases();

    default String getPrimaryAlias() {
        if (aliases().isEmpty()) return null;
        return aliases().get(0);
    }

    default void validatePermissions(ValueStore store, PermissionHandler permisser) throws IllegalArgumentException {

    }

    default boolean hasPermission(ValueStore store, PermissionHandler permisser) {
        try {
            validatePermissions(store, permisser);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    default String toHtml(ValueStore store, PermissionHandler permisser) {
        return toHtml(store, permisser, null);
    }

    String toHtml(ValueStore store, PermissionHandler permisser, String endpoint);

    default CommandCallable getCallable(List<String> args) {
        CommandCallable root = this;

        ArrayDeque<String> stack = new ArrayDeque<>(args);
        while (!stack.isEmpty()) {
            String arg = stack.poll();
            if (root instanceof CommandGroup) {
                root = ((CommandGroup) root).get(arg);
            } else {
                throw new IllegalArgumentException("Command: " + root.getPrimaryCommandId() + " of type " + root.getClass().getSimpleName() + " has no subcommand: " + arg);
            }
        }
        return root;
    }

    default String getFullPath() {
        return getFullPath(" ");
    }

    default String getFullPath(String delim) {
        List<String> pathList = new ArrayList<>();
        CommandCallable root = this;
        while (root != null) {
            if (!root.getPrimaryCommandId().isEmpty()) {
                pathList.add(root.getPrimaryCommandId());
            }
            root = root.getParent();
        }
        Collections.reverse(pathList);
        return StringMan.join(pathList, delim);
    }

    default String getPrimaryCommandId() {
        StringBuilder help = new StringBuilder();
        CommandCallable tmp = this;
        while (tmp != null) {
            if (!tmp.aliases().isEmpty() && !tmp.aliases().get(0).isEmpty()) {
                help.insert(0, tmp.aliases().get(0) + " ");
            }
            tmp = tmp.getParent();
        }
        return help.toString().trim();
    }
}
