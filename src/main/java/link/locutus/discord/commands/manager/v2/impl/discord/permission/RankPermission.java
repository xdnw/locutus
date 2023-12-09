package link.locutus.discord.commands.manager.v2.impl.discord.permission;

import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
public @interface RankPermission {
    @Command(desc = "The rank required (default: MEMBER)")
    Rank value() default Rank.MEMBER;
}
