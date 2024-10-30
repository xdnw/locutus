package link.locutus.discord.commands.manager.v2.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WebOptions {
    private List<String> icon;
    private List<Object> key;
    private List<String> text;
    private List<String> subtext;
    private List<String> color;

    public WebOptions() {
        this.key = new ArrayList<>();
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

    public WebOptions add(Object key) {
        this.key.add(key);
        return this;
    }

    public WebOptions add(Object key, String text) {
        this.key.add(key);
        this.text.add(text);
        return this;
    }

    public WebOptions add(Object key, String text, String subtext) {
        this.key.add(key);
        this.text.add(text);
        this.subtext.add(subtext);
        return this;
    }

    public WebOptions addWithIcon(Object key, String text, String subtext, String icon) {
        this.key.add(key);
        this.text.add(text);
        this.subtext.add(subtext);
        this.icon.add(icon);
        return this;
    }

    public WebOptions addWithColor(Object key, String text, String subtext, String color) {
        this.key.add(key);
        this.text.add(text);
        this.subtext.add(subtext);
        this.color.add(color);
        return this;
    }

    public WebOptions addWithColor(Object key, String text, String color) {
        this.key.add(key);
        this.text.add(text);
        this.color.add(color);
        return this;
    }

    public Map<String, Object> build() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        if (text != null) {
            result.put("text", text);
        }
        if (subtext != null) {
            result.put("subtext", subtext);
        }
        if (color != null) {
            result.put("color", color);
        }
        if (icon != null) {
            result.put("icon", icon);
        }
        return result;
    }
}
