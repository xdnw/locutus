package link.locutus.discord.commands.manager.v2.command;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.annotation.ArgChoice;
import link.locutus.discord.commands.manager.v2.binding.annotation.Filter;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.mcp.MCPUtil;
import link.locutus.discord.util.StringMan;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class ParameterData {
    private Type type;
    private Annotation[] annotations;
    private String flag;
    private boolean optional;
    private String[] defaultValue;
    private String name;
    private Parser<?> binding;
    private String desc;
    private int group = -1;

    public Map<String, Object> toJson() {
        Map<String, Object> arg = new LinkedHashMap<>();
        arg.put("name", getName());
        if (optional) arg.put("optional", true);
        if (isFlag()) arg.put("flag", getFlag());
        if (this.desc != null && !desc.isEmpty()) arg.put("desc", desc);
        if (group != -1) arg.put("group", group);
        Key<?> webType = binding.getWebTypeOrNull();
        if (webType == null) webType = binding.getKey();
        arg.put("type", webType.toSimpleString());
        if (defaultValue != null && defaultValue.length != 0) {
            arg.put("def", getDefaultValueString());
        }
        ArgChoice choiceAnn = getAnnotation(ArgChoice.class);
        if (choiceAnn != null) {
            arg.put("choices", Arrays.asList(choiceAnn.value()));
        }
        Range range = getAnnotation(Range.class);
        if (range != null) {
            if (range.min() != Double.NEGATIVE_INFINITY)
                arg.put("min", range.min());
            if (range.max() != Double.POSITIVE_INFINITY)
                arg.put("max", range.max());
        }
        Filter filter = getAnnotation(Filter.class);
        if (filter != null) {
            arg.put("filter", filter.value());
        }
        return arg;
    }

    public Map<String, Object> toToolJson(Map<Key<?>, Map<String, Object>> primitiveCache) {
        Map<String, Object> arg = new Object2ObjectLinkedOpenHashMap<>();

        if (defaultValue != null && defaultValue.length != 0) {
            arg.put("default", getDefaultValueString());
        }
        ArgChoice choiceAnn = getAnnotation(ArgChoice.class);
        if (choiceAnn != null) {
            arg.put("enum", Arrays.asList(choiceAnn.value()));
        }

        Range range = getAnnotation(Range.class);
        if (range != null) {
            if (range.min() != Double.NEGATIVE_INFINITY)
                arg.put("minimum", range.min());
            if (range.max() != Double.POSITIVE_INFINITY)
                arg.put("maximum", range.max());
        }
        Filter filter = getAnnotation(Filter.class);
        if (filter != null) {
            arg.put("pattern", filter.value());
        }
        Key<?> key = binding.getKey();
        Key<?> webType = binding.getWebTypeOrNull();
        if (webType != null) key = webType;

        String[] examples = binding.getExamples();
        if (examples != null && examples.length > 0) {
            arg.put("examples", Arrays.asList(examples));
        }
        if (this.desc != null && !desc.isEmpty()) {
            arg.put("description", this.desc);
        }

        Map<String, Object> primitiveType = primitiveCache.get(key);
        if (primitiveType == null && key.getAnnotations().length != 0) {
            primitiveType = MCPUtil.toJsonSchema(type, false);
            primitiveCache.put(key, primitiveType);
        }
        if (primitiveType != null) {
            arg.putAll(primitiveType);
        } else {
            Key<?> bindingWebType = binding.getWebTypeOrNull();
            if (bindingWebType == null) bindingWebType = key;
            String definitionName = MCPUtil.getJsonName(bindingWebType.toSimpleString());
            arg.put("$ref", definitionName);
        }

        return arg;
    }

    public Type getType() {
        return type;
    }

    public ParameterData setType(Type type) {
        this.type = type;
        return this;
    }

    public int getGroup() {
        return group;
    }

    public ParameterData setModifiers(Annotation[] annotations) {
        this.annotations = annotations;
        return this;
    }

    public String[] getDefaultValue() {
        return defaultValue;
    }

    public ParameterData setDefaultValue(String[] value) {
        this.defaultValue = value;
        return this;
    }

    public String getDefaultValueString() {
        return getDefaultValue() == null ? null : StringMan.join(getDefaultValue(), " ");
    }

    public String getName() {
        return name;
    }

    public ParameterData setName(String name) {
        this.name = name;
        return this;
    }

    public Annotation[] getAnnotations() {
        return annotations;
    }

    public boolean isConsumeFlag() {
        return flag != null && type != boolean.class;
    }

    public String getFlag() {
        return flag;
    }

    public boolean isOptional() {
        return optional;
    }

    public ParameterData setOptional(boolean value) {
        this.optional = value;
        return this;
    }

    public Parser<?> getBinding() {
        return binding;
    }

    public ParameterData setBinding(Parser<?> binding) {
        this.binding = binding;
        return this;
    }

    public boolean isFlag() {
        return getFlag() != null;
    }

    public ParameterData setFlag(String value) {
        this.flag = value;
        return this;
    }

    public String getExpandedDescription() {
        return getExpandedDescription(true, true, true);
    }

    public String getSimpleTypeName() {
        String[] split = getType().getTypeName().split("\\.");
        return split[split.length - 1];
    }

    public String getExpandedDescription(boolean includeName, boolean includeExample, boolean includeDesc) {
        String typeName = getType().getTypeName();
        String[] split = typeName.split("\\.");
        typeName = split[split.length - 1];

        StringBuilder expanded = new StringBuilder();

        String examplePrefix = "";
        if (isFlag()) {
            examplePrefix = "-" + getFlag() + " ";
            if (includeName) {
                expanded.append("`-").append(getFlag()).append("`- ").append(getName());
                if (isConsumeFlag()) {
                    expanded.append(" (").append(typeName).append(")");
                }
            }
        } else if (includeName) {
            expanded.append("`").append(getName()).append("` (").append(typeName).append(")");
        }
        if (includeDesc) {
            String paramDesc = getDescription();
            if (paramDesc != null) {
                if (expanded.length() > 0) {
                    expanded.append("- ");
                }
                expanded.append(paramDesc);
            }
        }
        if (getDefaultValue() != null) {
            if (expanded.length() > 0) expanded.append("\n- ");
            expanded.append("default: `").append(StringMan.join(getDefaultValue(), " ")).append("`");
        }
        if (includeExample) {
            Key<?> key = getBinding().getKey();
            String[] examples = getBinding().getExamples();
            if (examples != null && examples.length != 0) {
                if (!isFlag() || isConsumeFlag()) {
                    String example = examplePrefix + StringMan.join(examples, "`, `" + examplePrefix);
                    expanded.append("\n- e.g. `").append(example).append("`");
                }
            }
        }
        return expanded.toString();
    }

    public String getDescription() {
        if (this.desc != null && !desc.isEmpty()) return desc;
        if (binding != null && !binding.getDescription().isEmpty()) {
            return binding.getDescription();
        }
        return null;
    }

    public ParameterData setDescription(String value) {
        this.desc = value;
        return this;
    }

    public <T extends Annotation> T getAnnotation(Class<T> filterClass) {
        for (Annotation annotation : annotations) {
            if (filterClass.isAssignableFrom(annotation.annotationType())) return (T) annotation;
        }
        return null;
    }

    public void setGroup(int group) {
        this.group = group;
    }
}
