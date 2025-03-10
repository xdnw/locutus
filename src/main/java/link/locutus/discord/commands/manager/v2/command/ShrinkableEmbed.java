package link.locutus.discord.commands.manager.v2.command;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class ShrinkableEmbed {
    private Shrinkable title;
    private IShrinkable description;

    private Shrinkable footer;
    private List<ShrinkableField> fields = new ObjectArrayList<>();
    private Color color;

    public ShrinkableEmbed() {

    }

    public ShrinkableEmbed(ShrinkableEmbed embed) {
        this.title = embed.title.clone();
        this.description = embed.description.clone();
        this.footer = embed.footer == null ? null : embed.footer.clone();
        this.fields = new ObjectArrayList<>(embed.fields.size());
        this.color = embed.color;
        for (ShrinkableField field : embed.fields) {
            this.fields.add(field.clone());
        }
    }

    public ShrinkableEmbed(MessageEmbed embed) {
        this.title = Shrinkable.of(embed.getTitle() == null ? "" : embed.getTitle());
        this.description = Shrinkable.of(embed.getDescription() == null ? "" : embed.getDescription());
        MessageEmbed.Footer footerObj = embed.getFooter();
        if (footerObj != null) {
            String footerStr = footerObj.getText();
            if (footerStr != null && !footerStr.isEmpty()) {
                this.footer = Shrinkable.of(footerStr);
            }
        }
        this.color = embed.getColor();
        for (MessageEmbed.Field field : embed.getFields()) {
            fields.add(new ShrinkableField(field.getName(), field.getValue(), field.isInline()));
        }
    }

    public ShrinkableEmbed title(Shrinkable title) {
        this.title = title;
        return this;
    }

    public ShrinkableEmbed description(Shrinkable description) {
        this.description = description;
        return this;
    }

    public ShrinkableEmbed footer(Shrinkable footer) {
        this.footer = footer;
        return this;
    }

    public ShrinkableEmbed field(Shrinkable name, Shrinkable value, boolean inline) {
        this.fields.add(new ShrinkableField(name, value, inline));
        return this;
    }

    public ShrinkableEmbed shrink(int totalSize, int titleSize, int descSize, int footerSize, int valueSize) {
        List<Shrinkable> all = new ObjectArrayList<>(3 + fields.size() * 2);
        if (title != null) all.add(title);
        if (description != null) all.add(description);
        if (footer != null) all.add(footer);
        for (ShrinkableField field : fields) {
            if (field.name == null || field.value == null) System.out.println("Field is null");
            all.add(field.name);
            all.add(field.value);
        }
        Shrinkable.shrink(totalSize, all);

        title.shrink(titleSize);
        description.shrink(descSize);
        if (footer != null) footer.shrink(footerSize);
        for (ShrinkableField field : fields) {
            field.name.shrink(titleSize);
            field.value.shrink(valueSize);
        }
        return this;
    }

    public EmbedBuilder builder() {
        shrinkDefault();
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(title.get());
        if (description != null) {
            builder.setDescription(description.get());
        }
        if (footer != null) {
            builder.setFooter(footer.get());
        }
        if (color != null) {
            builder.setColor(color);
        }
        return builder;
    }

    public MessageEmbed build() {
        return builder().build();
    }

    public Shrinkable getTitle() {
        return title;
    }

    public Shrinkable getDescription() {
        return description;
    }

    public List<ShrinkableField> getFields() {
        return fields;
    }

    public Shrinkable getFooter() {
        return footer;
    }

    public ShrinkableEmbed shrinkDefault() {
        return shrink(MessageEmbed.EMBED_MAX_LENGTH_BOT, MessageEmbed.TITLE_MAX_LENGTH, MessageEmbed.DESCRIPTION_MAX_LENGTH, MessageEmbed.TEXT_MAX_LENGTH, MessageEmbed.VALUE_MAX_LENGTH);
    }

    public ShrinkableEmbed setTitle(String title) {
        this.title = Shrinkable.of(title);
        return this;
    }

    public ShrinkableEmbed setDescription(String description) {
        this.description = Shrinkable.of(description);
        return this;
    }

    public ShrinkableEmbed setFooter(String footer) {
        this.footer = footer == null ? null : Shrinkable.of(footer);
        return this;
    }

    public ShrinkableEmbed addField(String name, String value, boolean inline) {
        this.fields.add(new ShrinkableField(name, value, inline));
        return this;
    }

    public Map<String, Object> toData() {
        shrinkDefault();
        Map<String, Object> data = new Object2ObjectLinkedOpenHashMap<>();
        data.put("title", title.get());
        data.put("description", description.get());
        if (footer != null && !footer.get().isEmpty()) {
            data.put("footer", Map.of("text", footer.get()));
        }
        if (!fields.isEmpty()) {
            List<Map<String, Object>> fieldsData = new ObjectArrayList<>(fields.size());
            for (ShrinkableField field : fields) {
                fieldsData.add(Map.of("name", field.name.get(), "value", field.value.get(), "inline", field.inline));
            }
            data.put("fields", fieldsData);
        }
        if (color != null) {
            data.put("color",  color.getRGB() & 0xFFFFFF);
        }
        return data;
    }

    public ShrinkableEmbed setColor(Color color) {
        this.color = color;
        return this;
    }

    public ShrinkableEmbed append(String s) {
        if (this.description == null) {
            this.description = Shrinkable.of(s);
        } else {
            this.description.append(s);
        }
        return this;
    }

    public ShrinkableEmbed description(String s) {
        this.description = Shrinkable.of(s);
        return this;
    }

    public ShrinkableEmbed title(String title) {
        this.title = Shrinkable.of(title);
        return this;
    }
}
