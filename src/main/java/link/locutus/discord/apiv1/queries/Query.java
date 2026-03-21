package link.locutus.discord.apiv1.queries;

import link.locutus.discord.apiv1.domains.Entity;

public abstract class Query<T extends Entity> {

  String[] args;

  public Query(String... objects) {
    this.args = objects;
  }

  abstract ApiQuery<T> build();
}
