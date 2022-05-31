package com.boydti.discord.web.commands.search;

public class SearchResult {
    public final String title;
    public final String body;
    public final String url;
    public final double match;
    public final SearchType type;

    public SearchResult(String title, String body, String url, double match, SearchType type) {
        this.title = title;
        this.body = body;
        this.url = url;
        this.match = match;
        this.type = type;
    }
}
