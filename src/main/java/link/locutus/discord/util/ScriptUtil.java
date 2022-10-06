package link.locutus.discord.util;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

public class ScriptUtil {
    private static final NashornScriptEngineFactory manager = new NashornScriptEngineFactory();
    private static final ScriptEngine engine = manager.getScriptEngine();

    public static ScriptEngine getEngine() {
        return engine;
    }
}
