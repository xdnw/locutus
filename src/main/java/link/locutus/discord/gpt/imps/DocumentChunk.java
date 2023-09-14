package link.locutus.discord.gpt.imps;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DocumentChunk {
    // // document_chunks: source_id int, chunk_index int, converted: bool, text: string, primary key (source_id, chunk_index)
    public int source_id;
    public int chunk_index;
    public boolean converted;
    public String text;
    public String output;

    public List<String> getOutputList() {
        if (output == null) return null;
        if (output.isEmpty()) return Collections.emptyList();
        return Arrays.asList(output.split("\n- "));
    }
}
