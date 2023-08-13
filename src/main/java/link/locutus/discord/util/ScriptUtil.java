package link.locutus.discord.util;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class ScriptUtil {
    private static final NashornScriptEngineFactory manager = new NashornScriptEngineFactory();
    private static final ScriptEngine engine = manager.getScriptEngine();

    public static ScriptEngine getEngine() {
        return engine;
    }

    public static Object evalNumber(String input) throws ScriptException {
        if (input.matches("[\\d\\s()+\\-*/^.]+")) {
            return getEngine().eval(input);
        }
        throw new IllegalArgumentException("Invalid number or math expression: `" + input + "`");
    }
}
