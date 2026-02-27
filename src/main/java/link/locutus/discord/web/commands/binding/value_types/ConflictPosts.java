package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;
import java.util.Map;

/**
 * Response for the conflictPosts endpoint.
 * - {@code posts}: conflictId → {description → [topic_id, topic_urlname, timestamp]}
 */
public class ConflictPosts {
    public Map<Integer, Map<String, List>> posts;

    public ConflictPosts() {
    }

    public ConflictPosts(Map<Integer, Map<String, List>> posts) {
        this.posts = posts;
    }
}