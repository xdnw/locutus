package link.locutus.discord.apiv3;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.politicsandwar.graphql.model.CityInfraDamage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CityInfraDamageDeserializer extends StdDeserializer<Object> {
    protected CityInfraDamageDeserializer() {
        super(CityInfraDamage.class);
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        JsonNode node = p.getCodec().readTree(p);
        if (node.has("id")) {
            return new CityInfraDamage(node.get("id").asInt(), node.get("infrastructure").asDouble(), null);
        } else {
            List<CityInfraDamage> infraDamages = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                int id = Integer.parseInt(entry.getKey());
                double infra = entry.getValue().asDouble();
                infraDamages.add(new CityInfraDamage(id, infra, null));
            }
            return (Object) infraDamages;
        }
    }
}