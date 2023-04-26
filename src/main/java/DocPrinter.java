//package link.locutus.discord._test;
//
//import link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands;
//import link.locutus.discord.util.StringMan;
//
//import java.io.FileOutputStream;
//import java.io.PrintStream;
//
//public class DocPrinter {
//    private DocumentationPrinter() {
//    }
//
//    /**
//     * Generates documentation.
//     *
//     * @param args arguments
//     * @throws IOException thrown on I/O error
//     */
//    public static void main(String[] args) throws IOException {
//        writePermissionsWikiTable();
//    }
//
//    private static void writePermissionsWikiTable()
//            throws IOException {
//        try (FileOutputStream fos = new FileOutputStream("wiki_permissions.md")) {
//            PrintStream stream = new PrintStream(fos);
//
//            stream.print("## Overview\n");
//            stream.print("This page is generated from the source. " +
//                    "Click one of the edit buttons below to modify a command class. " +
//                    "You will need to find the parts which correspond to the documentation. " +
//                    "Command documentation will be consistent with what is available ingame");
//            stream.println();
//            stream.println();
//            stream.print("To view this information use `/help [category|command]`\n");
//            stream.print("## Command Syntax     \n");
//            stream.print(" - `<arg>` - A required parameter     \n");
//            stream.print(" - `[arg]` - An optional parameter     \n");
//            stream.print(" - `<arg1|arg2>` - Multiple parameters options     \n");
//            stream.print(" - `<arg=value>` - Default or suggested value     \n");
//            stream.print(" - `-a` - A command flag e.g. `//<command> -a [flag-value]`");
//            stream.println();
//            stream.print("## See also\n");
//            stream.print(" - [Pages](https://github.com/xdnw/locutus/wiki/Pages)\n");
//            stream.print(" - [Arguments](https://github.com/xdnw/locutus/wiki/Arguments)\n");
//            stream.print(" - [Filters](https://github.com/xdnw/locutus/wikiNation-Filters)\n");
//            stream.println();
//            stream.print("## Content");
//            stream.println();
//            stream.print("Click on a category to go to the list of commands, or `More Info` for detailed descriptions ");
//            stream.println();
//            StringBuilder builder = new StringBuilder();
//            writePermissionsWikiTable(stream, builder, "/we ", WorldEditCommands.class);
//            writePermissionsWikiTable(stream, builder, "/", UtilityCommands.class);
//            writePermissionsWikiTable(stream, builder, "/", RegionCommands.class);
//            writePermissionsWikiTable(stream, builder, "/", SelectionCommands.class);
//            writePermissionsWikiTable(stream, builder, "/", HistoryCommands.class);
//            writePermissionsWikiTable(stream, builder, "/schematic ", SchematicCommands.class);
//            writePermissionsWikiTable(stream, builder, "/", ClipboardCommands.class);
//            writePermissionsWikiTable(stream, builder, "/", GenerationCommands.class);
//            writePermissionsWikiTable(stream, builder, "/", BiomeCommands.class);
//            writePermissionsWikiTable(stream, builder, "/anvil ", AnvilCommands.class);
//            writePermissionsWikiTable(stream, builder, "/sp ", SuperPickaxeCommands.class);
//            writePermissionsWikiTable(stream, builder, "/", NavigationCommands.class);
//            writePermissionsWikiTable(stream, builder, "/snapshot", SnapshotCommands.class);
//            writePermissionsWikiTable(stream, builder, "/", SnapshotUtilCommands.class);
//            writePermissionsWikiTable(stream, builder, "/", ScriptingCommands.class);
//            writePermissionsWikiTable(stream, builder, "/", ChunkCommands.class);
//            writePermissionsWikiTable(stream, builder, "/", OptionsCommands.class);
//            writePermissionsWikiTable(stream, builder, "/", BrushOptionsCommands.class);
//            writePermissionsWikiTable(stream, builder, "/tool ", ToolCommands.class);
//            writePermissionsWikiTable(stream, builder, "/brush ", BrushCommands.class);
//            writePermissionsWikiTable(stream, builder, "", MaskCommands.class, "/Masks");
//            writePermissionsWikiTable(stream, builder, "", PatternCommands.class, "/Patterns");
//            writePermissionsWikiTable(stream, builder, "", TransformCommands.class, "/Transforms");
//            writePermissionsWikiTable(stream, builder, "/cfi ", CFICommands.class, "Create From Image");
//
//
//
//            stream.println();
//            stream.print("\n---\n");
//
//            stream.print(builder);
//        }
//    }
//
//    private static void writePermissionsWikiTable(PrintStream stream, StringBuilder content, String prefix, Class<?> cls) {
//        writePermissionsWikiTable(stream, content, prefix, cls, getName(cls));
//    }
//
//    public static String getName(Class cls) {
//        return cls.getSimpleName().replaceAll("(\\p{Ll})(\\p{Lu})", "$1 $2");
//    }
//
//    private static void writePermissionsWikiTable(PrintStream stream, StringBuilder content, String prefix, Class<?> cls, String name) {
//        stream.print(" - [`" + name + "`](#" + name.replaceAll(" ", "-").replaceAll("/", "").toLowerCase() + "-edittop) ");
//        Command cmd = cls.getAnnotation(Command.class);
//        if (cmd != null) {
//            stream.print(" (" + cmd.desc() + ")");
//        }
//        stream.println();
//        writePermissionsWikiTable(content, prefix, cls, name, true);
//    }
//
//    private static void writePermissionsWikiTable(StringBuilder stream, String prefix, Class<?> cls, String name, boolean title) {
//        if (title) {
//            String path = "https://github.com/boy0001/FastAsyncWorldedit/edit/master/core/src/main/java/" + cls.getName().replaceAll("\\.", "/") + ".java";
//            stream.append("### **" + name + "** `[`[`edit`](" + path + ")`|`[`top`](#overview)`]`");
//            stream.append("\n");
//            Command cmd = cls.getAnnotation(Command.class);
//            if (cmd != null) {
//                if (!cmd.desc().isEmpty()) {
//                    stream.append("> (" + (cmd.desc()) + ")    \n");
//                }
//                if (!cmd.help().isEmpty()) {
//                    stream.append("" + (cmd.help()) + "    \n");
//                }
//            }
//            stream.append("\n");
//            stream.append("---");
//            stream.append("\n");
//            stream.append("\n");
//        }
//        for (Method method : cls.getMethods()) {
//            if (!method.isAnnotationPresent(Command.class)) {
//                continue;
//            }
//
//            Command cmd = method.getAnnotation(Command.class);
//            String[] aliases = cmd.aliases();
//            String usage = prefix + aliases[0] + " " + cmd.usage();
//            if (!cmd.flags().isEmpty()) {
//                for (char c : cmd.flags().toCharArray()) {
//                    usage += " [-" + c + "]";
//                }
//            }
////            stream.append("#### [`" + usage + "`](" + "https://github.com/boy0001/FastAsyncWorldedit/wiki/" + aliases[0] + ")\n");
//            stream.append("#### `" + usage + "`\n");
//            if (method.isAnnotationPresent(CommandPermissions.class)) {
//                CommandPermissions perms = method.getAnnotation(CommandPermissions.class);
//                stream.append("**Perm**: `" + StringMan.join(perms.value(), "`, `") + "`    \n");
//            }
//            String help = cmd.help() == null || cmd.help().isEmpty() ? cmd.desc() : cmd.help();
//            stream.append("**Desc**: " + help.trim().replaceAll("\n", "<br />") + "    \n");
//
//            if (method.isAnnotationPresent(NestedCommand.class)) {
//                NestedCommand nested =
//                        method.getAnnotation(NestedCommand.class);
//
//                Class<?>[] nestedClasses = nested.value();
//                for (Class clazz : nestedClasses) {
//                    writePermissionsWikiTable(stream, prefix + cmd.aliases()[0] + " ", clazz, getName(clazz), false);
//                }
//            }
//        }
//        stream.append("\n");
//        if (title) stream.append("---");
//        stream.append("\n");
//        stream.append("\n");
//    }
//}
