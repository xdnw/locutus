package link.locutus.discord.apiv1.queries;

public abstract class Query {

  String[] args;

  public Query(String... objects) {
    this.args = objects;
  }

  abstract ApiQuery build();
}
