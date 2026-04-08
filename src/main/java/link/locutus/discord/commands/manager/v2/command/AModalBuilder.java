package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.util.RateLimitedSource;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AModalBuilder implements IModalBuilder {
    private final IMessageIO parent;
    private UUID id;
    private String title;
    private final List<Label> inputs = new ArrayList<>();

    public AModalBuilder(IMessageIO io, String id, String title) {
        this.parent = io;
        this.title = title;
    }

    @Override
    public AModalBuilder setTitle(String title) {
        this.title = title;
        return this;
    }

    public IModalBuilder addInput(String label, TextInput input) {
        inputs.add(Label.of(label, input));
        return this;
    }

    public IMessageIO getParent() {
        return parent;
    }

    public CompletableFuture<IModalBuilder> send(RateLimitedSource source) {
        return parent.send(this, source);
    }

    public UUID getId() {
        return id;
    }

    @Override
    public IModalBuilder setId(UUID id) {
        this.id = id;
        return this;
    }

    @Override
    public String getTitle() {
        return title;
    }

    public List<Label> getInputComponents() {
        return new ArrayList<>(inputs);
    }

    public List<TextInput> getInputs() {
        List<TextInput> textInputs = new ArrayList<>(inputs.size());
        for (Label input : inputs) {
            if (input.getChild() instanceof TextInput textInput) {
                textInputs.add(textInput);
            }
        }
        return textInputs;
    }
}
