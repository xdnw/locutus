package com.boydti.discord.event;

import com.boydti.discord.Locutus;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.Alliance;
import com.boydti.discord.pnw.DBNation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

public class NationChangePositionEvent extends NationChangeEvent {
    public NationChangePositionEvent(DBNation previous, DBNation current) {
        super(previous, current);
    }
}
