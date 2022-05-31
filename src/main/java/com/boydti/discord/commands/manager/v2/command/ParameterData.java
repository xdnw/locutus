package com.boydti.discord.commands.manager.v2.command;

import com.boydti.discord.commands.manager.v2.binding.Key;
import com.boydti.discord.commands.manager.v2.binding.Parser;
import com.boydti.discord.commands.manager.v2.binding.ValueStore;
import com.boydti.discord.commands.manager.v2.binding.annotation.Binding;
import com.boydti.discord.commands.manager.v2.binding.annotation.Filter;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.math.ReflectionUtil;
import com.boydti.discord.web.commands.HtmlInput;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ParameterData {
    private Type type;
    private Annotation[] annotations;
    private Character flag;
    private boolean optional;
    private String[] defaultValue;
    private String name;
    private Parser binding;
    private String desc;

    public ParameterData setType(Type type) {
        this.type = type;
        return this;
    }

    public Type getType() {
        return type;
    }

    public ParameterData setModifiers(Annotation[] annotations) {
        this.annotations = annotations;
        return this;
    }

    public ParameterData setFlag(char value) {
        this.flag = value;
        return this;
    }

    public ParameterData setOptional(boolean value) {
        this.optional = value;
        return this;
    }

    public ParameterData setDefaultValue(String[] value) {
        this.defaultValue = value;
        return this;
    }

    public String[] getDefaultValue() {
        return defaultValue;
    }

    public String getDefaultValueString() {
        return getDefaultValue() == null ? null : StringMan.join(getDefaultValue(), " ");
    }

    public String getName() {
        return name;
    }

    public Annotation[] getAnnotations() {
        return annotations;
    }

    public ParameterData setName(String name) {
        this.name = name;
        return this;
    }

    public boolean isConsumeFlag() {
        return flag != null && type != boolean.class;
    }

    public Character getFlag() {
        return flag;
    }

    public boolean isOptional() {
        return optional;
    }

    public ParameterData setBinding(Parser binding) {
        this.binding = binding;
        return this;
    }

    public Parser getBinding() {
        return binding;
    }

    public boolean isFlag() {
        return getFlag() != null;
    }

    public ParameterData setDescription(String value) {
        this.desc = desc;
        return this;
    }

    public String getExpandedDescription() {
        StringBuilder expanded = new StringBuilder();
        String examplePrefix = "";
        if (isFlag()) {
            examplePrefix = "-" + getFlag() + " ";
            expanded.append("`-").append(getFlag()).append("` - " + getName());
            if (isConsumeFlag()) {
                expanded.append(" (" + getType().getTypeName() + ")");
            }
        } else {
            String typeName = getType().getTypeName();
            String[] split = typeName.split("\\.");
            typeName = split[split.length - 1];
            expanded.append("`").append(getName()).append("` (" + typeName + ")");
        }
        String paramDesc = getDescription();
        if (paramDesc != null) {
            expanded.append(" - " + paramDesc);
        }
        if (getDefaultValue() != null) {
            expanded.append("\n - default: `" + StringMan.join(getDefaultValue(), " ") + "`");
        }
        Key key = getBinding().getKey();
        Binding keyBinding = key.getBinding();
        if (keyBinding != null && keyBinding.examples().length != 0) {
            if (!isFlag() || isConsumeFlag()) {
                String example = examplePrefix + StringMan.join(keyBinding.examples(), "`, `" + examplePrefix);
                expanded.append("\n - e.g. `" + example + "`");
            }
        }
        return expanded.toString();
    }

    public String getDescription() {
        if (this.desc != null) return desc;
        if (binding != null && !binding.getDescription().isEmpty()) {
            return binding.getDescription();
        }
        return null;
    }

    public <T extends Annotation> T getAnnotation(Class<T> filterClass) {
        for (Annotation annotation : annotations) {
            if (filterClass.isAssignableFrom(annotation.annotationType())) return (T) annotation;
        }
        return null;
    }
}
