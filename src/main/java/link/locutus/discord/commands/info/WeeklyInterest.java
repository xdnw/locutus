package link.locutus.discord.commands.info;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.util.MathMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class WeeklyInterest extends Command {
    public WeeklyInterest() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.ECON);
    }

    @Override
    public String help() {
        return super.help() + " <amount> <pct-weekly> <num-weeks>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 3) return usage(args.size(), 3, channel);
        Double amount = MathMan.parseDouble(args.get(0));
        Double pct = MathMan.parseDouble(args.get(1).replaceAll("%", ""));
        Integer weeks = MathMan.parseInt(args.get(2));

        if (amount == null) return "Invalid amount: `" + args.get(0) + "`";
        if (pct == null) return "Invalid %: `" + args.get(1) + "`";
        if (weeks == null) return "Invalid weeks: `" + args.get(2) + "`";

        double totalInterest = weeks * (pct / 100d) * amount;

        double weeklyPayments = (totalInterest + amount) / weeks;

        StringBuilder result = new StringBuilder("```");
        result.append("Principle Amount: $").append(MathMan.format(amount)).append("\n");
        result.append("Loan Interest Rate: ").append(MathMan.format(pct)).append("%\n");
        result.append("Total Interest: $").append(MathMan.format(totalInterest)).append("\n");
        result.append("Weekly Payments: $").append(MathMan.format(weeklyPayments)).append("\n");

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        int day = calendar.get(Calendar.DAY_OF_WEEK);

        int dayOffset = 0;
        switch (day) {
            case Calendar.MONDAY, Calendar.TUESDAY -> dayOffset = Calendar.FRIDAY - day;
            case Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY ->
                    dayOffset += 7 + Calendar.FRIDAY - day;
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime due = now.plusDays(dayOffset);
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("EEEE dd MMMM", Locale.ENGLISH);

        result.append("Today: ").append(pattern.format(now)).append("\n");
        String repeating = pattern.format(due) + " and every Friday thereafter for a total of " + weeks + " weeks.";
        result.append("First Payment Due: ").append(repeating).append("```");

        return result.toString();
    }
}
