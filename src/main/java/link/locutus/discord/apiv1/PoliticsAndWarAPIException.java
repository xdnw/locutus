package link.locutus.discord.apiv1;

public class PoliticsAndWarAPIException extends RuntimeException {
  public PoliticsAndWarAPIException() {
    super();
  }

  public PoliticsAndWarAPIException(String message) {
    super(message);
  }

  public PoliticsAndWarAPIException(Throwable cause) {
    super(cause);
  }

  public PoliticsAndWarAPIException(String message, String url) {
    super(message + ". URL OF REQUEST: " + url);
  }

  @Override
  public synchronized Throwable fillInStackTrace() {
    return super.fillInStackTrace();
  }
}
