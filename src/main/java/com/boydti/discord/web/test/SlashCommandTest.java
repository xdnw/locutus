package com.boydti.discord.web.test;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

public class SlashCommandTest extends ListenerAdapter {

    public SlashCommandTest(JDA jda) {
//        jda.upsertCommand(new CommandData("Test", "blah")).add(OptionType.fromKey());
        MessageChannel channel = null;
        Guild guild = null;
//        Message msg = com.boydti.discord.util.RateLimitUtil.complete(channel.sendMessage(new EmbedBuilder().build()));
//        msg.getActionRows().ad
    }

//    public void onSlashCommand(SlashCommandEvent event) {
//        if (event.getName().equals("hello")) {
//            event.reply("Click the button to say hello")
//                    .addActionRow(
//                            Button.primary("hello", "Click Me"), // Button with only a label
//                            Button.success("emoji", Emoji.fromMarkdown("<:minn:245267426227388416>"))) // Button with only an emoji
//                    .queue();
//        } else if (event.getName().equals("info")) {
//            event.reply("Click the buttons for more info")
//                    .addActionRow( // link buttons don't send events, they just open a link in the browser when clicked
//                            Button.link("https://github.com/DV8FromTheWorld/JDA", "GitHub")
//                                    .withEmoji(Emoji.fromMarkdown("<:github:849286315580719104>")), // Link Button with label and emoji
//                            Button.link("https://ci.dv8tion.net/job/JDA/javadoc/", "Javadocs")) // Link Button with only a label
//                    .queue();
//        }
//    }
}
