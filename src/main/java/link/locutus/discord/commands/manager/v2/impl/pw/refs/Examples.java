package link.locutus.discord.commands.manager.v2.impl.pw.refs;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.db.entities.DBNation;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Examples {

    private Map<CommandRef, ExampleFunc[]> examples = new Object2ObjectArrayMap<>();

    public static interface ExampleFunc {
        Map.Entry<CommandRef, String>get(Guild guild, User user, DBNation nation, IMessageIO io);
    }

    public Examples setupExamples() {
        return this;
    }

    public Map<CommandRef, ExampleFunc[]> getExamples() {
        return examples;
    }

    public Map<ParametricCallable, ExampleFunc[]> getCallableExamples() {
        Map<ParametricCallable, ExampleFunc[]> callableExamples = new ConcurrentHashMap<>();
        examples.forEach((ref, funcs) -> {
            ParametricCallable callable = ref.getCallable(false);
            if (callable != null) {
                callableExamples.put(callable, funcs);
            } else {
                System.out.println("No callable for " + ref);
            }
        });
        return callableExamples;
    }
}
