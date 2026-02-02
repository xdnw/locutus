package link.locutus.discord._main;

import java.util.List;

public class TaskDetails {
    public boolean found;
    public TaskSummary summary;
    public List<ErrorSample> errors;
    /** Recent run history (bounded here to last hour) */
    public long sinceMs;
    public RunHistorySnapshot history;
}
