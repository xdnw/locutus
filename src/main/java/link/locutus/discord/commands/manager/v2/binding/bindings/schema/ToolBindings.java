package link.locutus.discord.commands.manager.v2.binding.bindings.schema;

import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.db.entities.MMRDouble;
import link.locutus.discord.db.entities.MMRInt;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Message;
import org.json.JSONObject;

import java.awt.*;
import java.util.Map;
import java.util.UUID;

public class ToolBindings extends BindingHelper {

    @Binding(types = Color.class)
    public static Map<String, Object> color() {
        return Map.of(
                "type", "string",
                "pattern", "^(#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})|(?i)(black|blue|cyan|darkGray|gray|green|lightGray|magenta|orange|pink|red|white|yellow))$"
        );
    }

    // UUID
    // {"type":"string","format":"uuid"}
    @Binding(types = UUID.class)
    public static Map<String, Object> uuid() {
        return Map.of(
                "type", "string",
                "format", "uuid"
        );
    }

    // Message
    // {"type":"string","pattern":"^https://(?:canary\\.|ptb\\.)?discord(?:app)?\\.com/channels/\\d+/\\d+/\\d+$"}
    @Binding(types = Message.class)
    public static Map<String, Object> message() {
        return Map.of(
                "type", "string",
                "pattern", "^(https://)?(canary\\.|ptb\\.)?discord(?:app)?\\.com/channels/\\d+/\\d+/\\d+$"
        );
    }

    // JSONObject
    // {"type":"object","description":"Command represented as JSON. The empty-string key (\"\") stores the command name. Additional properties are command parameters with string values.","properties":{"":{"type":"string","description":"Command name"}},"required":[""],"additionalProperties":{"type":"string","description":"Parameter values"}}
    @Binding(types = JSONObject.class)
    public static Map<String, Object> jsonObject() {
        return Map.of(
                "type", "object",
                "description", "Command represented as JSON. The empty-string key (\"\") stores the command name. Additional properties are command parameters with string values.",
                "properties", Map.of(
                        "", Map.of(
                                "type", "string",
                                "description", "Command pathname"
                        )
                ),
                "required", new String[]{""},
                "additionalProperties", Map.of(
                        "type", "string",
                        "description", "Parameter values"
                )
        );
    }


    // CityBuild
    // {"CityBuild":{"type":"object","additionalProperties":false,"properties":{"cityId":{"type":"integer","minimum":0},"cityIndex":{"type":"integer","minimum":0,"maximum":100},"buildings":{"type":"object","propertyNames":{"enum":["land","age","infra","coalpower","oilpower","windpower","nuclearpower","coalmine","oilwell","uramine","leadmine","ironmine","bauxitemine","farm","gasrefinery","aluminumrefinery","munitionsfactory","steelmill","policestation","hospital","recyclingcenter","subway","supermarket","bank","mall","stadium","barracks","factory","hangars","drydock"]},"additionalProperties":{"type":"number","minimum":0}}},"oneOf":[{"required":["cityId"]},{"required":["cityIndex"]},{"required":["buildings"]}],"not":{"required":["cityId","cityIndex"]}}}
    @Binding(types = CityBuild.class)
    public static Map<String, Object> cityBuild() {
        return Map.of(
                "CityBuild", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", Map.of(
                                "cityId", Map.of(
                                        "type", "integer",
                                        "minimum", 0
                                ),
                                "cityIndex", Map.of(
                                        "type", "integer",
                                        "minimum", 0,
                                        "maximum", 100
                                ),
                                "buildings", Map.of(
                                        "type", "object",
                                        "propertyNames", Map.of(
                                                "enum", new String[]{
                                                        "land","age","infra","coalpower","oilpower","windpower","nuclearpower","coalmine","oilwell","uramine","leadmine","ironmine","bauxitemine","farm","gasrefinery","aluminumrefinery","munitionsfactory","steelmill","policestation","hospital","recyclingcenter","subway","supermarket","bank","mall","stadium","barracks","factory","hangars","drydock"
                                                }
                                        ),
                                        "additionalProperties", Map.of(
                                                "type", "number",
                                                "minimum", 0
                                        )
                                )
                        ),
                        "oneOf", new Object[]{
                                Map.of("required", new String[]{"cityId"}),
                                Map.of("required", new String[]{"cityIndex"}),
                                Map.of("required", new String[]{"buildings"})
                        },
                        "not", Map.of(
                                "required", new String[]{"cityId","cityIndex"}
                        )
                )
        );
    }

    // MMRInt
    // {"type":"array","items":[{"type":"integer","minimum":0,"maximum":5},{"type":"integer","minimum":0,"maximum":5},{"type":"integer","minimum":0,"maximum":5},{"type":"integer","minimum":0,"maximum":3}],"additionalItems":false}
        @Binding(types = MMRInt.class)
        public static Map<String, Object> mmrInt() {
            return Map.of(
                    "type", "array",
                    "items", new Object[]{
                            Map.of(
                                    "type", "integer",
                                    "minimum", 0,
                                    "maximum", 5
                            ),
                            Map.of(
                                    "type", "integer",
                                    "minimum", 0,
                                    "maximum", 5
                            ),
                            Map.of(
                                    "type", "integer",
                                    "minimum", 0,
                                    "maximum", 5
                            ),
                            Map.of(
                                    "type", "integer",
                                    "minimum", 0,
                                    "maximum", 3
                            )
                    },
                    "additionalItems", false
            );
        }

    // MMRDouble
    // {"type":"array","items":{"type":"number"},"minItems":4,"maxItems":4,"additionalItems":false}
    @Binding(types = MMRDouble.class)
    public static Map<String, Object> mmrDouble() {
        return Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "number",
                        "minimum", 0
                ),
                "minItems", 4,
                "maxItems", 4,
                "additionalItems", false
        );
    }

    // TaxRate
    // {"type":"array","items":{"type":"integer","minimum":0,"maximum":100},"minItems":2,"maxItems":2,"additionalItems":false}
    @Binding(types = TaxRate.class)
    public static Map<String, Object> taxRate() {
        return Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "integer",
                        "minimum", 0,
                        "maximum", 100
                ),
                "minItems", 2,
                "maxItems", 2,
                "additionalItems", false
        );
    }

    // SpreadSheet
    // {"type":"string","anyOf":[{"pattern":"^sheet:[A-Za-z0-9-_]+(?:,[A-Za-z0-9 _-]+)?$"},{"pattern":"^https://docs\\.google\\.com/spreadsheets/d/[A-Za-z0-9-_]+(?:/edit)?(?:#(?:gid=\\d+|tab=[^&#]+))?$"}]}
    @Binding(types = SpreadSheet.class)
    public static Map<String, Object> spreadSheet() {
        return Map.of(
                "type", "string",
                "anyOf", new Object[]{
                        Map.of(
                                "pattern", "^sheet:[A-Za-z0-9-_]+(?:,[A-Za-z0-9 _-]+)?$"
                        ),
                        Map.of(
                                "pattern", "^https://docs\\.google\\.com/spreadsheets/d/[A-Za-z0-9-_]+(?:/edit)?(?:#(?:gid=\\d+|tab=[^&#]+))?$"
                        )
                }
        );
    }
}
