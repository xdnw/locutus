package link.locutus.discord.gpt;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ModerationResult {
    private boolean flagged;
    private boolean error;
    private String message;
    private Map<String, Double> scores;
    private Set<String> flaggedCategories;

    public ModerationResult() {
        this.flagged = false;
        this.error = false;
        this.message = "";
        this.scores = new HashMap<>();
        this.flaggedCategories = new HashSet<>();
    }

    public boolean isFlagged() {
        return flagged;
    }

    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Double> getScores() {
        return scores;
    }

    public void setScores(Map<String, Double> scores) {
        this.scores = scores;
    }

    public Set<String> getFlaggedCategories() {
        return flaggedCategories;
    }

    public void setFlaggedCategories(Set<String> flaggedCategories) {
        this.flaggedCategories = flaggedCategories;
    }
}