package com.boydti.discord.commands.manager.v2.impl.pw.binding;

import com.boydti.discord.commands.manager.v2.binding.annotation.Command;
import com.boydti.discord.commands.manager.v2.binding.annotation.Default;
import com.boydti.discord.commands.manager.v2.binding.annotation.Me;
import com.boydti.discord.commands.manager.v2.binding.annotation.TextArea;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.ScriptUtil;
import com.boydti.discord.util.TimeUtil;
import com.boydti.discord.apiv1.enums.city.JavaCity;
import net.dv8tion.jda.api.entities.User;

import javax.script.ScriptException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class StandardPlaceholders {

    @Command String js(@Me User author, @TextArea String text) throws ScriptException {
        String msg = text;
        if (msg.contains("[a-zA-Z]+")){
            if (!Roles.ADMIN.hasOnRoot(author)) {
                return null;
            }
        }
        return ScriptUtil.getEngine().eval(msg) + "";
    }

    @Command
    public String random(List<String> args) {
        return args.get(ThreadLocalRandom.current().nextInt(args.size()));
    }

    @Command
    public String day() {
        return TimeUtil.getDay() + "";
    }

    @Command
    public String turn() {
        return TimeUtil.getTurn() + "";
    }

    @Command(desc = "Get the timestamp")
    public String timestamp() {
        return System.currentTimeMillis() + "";
    }

    @Command public String date() {
        return Instant.now().toString();
    }

    @Command(desc = "Get the city url")
    public String city(@Me DBNation nation, int index, @Default DBNation other) {
        if (other == null) other = nation;
        Set<Map.Entry<Integer, JavaCity>> cities = other.getCityMap(false, false).entrySet();
        List<JavaCity> citiesByDate = new ArrayList<>();
        for (Map.Entry<Integer, JavaCity> entry : cities) {
            citiesByDate.add(entry.getValue());
        }
        citiesByDate.sort((o1, o2) -> {
            return Long.compare(o2.getAge(), o1.getAge());
        });
        return citiesByDate.get(index).getAge() + "";
    }
}
