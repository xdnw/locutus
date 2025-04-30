package link.locutus.discord.util.math;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.List;

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

    public static void main(String[] args) {
        List<TestClass> tests = List.of(new TestClass(), new TestClass(), new TestClass());

        ScriptEngine engine = getEngine();
        try {
            engine.put("tests", tests);
            engine.eval(
                    """
                    for(var i = 0; i < tests.length; i++) {
                        var test = tests[i];
                        print(test.myMethod());
                        print(test.myPrivateValue);
                    }
                    """
            );
        } catch (ScriptException e) {
            e.printStackTrace();
        }
    }
}
