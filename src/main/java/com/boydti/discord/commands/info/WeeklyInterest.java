package com.boydti.discord.commands.info;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.TimeUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

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
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.size() != 3) return usage(event);
        Double amount = MathMan.parseDouble(args.get(0));
        Double pct = MathMan.parseDouble(args.get(1).replaceAll("%", ""));
        Integer weeks = MathMan.parseInt(args.get(2));

        if (amount == null) return "Invalid amount: `" + args.get(0) + "`";
        if (pct == null) return "Invalid %: `" + args.get(1) + "`";
        if (weeks == null) return "Invalid weeks: `" + args.get(2) + "`";

        double totalInterest = weeks * (pct / 100d) * amount;

        double weeklyPayments = (totalInterest + amount) / weeks;

        StringBuilder result = new StringBuilder("```");
        result.append("Principle Amount: $" + MathMan.format(amount)).append("\n");
        result.append("Loan Interest Rate: " + MathMan.format(pct)).append("%\n");
        result.append("Total Interest: $" + MathMan.format(totalInterest)).append("\n");
        result.append("Weekly Payments: $" + MathMan.format(weeklyPayments)).append("\n");

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        int day = calendar.get(Calendar.DAY_OF_WEEK);

        int dayOffset = 0;
        switch (day) {
            case Calendar.MONDAY:
            case Calendar.TUESDAY:
                dayOffset = Calendar.FRIDAY - day;
                break;
            case Calendar.WEDNESDAY:
            case Calendar.THURSDAY:
            case Calendar.FRIDAY:
            case Calendar.SATURDAY:
            case Calendar.SUNDAY:
                dayOffset += 7 + Calendar.FRIDAY - day;
                break;
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime due = now.plusDays(dayOffset);
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("EEEE dd MMMM", Locale.ENGLISH);

        result.append("Today: " + pattern.format(now)).append("\n");
        String repeating = pattern.format(due) + " and every Friday thereafter for a total of " + weeks + " weeks.";
        result.append("First Payment Due: " + repeating).append("```");

        return result.toString();
    }
}
