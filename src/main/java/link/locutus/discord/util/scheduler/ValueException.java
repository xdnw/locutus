package link.locutus.discord.util.scheduler;

public class ValueException extends RuntimeException {
    private final Object value;

    public ValueException(Object value) {
        super();
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
