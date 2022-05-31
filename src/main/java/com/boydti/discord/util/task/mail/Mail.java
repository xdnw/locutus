package com.boydti.discord.util.task.mail;

public class Mail {
    public final int id;
    public final String subject;
    public final String leader;
    public final int nationId;
    public final String content;

    public Mail(int id, String subject, String leader, int nationId, String content) {
        this.id = id;
        this.subject = subject;
        this.leader = leader;
        this.nationId = nationId;
        this.content = content;
    }
}