package com.boydti.discord.event;

import com.boydti.discord.Locutus;

public class Event {
    public void post() {
        Locutus.post(this);
    }
}
