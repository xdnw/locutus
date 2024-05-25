package link.locutus.discord.commands.manager.v2.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.util.StringMan;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

public class ParameterData {
    private Type type;
    private Annotation[] annotations;
    private String flag;
    private boolean optional;
    private String[] defaultValue;
    private String name;
    private Parser binding;
    private String desc;
    private int group = -1;

    public JsonElement toJson() {
        JsonObject arg = new JsonObject();
        arg.addProperty("name", getName());
        if (optional) arg.addProperty("optional", true);
        if (isFlag()) arg.addProperty("flag", getFlag());
        if (this.desc != null && !desc.isEmpty()) arg.addProperty("desc", desc);
        if (group != -1) arg.addProperty("group", group);
        arg.addProperty("type", binding.getKey().toSimpleString());
        if (defaultValue != null && defaultValue.length != 0) {
            arg.addProperty("default", getDefaultValueString());
        }
        ArgChoice choiceAnn = getAnnotation(ArgChoice.class);
        if (choiceAnn != null) {
            JsonObject choices = new JsonObject();
            for (String choice : choiceAnn.value()) choices.addProperty(choice, choice);
            arg.add("choices", choices);
        }
        Range range = getAnnotation(Range.class);
        if (range != null) {
            if (range.min() != Double.NEGATIVE_INFINITY)
                arg.addProperty("min", range.min());
            if (range.max() != Double.POSITIVE_INFINITY)
                arg.addProperty("max", range.max());
        }
        Filter filter = getAnnotation(Filter.class);
        if (filter != null) {
            arg.addProperty("filter", filter.value());
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
            Binding keyBinding = key.getBinding();
            if (keyBinding != null && keyBinding.examples().length != 0) {
                if (!isFlag() || isConsumeFlag()) {
                    String example = examplePrefix + StringMan.join(keyBinding.examples(), "`, `" + examplePrefix);
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
