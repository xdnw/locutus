package link.locutus.discord.web.jooby.adapter;

import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.command.WebOption;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ReflectionUtil;
import link.locutus.discord.util.scheduler.TriFunction;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.CacheType;
import link.locutus.discord.web.commands.options.WebOptionBindings;
import link.locutus.discord.web.jooby.PageHandler;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;

public class TsEndpointGenerator {
    public static void main(String[] args) {
        try {
            ValueStore<Object> store = PWBindings.createDefaultStore();
            ValidatorStore validators = PWBindings.createDefaultValidators();
            PermissionHandler permisser = PWBindings.createDefaultPermisser();
            PlaceholdersMap placeholders = new PlaceholdersMap(store, validators, permisser).init();
            PageHandler handler = new PageHandler(placeholders);
            writeFiles(handler, null, true, false);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.out.println("Done");
        System.exit(0);
    }

    public static void writeFiles(PageHandler handler, File outputDir, boolean endpoints, boolean commands) throws IOException {
        if (outputDir == null) {
            outputDir = new File("../lc_cmd_react/src/");
        }
        CommandGroup api = (CommandGroup) handler.getCommands().get("api");
        if (endpoints){
            File endpointFile = new File(outputDir, "lib/endpoints.ts");
            String header = """
                    import { ApiEndpoint, CommonEndpoint } from "./BulkQuery";
                    import type * as ApiTypes from "@/lib/apitypes.d.ts";
                    """;
            String constants = generateEndpointConstants(api);
            Files.write(endpointFile.toPath(), (header + constants).getBytes());
        }
        {
            // generateTsPlaceholderBuilder (unused)
        }
        if (commands) {
            CommandManager2 cmdInst = Locutus.cmd().getV2();
            SimpleValueStore<Object> store = new SimpleValueStore<>();
            new WebOptionBindings().register(store);
            Map<String, Object> json = cmdInst.toJson(store, cmdInst.getPermisser());
//            byte[] data = handler.getSerializer().writeValueAsBytes(json);
//            File commandsFile = new File(outputDir, "assets/commands.msgpack");
//            if (!commandsFile.exists()) {
//                commandsFile.getParentFile().mkdirs();
//                commandsFile.createNewFile();
//            }
//            Files.write(commandsFile.toPath(), data);
//            ObjectMapper objectMapper = new ObjectMapper();
//            String jsonString = objectMapper.writeValueAsString(json);
//            File jsonFile = new File(outputDir, "assets/commands.json");
//            if (!jsonFile.exists()) {
//                jsonFile.getParentFile().mkdirs();
//                jsonFile.createNewFile();
//            }
//            Files.write(jsonFile.toPath(), jsonString.getBytes());

            String header = """
                    export const COMMANDS = """;
            File output = new File(outputDir, "lib/commands.ts");

            String jsonStr = WebUtil.GSON.toJson(json);
            Files.write(output.toPath(), (header + jsonStr).getBytes());
        }
    }

    private static String generateEndpointConstants(CommandGroup api) {
        StringBuilder output = new StringBuilder();
        List<String> endpoints = new ArrayList<>();
        for (ParametricCallable cmd : api.getParametricCallables(Predicates.alwaysTrue())) {
            TsEndpoint endpoint = generateTsEndpoint(cmd);
            endpoints.add(endpoint.name);
            output.append(endpoint.declaration).append("\n\n");
        }
        output.append("export const ENDPOINTS = [").append(StringMan.join(endpoints, ", ")).append("];\n");
        return output.toString();
    }

//    public static void saveSchema(Schema schema, boolean pretty, File file) throws IOException {
//        if (file == null) {
//            file = new File("../lc_cmd_react/src/assets/schema.avsc");
//        }
//        if (!file.exists()) {
//            throw new IllegalArgumentException("Output does not exist: " + file.getAbsolutePath());
//        }
//        Files.write(file.toPath(), schema.toString(pretty).getBytes());
//    }
//
//    public static Schema loadSchema(File avscFile) throws IOException {
//        if (avscFile == null) {
//            avscFile = new File("../lc_cmd_react/src/assets/schema.avsc");
//        }
//        if (!avscFile.exists()) {
//            throw new IllegalArgumentException("Output does not exist: " + avscFile.getAbsolutePath());
//        }
//        return new Schema.Parser().parse(new File("../lc_cmd_react/src/assets/schema.avsc"));
//    }
//
//    public static void compileSchema(File avscFile) throws IOException {
//        Schema schema = loadSchema(avscFile);
//        // Create a SpecificCompiler instance
//        SpecificCompiler compiler = new SpecificCompiler(schema);
//
//        // Set the output directory for the generated classes
//        File outputDir = new File("src/main/tmp/");
//        // delete contents of dir before compile
//        Runnable deleteOutput = new Runnable() {
//            @Override
//            public void run() {
//                if (outputDir.exists()) {
//                    File[] files = outputDir.listFiles();
//                    if (files != null) {
//                        for (File f : files) {
//                            f.delete();
//                        }
//                    }
//                } else {
//                    outputDir.mkdirs();
//                }
//            }
//        };
//        deleteOutput.run();
//
//        // Compile the schema to generate the Java classes
//        compiler.compileToDestination(null, outputDir);
//
//        Set<String> permittedPackages = Set.of(
//                "link.locutus.discord.web.commands.binding.value_types",
//                "org.apache.avro"
//        );
//
//        // Iterate files, and if they have the correct package, move them to `/src/main/java`
//        // Packages in sub directories should be permitted e.g. org.apache.avro.reflect
//        // The package name can be found by reading the first few lines of the generated file (until a line with `package ...` is found)
//        // Print any files that are not in the permitted packages and do NOT copy them
//        File moveTo = new File("src/main/java");
//
//        Files.walk(outputDir.toPath())
//        .filter(Files::isRegularFile)
//        .forEach(file -> {
//            try {
//                List<String> lines = Files.readAllLines(file);
//                Optional<String> packageLine = lines.stream()
//                        .filter(line -> line.startsWith("package "))
//                        .findFirst();
//
//                if (packageLine.isPresent()) {
//                    String packageName = packageLine.get().substring(8, packageLine.get().indexOf(';')).trim();
//                    boolean isPermitted = permittedPackages.stream().anyMatch(packageName::startsWith);
//
//                    if (isPermitted) {
//                        Path targetPath = moveTo.toPath().resolve(outputDir.toPath().relativize(file));
//                        System.out.println("Moving file: " + file + " to " + targetPath);
////                        Files.createDirectories(targetPath.getParent());
////                        Files.move(file, targetPath);
//                    } else {
//                        System.out.println("File not in permitted package: " + file + " (" + packageName + ")");
//                        // delete file
////                        Files.delete(file);
//                    }
//                } else {
//                    System.out.println("No package declaration found in file: " + file);
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        });
////        deleteOutput.run();
//    }
//
//
//    public static Schema generateSchema(Collection<Class<?>> classes) {
//        Set<Class<?>> processedClasses = new ObjectLinkedOpenHashSet<>();
//        List<Schema> schemas = new ArrayList<>();
//        for (Class<?> clazz : classes) {
//            if (!processedClasses.contains(clazz)) {
//                processedClasses.add(clazz);
//                schemas.add(ReflectData.get().getSchema(clazz));
//            }
//        }
//        return Schema.createUnion(schemas);
//    }

    private static record TsEndpoint(String name, String declaration) {}

    public static TsEndpoint generateTsEndpoint(ParametricCallable<?> cmd) {
        Method method = cmd.getMethod();
        Command cmdAnn = method.getAnnotation(Command.class);
        ReturnType returnType = method.getAnnotation(ReturnType.class);
        boolean isPost = !cmdAnn.viewable();
        if (returnType == null) throw new IllegalArgumentException("No return type for " + method.getName() + " in " + method.getDeclaringClass().getSimpleName());
        String typeName = adaptType(returnType.value().getSimpleName());
        String path = cmd.getFullPath("/").replace("api/", "");
        String constName = path.replace("/", "_").toUpperCase();

        Map<String, Object> paramMap = new LinkedHashMap<>();
        for (ParameterData param : cmd.getUserParameters()) {
            paramMap.put(param.getName(), param.toJson());
        }

        List<String> cachePolicy = new ArrayList<>();
        Long cacheDuration = returnType != null ? returnType.duration() : 5000;
        CacheType cacheType = returnType != null ? returnType.cache() : CacheType.Memory;
        if (returnType.cache() != CacheType.None) {
            cachePolicy.add("type: '" + returnType.cache().name() + "'");
            cachePolicy.add("duration: " + returnType.duration());
        }
        String paramsJson = WebUtil.GSON.toJson(paramMap);
        boolean hasArg = !cmd.getUserParameters().isEmpty();
        String argValues, argValuesAllOptional, cacheArg;
        if (hasArg) {
            List<String> argTypes = new ArrayList<>();
            List<String> argTypesAllOptional = new ArrayList<>();
            for (ParameterData param : cmd.getUserParameters()) {
                List<String> allowedTypes = new ArrayList<>();
                Type type = param.getType();
                if (type instanceof ParameterizedType pt && pt.getRawType() == List.class && pt.getActualTypeArguments().length == 1 && pt.getActualTypeArguments()[0] == String.class) {
                    allowedTypes.add("string[]");
                }
                allowedTypes.add("string");
                String paramName = param.getName() + "?: " + StringMan.join(allowedTypes, " | ");
                String paramOptional = param.getName() + "?: " + StringMan.join(allowedTypes, " | ");
                if (param.isOptional()) {
                    argTypes.add(paramOptional);
                } else {
                    argTypes.add(paramName);
                }
                argTypesAllOptional.add(paramOptional);
            }
            argValues = "{" + StringMan.join(argTypes, ", ") + "}";
            argValuesAllOptional = "{" + StringMan.join(argTypesAllOptional, ", ") + "}";
            cacheArg = "combine(" + constName + ".endpoint.cache, args)";
        } else {
            argValues = "Record<string, never>";
            argValuesAllOptional = argValues;
            cacheArg = constName + ".endpoint.cache";
        }

        String tsDef = """
                export const {constName}: CommonEndpoint<ApiTypes.{typeName}, {argValues}, {argValuesAllOptional}> = {
                    endpoint: new ApiEndpoint<ApiTypes.{typeName}>(
                        "{constNameLower}",
                        "{path}",
                        {paramsJson},
                        (data: unknown) => data as ApiTypes.{typeName},
                        {cacheDuration},
                        {cacheType},
                        "{typeName}",
                        `{desc}`,
                        {isPost}
                    )
                };"""
                .replace("{path}", path)
                .replace("{constName}", constName)
                .replace("{constNameLower}", constName.toLowerCase())
                .replace("{typeName}", typeName)
                .replace("{paramsJson}", paramsJson)
                .replace("{argValues}", argValues)
                .replace("{cacheDuration}", cacheDuration.toString())
                .replace("{cacheType}", "'" + cacheType.name() + "'")
                .replace("{argValuesAllOptional}", argValuesAllOptional)
                .replace("{cacheArg}", cacheArg)
                .replace("{desc}", cmd.simpleDesc().replace("`", "\\`"))
                .replace("{isPost}", String.valueOf(isPost))
                ;
        return new TsEndpoint(constName, tsDef);
    }

    private static String adaptType(String simpleName) {
        return switch (simpleName) {
            case "Integer", "int", "long", "Long", "Short", "short", "Byte", "byte", "double", "Double", "float", "Float", "Number" -> "number";
            case "Boolean", "boolean" -> "boolean";
            case "String" -> "string";
            default -> simpleName;
        };
    }

    private static boolean isValidFieldType(Class<?> type) {
        return type.isPrimitive() ||
                type.isEnum() ||
                type == String.class ||
                (type.isArray() && isValidFieldType(type.getComponentType())) ||
                Number.class.isAssignableFrom(type) ||
                type == Boolean.class ||
                type == Character.class;
    }

    public static String generateTsPlaceholderBuilder(Placeholders<?, ?> placeholder) throws IOException {
        String boilerPlate = """
                class {className} extends AbstractBuilder {
                    constructor() {
                        super();
                        this.data.type = "{typeName}";
                    }
                
                    {functions}
                }
                """;

        Function<String, String> emptyFunction = (String name) -> """
                {name}(): this {
                    return this.set("{name}", true);
                }
                """.replace("{name}", name);

        // name, string[] required args, string[] optional args
        TriFunction<String, String[], String[], String> withArgs = (String name, String[] required, String[] optional) -> {
            StringBuilder builder = new StringBuilder();
            builder.append(name).append("(args: {");
            for (String arg : required) {
                builder.append(arg).append(": string, ");
            }
            for (String arg : optional) {
                builder.append(arg).append("?: string, ");
            }
            builder.append("}): this {\n");
            builder.append("    return this.set(\"").append(name).append("\", args);\n");
            builder.append("}");
            return builder.toString();
        };

        String typeName = placeholder.getType().getSimpleName();
        String className = typeName + "Builder";

        List<String> functionStrings = new ArrayList<>();
        for (ParametricCallable callable : placeholder.getParametricCallables()) {
            String fieldName = callable.getPrimaryCommandId();
            List<ParameterData> params = callable.getUserParameters();
            String functionStr;
            if (params.isEmpty()) {
                functionStr = emptyFunction.apply(fieldName);
            } else {
                String[] required = params.stream().filter(p -> !p.isOptional()).map(ParameterData::getName).toArray(String[]::new);
                String[] optional = params.stream().filter(ParameterData::isOptional).map(ParameterData::getName).toArray(String[]::new);
                functionStr = withArgs.apply(fieldName, required, optional);
            }
            functionStrings.add(functionStr);
        }

        String functions = StringMan.join(functionStrings, "\n");
        // fix indentation of functions
        functions = functions.replace("\n", "\n    ");

        String classStr = boilerPlate.replace("{className}", className).replace("{typeName}", typeName).replace("{functions}", functions);

        return classStr;
    }

    public static <T> void generatePlaceholderPojos(@Nullable File root, Placeholders<T, ?> placeholders) throws IOException {
        if (root == null) root = new File("src/main/java");

        String fileName = placeholders.getType().getSimpleName() + "_Web";
        File output = new File(root, "link/locutus/discord/web/commands/binding/protos/" + fileName + ".java");

        StringBuilder javaClass = new StringBuilder();
        javaClass.append("package link.locutus.discord.web.commands.binding.protos;\n\n");
        javaClass.append("import org.checkerframework.checker.nullness.qual.Nullable;\n");
        javaClass.append("import java.util.List;\n");
        javaClass.append("import java.util.Map;\n");
        javaClass.append("import java.util.Set;\n");
        javaClass.append("{imports}\n");

        javaClass.append("public class ").append(fileName).append(" {\n");

        Set<Class> enumTypes = new ObjectLinkedOpenHashSet<>();
        for (ParametricCallable callable : placeholders.getParametricCallables()) {
            String fieldName = callable.getPrimaryCommandId();
            Type type = callable.getReturnType();
            if (type instanceof Class clazz) {
                Class<?> wrapper = ReflectionUtil.getWrapperClass(clazz);
                if (wrapper != null) {
                    type = wrapper;
                }
            }

            String typeName = StringMan.classNameToSimple(type.getTypeName());
            if (typeName.contains("<")) {
                List<Class> components = WebOption.getComponentClasses(type);
                for (Class component : components) {
                    if (component.isEnum()) {
                        enumTypes.add(component);
                        continue;
                    }
                    if (isValidFieldType(component)) {
                        continue;
                    }
                    typeName = typeName.replace(component.getSimpleName(), component.getSimpleName() + "_Web");
                }
            } else if (type instanceof Class t && t.isEnum()) {
                enumTypes.add(t);
            } else if (type instanceof Class clazz) {
                if (!isValidFieldType(clazz)) {
                    typeName += "_Web";
                }
            } else {
                Logg.error("INVALID TYPE: " + typeName + " for field " + fieldName);
                continue;
            }

            javaClass.append("    @Nullable public ").append(typeName).append(" ").append(fieldName).append(";\n");
        }
        javaClass.append("}");

        List<String> importsToAdd = new ArrayList<>();
        // replace {imports} with enum imports
        if (!enumTypes.isEmpty()) {
            for (Class enumType : enumTypes) {
                importsToAdd.add("import " + enumType.getName().replace("$", ".") + ";");
            }
        }
        String tag = "{imports}";
        javaClass.replace(javaClass.indexOf(tag), javaClass.indexOf(tag) + tag.length(), StringMan.join(importsToAdd, "\n"));

        Logg.info(javaClass);
        // save to file
        Files.write(output.toPath(), javaClass.toString().getBytes());
    }
}
