package link.locutus.discord.gpt.imps;

import java.util.List;

public class DocumentChunk {
    // // document_chunks: source_id int, chunk_index int, converted: bool, text: string, primary key (source_id, chunk_index)
    public int source_id;
    public int chunk_index;
    public boolean converted;
    public String text;
    public String output;
}
