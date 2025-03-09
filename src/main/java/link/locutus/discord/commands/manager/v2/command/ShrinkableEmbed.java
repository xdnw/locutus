package link.locutus.discord.commands.manager.v2.command;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.util.ImageUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ShrinkableEmbed {
    private Shrinkable title;
    private Shrinkable description;
    private Shrinkable footer;
    private List<ShrinkableField> fields = new ObjectArrayList<>();

    public ShrinkableEmbed() {

    }

    public ShrinkableEmbed(ShrinkableEmbed embed) {
        this.title = embed.title.clone();
        this.description = embed.description.clone();
        this.footer = embed.footer.clone();
        this.fields = new ObjectArrayList<>(embed.fields.size());
        for (ShrinkableField field : embed.fields) {
            this.fields.add(field.clone());
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

        return this;
    }

    public ShrinkableEmbed shrink(int totalSize, int titleSize, int descSize, int footerSize, int valueSize) {
        List<Shrinkable> all = new ObjectArrayList<>(3 + fields.size() * 2);
        all.add(title);
        all.add(description);
        all.add(footer);
        for (ShrinkableField field : fields) {
            all.add(field.name);
            all.add(field.value);
        }
        Shrinkable.shrink(totalSize, all);

        title.shrink(titleSize);
        description.shrink(descSize);
        footer.shrink(footerSize);
        for (ShrinkableField field : fields) {
            field.name.shrink(titleSize);
            field.value.shrink(valueSize);
        }
        return this;
    }

    public MessageEmbed build() {
        shrinkDefault();
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(title.get());
        if (description != null) {
            builder.setDescription(description.get());
        }
        if (footer != null) {
            builder.setFooter(footer.get());
        }
        return builder.build();
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
        this.footer = Shrinkable.of(footer);
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
        return data;
    }
}
