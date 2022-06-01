package link.locutus.discord.util;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class ScriptUtil {
    private static final ScriptEngineManager manager = new ScriptEngineManager();
    private static final ScriptEngine engine = manager.getEngineByName("JavaScript");

    public static ScriptEngine getEngine() {
        return engine;
    }
}
