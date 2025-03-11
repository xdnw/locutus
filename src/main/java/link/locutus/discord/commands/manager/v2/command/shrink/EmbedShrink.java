package link.locutus.discord.commands.manager.v2.command.shrink;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class EmbedShrink implements IShrink {
    private IShrink title;
    private IShrink description;
    private IShrink footer;

    private List<ShrinkableField> fields = new ObjectArrayList<>();
    private Color color;

    public EmbedShrink() {
        title = EmptyShrink.EMPTY;
        description = EmptyShrink.EMPTY;
        footer = EmptyShrink.EMPTY;
    }

    public EmbedShrink(EmbedShrink embed) {
        this.title = embed.title.clone();
        this.description = embed.description.clone();
        this.footer = embed.footer == null ? null : embed.footer.clone();
        this.fields = new ObjectArrayList<>(embed.fields.size());
        this.color = embed.color;
        for (ShrinkableField field : embed.fields) {
            this.fields.add(field.clone());
        }
    }

    public EmbedShrink(MessageEmbed embed) {
        this.title = IdenticalShrink.of(embed.getTitle() == null ? "" : embed.getTitle());
        this.description = IdenticalShrink.of(embed.getDescription() == null ? "" : embed.getDescription());
        MessageEmbed.Footer footerObj = embed.getFooter();
        if (footerObj != null) {
            String footerStr = footerObj.getText();
            if (footerStr != null && !footerStr.isEmpty()) {
                this.footer = IdenticalShrink.of(footerStr);
            }
        }
        this.color = embed.getColor();
        for (MessageEmbed.Field field : embed.getFields()) {
            fields.add(new ShrinkableField(field.getName(), field.getValue(), field.isInline()));
        }
    }

    public EmbedShrink title(IShrink title) {
        this.title = title;
        return this;
    }

    public EmbedShrink description(IShrink description) {
        this.description = description;
        return this;
    }

    public EmbedShrink footer(IShrink footer) {
        this.footer = footer;
        return this;
    }

    public EmbedShrink field(IShrink name, IShrink value, boolean inline) {
        this.fields.add(new ShrinkableField(name, value, inline));
        return this;
    }

    public EmbedShrink shrink(int totalSize, int titleSize, int descSize, int footerSize, int valueSize) {
        List<IShrink> all = new ObjectArrayList<>(3 + fields.size() * 2);
        if (title != null) all.add(title);
        if (description != null) all.add(description);
        if (footer != null) all.add(footer);
        for (ShrinkableField field : fields) {
            if (field.name == null || field.value == null) System.out.println("Field is null");
            all.add(field.name);
            all.add(field.value);
        }
        IShrink.shrink(all, totalSize);

        title.shrink(titleSize);
        if (description != null) description.shrink(descSize);
        if (footer != null && !footer.isEmpty()) footer.shrink(footerSize);
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

    @Override
    public String toString() {
        return get();
    }

    public MessageEmbed build() {
        return builder().build();
    }

    public IShrink getTitle() {
        return title;
    }

    public IShrink getDescription() {
        return description;
    }

    public List<ShrinkableField> getFields() {
        return fields;
    }

    public IShrink getFooter() {
        return footer;
    }

    public EmbedShrink shrinkDefault() {
        return shrink(MessageEmbed.EMBED_MAX_LENGTH_BOT, MessageEmbed.TITLE_MAX_LENGTH, MessageEmbed.DESCRIPTION_MAX_LENGTH, MessageEmbed.TEXT_MAX_LENGTH, MessageEmbed.VALUE_MAX_LENGTH);
    }

    public EmbedShrink setTitle(String title) {
        this.title = IdenticalShrink.of(title);
        return this;
    }

    public EmbedShrink setDescription(String description) {
        this.description = IdenticalShrink.of(description);
        return this;
    }

    public EmbedShrink setFooter(String footer) {
        this.footer = footer == null ? null : IdenticalShrink.of(footer);
        return this;
    }

    public EmbedShrink addField(String name, String value, boolean inline) {
        this.fields.add(new ShrinkableField(name, value, inline));
        return this;
    }

    public Map<String, Object> toData() {
        shrinkDefault();
        Map<String, Object> data = new Object2ObjectLinkedOpenHashMap<>();
        data.put("title", title.get());
        if (description != null && !description.isEmpty()) data.put("description", description.get());
        if (footer != null && !footer.isEmpty()) {
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

    public EmbedShrink setColor(Color color) {
        this.color = color;
        return this;
    }

    public EmbedShrink append(String s) {
        if (this.description == null) {
            this.description = IdenticalShrink.of(s);
        } else {
            this.description = this.description.append(s);
        }
        return this;
    }

    @Override
    public IShrink prepend(String s) {
        this.description = description.prepend(s);
        return this;
    }

    @Override
    public IShrink append(IShrink s) {
        this.description = description.append(s);
        return this;
    }

    @Override
    public IShrink prepend(IShrink s) {
        this.description = description.prepend(s);
        return this;
    }

    @Override
    public IShrink clone() {
        return new EmbedShrink(this);
    }

    @Override
    public int getSize() {
        return title.getSize() + description.getSize() + (footer == null ? 0 : footer.getSize()) + fields.stream().mapToInt(ShrinkableField::getSize).sum();
    }

    @Override
    public int shrink(int totalSize) {
        int size = getSize();
        if (size <= totalSize) return 0;
        int originalSize = size;
        size =- title.shrink();
        if (size <= totalSize) return originalSize - size;
        if (footer != null) {
            size -= footer.shrink();
            if (size <= totalSize) return originalSize - size;
        }
        size -= description.shrink();
        if (size <= totalSize) return originalSize - size;
        for (ShrinkableField field : fields) {
            size -= field.shrink();
            if (size <= totalSize) return originalSize - size;
        }
        return originalSize - size;
    }

    @Override
    public int shrink() {
        int diff = title.shrink() + description.shrink() + (footer == null ? 0 : footer.shrink());
        for (ShrinkableField field : fields) {
            diff += field.shrink();
        }
        return diff;
    }

    @Override
    public boolean isIdentical() {
        return title.isIdentical() && (description == null || description.isIdentical()) && (footer == null || footer.isIdentical()) && fields.stream().allMatch(ShrinkableField::isIdentical);
    }

    @Override
    public String get() {
        StringBuilder contentShrink = new StringBuilder();
        shrinkDefault();
        contentShrink.append("## ").append(getTitle()).append("\n")
                .append(">>> ").append(getDescription()).append("\n");
        IShrink footer = getFooter();
        if (footer != null && !footer.isEmpty()) {
            contentShrink.append("_").append(footer).append("_\n");
        }
        if (getFields() != null) {
            for (ShrinkableField field : getFields()) {
                contentShrink.append("> **").append(field.name).append("**: ").append(field.value).append("\n");
            }
        }
        return contentShrink.toString();
    }

    @Override
    public boolean isEmpty() {
        return title.isEmpty() && (description == null || description.isEmpty()) && (footer == null || footer.isEmpty()) && fields.stream().allMatch(ShrinkableField::isEmpty);
    }

    public EmbedShrink description(String s) {
        this.description = IdenticalShrink.of(s);
        return this;
    }

    public EmbedShrink title(String title) {
        this.title = IdenticalShrink.of(title);
        return this;
    }

    public EmbedShrink footer(String footer) {
        this.footer = footer == null ? null : IdenticalShrink.of(footer);
        return this;
    }
}
