package link.locutus.discord.web.jooby.adapter;

import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.CacheType;

import java.lang.reflect.Method;
import java.util.*;

public class TsEndpointGenerator {
    public static String generate(ParametricCallable cmd) {
        Method method = cmd.getMethod();
        ReturnType returnType = method.getAnnotation(ReturnType.class);
        if (returnType == null) throw new IllegalArgumentException("No return type for " + method.getName() + " in " + method.getDeclaringClass().getSimpleName());
        String typeName = returnType.value().getSimpleName();
        String path = cmd.getFullPath("/").replace("api/", "");
        String constName = path.replace("/", "_").toUpperCase();

        Map<String, Object> paramMap = new LinkedHashMap<>();
        for (ParameterData param : cmd.getUserParameters()) {
            paramMap.put(param.getName(), param.toJson());
        }

        List<String> cachePolicy = new ArrayList<>();
        if (returnType.cache() != CacheType.None) {
            cachePolicy.add("type: CacheType." + returnType.cache().name());
            cachePolicy.add("duration: " + returnType.duration());
        }
        String paramsJson = WebUtil.GSON.toJson(paramMap);
        boolean hasArg = !cmd.getUserParameters().isEmpty();
        String argValues, argValuesAllOptional, cacheArg;
        if (hasArg) {
            argValues = "{" + cmd.getUserParameters().stream().map(p -> p.getName() + (p.isOptional() ? "?" : "") + ": string").reduce((a, b) -> a + ", " + b).orElse("") + "}";
            argValuesAllOptional = "{" + cmd.getUserParameters().stream().map(p -> p.getName() + "?: string").reduce((a, b) -> a + ", " + b).orElse("") + "}";
            cacheArg = "combine(" + constName + ".endpoint.cache, args)";
        } else {
            argValues = "Record<string, never>";
            argValuesAllOptional = argValues;
            cacheArg = constName + ".endpoint.cache";
        }

        String tsDef = """
                export const {constName} = {
                    endpoint: new ApiEndpoint<{typeName}>(
                        "{constNameLower}",
                        "{path}",
                        {paramsJson},
                        (data: unknown) => data as {typeName},
                        {cachePolicy}
                    ),
                    useDisplay: (args: {argValues}, render: (data: {typeName}) => React.ReactNode, renderLoading?: () => React.ReactNode, renderError?: (error: string) => React.ReactNode): React.ReactNode => {
                        return useDisplay({constName}.endpoint.name, {cacheArg}, args, render, renderLoading, renderError);
                    },
                    useForm: (
                        default_values?: {argValuesAllOptional},
                        label?: string,
                        message?: ReactNode,
                        handle_response?: (data: {typeName}, setMessage: (message: React.ReactNode) => void,
                        setShowDialog: (showDialog: boolean) => void,
                        setTitle: (title: string) => void) => void,
                        handle_submit?: (args: {argValues}, setMessage: (message: string) => void,
                        setShowDialog: (showDialog: boolean) => void, setTitle: (title: string) => void) => void): React.ReactNode => {
                        return useForm({constName}.endpoint.url, {constName}.endpoint.args, message, default_values, label, handle_response, handle_submit);
                    }
                };"""
                .replace("{path}", path)
                .replace("{constName}", constName)
                .replace("{constNameLower}", constName.toLowerCase())
                .replace("{typeName}", typeName)
                .replace("{paramsJson}", paramsJson)
                .replace("{argValues}", argValues)
                .replace("{cachePolicy}", "{" + StringMan.join(cachePolicy, ", ") + "}")
                .replace("{argValuesAllOptional}", argValuesAllOptional)
                .replace("{cacheArg}", cacheArg)
                ;
        return tsDef;
    }
}
