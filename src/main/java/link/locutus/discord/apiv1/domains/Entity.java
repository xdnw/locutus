package link.locutus.discord.apiv1.domains;

import link.locutus.discord.web.WebUtil;

public abstract class Entity {

  public String toJson() {
    return WebUtil.GSON.toJson(this);
  }
}
