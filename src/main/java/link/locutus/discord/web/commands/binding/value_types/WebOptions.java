package link.locutus.discord.web.commands.binding.value_types;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WebOptions {
    public @Nullable List<Integer> key_numeric;
    public @Nullable List<String> key_string;
    public @Nullable List<String> icon;
    public @Nullable List<String> text;
    public @Nullable List<String> subtext;
    public @Nullable List<String> color;

    public WebOptions(boolean numeric) {
        if (numeric) {
            this.key_numeric = new ArrayList<>();
        } else {
            this.key_string = new ArrayList<>();
        }
    }

    public WebOptions withIcon() {
        this.icon = new ArrayList<>();
        return this;
    }

    public WebOptions withText() {
        this.text = new ArrayList<>();
        return this;
    }

    public WebOptions withSubtext() {
        this.subtext = new ArrayList<>();
        return this;
    }

    public WebOptions withColor() {
        this.color = new ArrayList<>();
        return this;
    }

    private List key() {
        return key_numeric != null ? key_numeric : key_string;
    }

    public WebOptions add(Object key) {
        this.key().add(key);
        return this;
    }

    public WebOptions add(Object key, String text) {
        this.key().add(key);
        this.text.add(text);
        return this;
    }

    public WebOptions add(Object key, String text, String subtext) {
        this.key().add(key);
        this.text.add(text);
        this.subtext.add(subtext);
        return this;
    }

    public WebOptions addWithIcon(Object key, String text, String subtext, String icon) {
        this.key().add(key);
        this.text.add(text);
        this.subtext.add(subtext);
        this.icon.add(icon);
        return this;
    }

    public WebOptions addWithColor(Object key, String text, String subtext, String color) {
        this.key().add(key);
        this.text.add(text);
        this.subtext.add(subtext);
        this.color.add(color);
        return this;
    }

    public WebOptions addWithColor(Object key, String text, String color) {
        this.key().add(key);
        this.text.add(text);
        this.color.add(color);
        return this;
    }
}
