package link.locutus.discord.util.parser;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.ICommand;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.ScriptUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ArgParser {
    private final Map<String, Command> placeholderMap = new ConcurrentHashMap<>();

    public ArgParser() {
        registerDefaults();
    }

    public Set<String> getPlaceholders() {
        return placeholderMap.keySet();
    }

    public void registerDefaults() {
        for (Field field : DBNation.class.getDeclaredFields()) {
            if ((field.getModifiers() & Modifier.VOLATILE) == 0) {
                field.setAccessible(true);
                placeholderMap.put(field.getName(), Command.create((event, guild, author, me, args, flags) -> {
                        DBNation nation = me;
                        if (nation == null && args.isEmpty()) {
                            nation = DiscordUtil.getNation(event);
                        } else if (nation == null || args.size() == 1) {
                            nation = DiscordUtil.parseNation(args.get(0));
                        }
                        if (nation == null) return null;
                        return StringMan.getString(field.get(nation));
                    }
                ));
            }
        }

        placeholderMap.put("js", Command.create(new ICommand() {
            @Override
            public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
                String msg = DiscordUtil.trimContent(event.getMessage().getContentRaw());
                if (msg.contains("[a-zA-Z]+")){
                    if (!Roles.ADMIN.hasOnRoot(author)) {
                        return null;
                    }
                }
                return ScriptUtil.getEngine().eval(msg) + "";
            }
        }));

        placeholderMap.put("random", Command.create(new ICommand() {
            @Override
            public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
                return args.get(ThreadLocalRandom.current().nextInt(args.size()));
            }
        }));

        placeholderMap.put("turn", Command.create(new ICommand() {
            @Override
            public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
                return TimeUtil.getTurn() + "";
            }
        }));

        placeholderMap.put("day", Command.create(new ICommand() {
            @Override
            public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
                return TimeUtil.getDay() + "";
            }
        }));

        placeholderMap.put("date", Command.create(new ICommand() {
            @Override
            public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
                return Instant.now().toString();
            }
        }));

        placeholderMap.put("timestamp", Command.create(new ICommand() {
            @Override
            public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
                return System.currentTimeMillis() + "";
            }
        }));

        placeholderMap.put("cityage", Command.create(new ICommand() {
             @Override
             public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
                 DBNation nation = DiscordUtil.getNation(event);
                 Set<Map.Entry<Integer, JavaCity>> cities = nation.getCityMap(false, false).entrySet();
                 List<JavaCity> citiesByDate = new ArrayList<>();
                 for (Map.Entry<Integer, JavaCity> entry : cities) {
                     citiesByDate.add(entry.getValue());
                 }
                 citiesByDate.sort((o1, o2) -> {
                     return Long.compare(o2.getAge(), o1.getAge());
                 });
                 int index = citiesByDate.size() - 1;
                 if (args.size() == 1) {
                     index= Integer.parseInt(args.get(0)) - 1;
                 }
                 return citiesByDate.get(index).getAge() + "";
             }
         }));

        placeholderMap.put("city", Command.create(new ICommand() {
            @Override
            public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
                DBNation nation = DiscordUtil.getNation(event);
                Integer index = 1;
                switch (args.size()) {
                    case 2:
                        nation = DiscordUtil.parseNation(args.get(1));
                    case 1:
                        index = MathMan.parseInt(args.get(0));
                        break;
                    default:
                        index = null;
                        break;

                }
                if (index == null || nation == null) return null;
                Set<Map.Entry<Integer, JavaCity>> cities = nation.getCityMap(true, false).entrySet();
                int i = 0;
                for (Map.Entry<Integer, JavaCity> entry : cities) {
                    if (++i == index) {
                        String url = "" + Settings.INSTANCE.PNW_URL() + "/city/id=" + entry.getKey();
                        return url;
                    }
                }
                return null;
            }
        }));

        for (Method method : DBNation.class.getDeclaredMethods()) {
            if (method.getParameters().length != 0) continue;
            if(method.getReturnType().equals(Void.TYPE)) continue;
            link.locutus.discord.commands.manager.v2.binding.annotation.Command annotation = method.getAnnotation(link.locutus.discord.commands.manager.v2.binding.annotation.Command.class);
            if (annotation == null) continue;
            Command cmd = Command.create((event, guild, author, me, args, flags) -> {
                DBNation nation = me;
                if (nation == null && args.isEmpty()) {
                    nation = DiscordUtil.getNation(event);
                } else if (nation == null || args.size() == 1) {
                    nation = DiscordUtil.parseNation(args.get(0));
                }
                if (nation == null) return null;
                    return StringMan.getString(method.invoke(nation));
            });
            String name = method.getName();
            if (name.startsWith("get")) name = name.substring(3);
            placeholderMap.putIfAbsent(name.toLowerCase(), cmd);
        }
    }

    public Command getPlaceholder(String key) {
        return placeholderMap.get(key);
    }

    public Object parsePlaceholder(Guild guild, MessageChannel channel, User user, DBNation nation, String line) throws Exception {
        line = line.substring(1, line.length() - 1);
        int i = line.indexOf(' ');
        String key = line;
        String args = "";
        if (i != -1) {
            key = line.substring(0, i);
            args = line.substring(i + 1);
        }
        Command placeholder = getPlaceholder(key);
        if (placeholder != null) {
            if (nation != null) {
                String finalArgs = args;
                return DiscordUtil.withNation(nation, new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        return placeholder.onCommand(guild, channel, user, nation, finalArgs);
                    }
                }, false);
            } else {
                return placeholder.onCommand(guild, channel, user, nation, args);
            }
        }
        return null;
    }

    public String parse(Guild guild, MessageChannel channel, User user, DBNation nation, String line) {
        return parse(guild, channel, user, nation, line, 0);
    }

    public String parse(Guild guild, MessageChannel channel, User user, DBNation nation, String line, int recursion) {
        try {
            int q = 0;
            List<Integer> indicies = null;
            for (int i = 0; i < line.length(); i++) {
                char current = line.charAt(i);
                if (current == '{') {
                    if (indicies == null) indicies = new ArrayList<>();
                    indicies.add(i);
                    q++;
                } else if (current == '}' && indicies != null) {
                    if (q > 0) {
                        if (recursion < 513) {
                            q--;
                            int lastindx = indicies.size() - 1;
                            int start = indicies.get(lastindx);
                            String arg = line.substring(start, i + 1);

                            Object result;
                            try {
                                result = parsePlaceholder(guild, channel, user, nation, arg);
                            } catch (Exception e) {
                                e.printStackTrace();
                                result = null;
                            }
                            if (result != null) {
                                line = new StringBuffer(line).replace(start, i + 1, result + "").toString();
                            }
                            indicies.remove(lastindx);
                            i = start;
                        }
                    }
                }
            }
            return line;
        } catch (Exception e2) {
            e2.printStackTrace();
            return "";
        }
    }
}