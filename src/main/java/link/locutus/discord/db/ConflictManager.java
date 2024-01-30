package link.locutus.discord.db;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.ThrowingConsumer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ConflictManager {
    private final WarDB db;
    private final Map<Integer, Conflict> conflictMap = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, String> legacyNames = new Int2ObjectOpenHashMap<>();
    private final Map<String, Integer> legacyIds = new ConcurrentHashMap<>();
    private final Set<Integer> activeConflicts = new IntOpenHashSet();
    private long lastTurn = 0;
    private final Map<Integer, Set<Integer>> activeConflictsByAllianceId = new Int2ObjectOpenHashMap<>();
    private final Map<Long, Map<Integer, int[]>> mapTurnAllianceConflictIds = new Long2ObjectOpenHashMap<>();

    private synchronized void initTurn() {
        long currTurn = TimeUtil.getTurn();
        if (lastTurn != currTurn) {
            Iterator<Integer> iter = activeConflicts.iterator();
            activeConflicts.removeIf(f -> {
                Conflict conflict = conflictMap.get(f);
                return (conflict == null || conflict.getEndTurn() <= currTurn);
            });
            recreateConflictsByAlliance();

            for (Conflict conflict : conflictMap.values()) {
                long startTurn = Math.max(lastTurn + 1, conflict.getStartTurn());
                long endTurn = Math.min(currTurn + 1, conflict.getEndTurn());
                if (startTurn >= endTurn) continue;
                Set<Integer> aaIds = conflict.getAllianceIds();
                for (long turn = startTurn; turn < endTurn; turn++) {
                    Map<Integer, int[]> conflictIdsByAA = mapTurnAllianceConflictIds.computeIfAbsent(turn, k -> new Int2ObjectOpenHashMap<>());
                    for (int aaId : aaIds) {
                        int[] currIds = conflictIdsByAA.get(aaId);
                        if (currIds == null) {
                            currIds = new int[]{conflict.getId()};
                        } else {
                            int[] newIds = new int[currIds.length + 1];
                            System.arraycopy(currIds, 0, newIds, 0, currIds.length);
                            newIds[currIds.length] = conflict.getId();
                            Arrays.sort(newIds);
                            currIds = newIds;
                        }
                        conflictIdsByAA.put(aaId, currIds);
                    }
                }
            }
            lastTurn = currTurn;
        }
    }

    private void applyConflicts(long turn, int allianceId1, int allianceId2, Consumer<Conflict> conflictConsumer) {
        if (allianceId1 == 0 && allianceId2 == 0) return;
        synchronized (mapTurnAllianceConflictIds) {
            Map<Integer, int[]> conflictIdsByAA = mapTurnAllianceConflictIds.get(turn);
            if (conflictIdsByAA == null) return;
            int[] conflictIds1 = allianceId1 != 0 ? conflictIdsByAA.get(allianceId1) : null;
            int[] conflictIds2 = allianceId2 != 0 && allianceId2 != allianceId1 ? conflictIdsByAA.get(allianceId2) : null;
            if (conflictIds2 != null && conflictIds1 != null) {
                int i = 0, j = 0;
                while (i < conflictIds1.length && j < conflictIds2.length) {
                    if (conflictIds1[i] < conflictIds2[j]) {
                        applyConflictConsumer(conflictIds1[i], conflictConsumer);
                        i++;
                    } else if (conflictIds1[i] > conflictIds2[j]) {
                        applyConflictConsumer(conflictIds2[j], conflictConsumer);
                        j++;
                    } else {
                        applyConflictConsumer(conflictIds1[i], conflictConsumer);
                        i++;
                        j++;
                    }
                }
                while (i < conflictIds1.length) {
                    applyConflictConsumer(conflictIds1[i], conflictConsumer);
                    i++;
                }
                while (j < conflictIds2.length) {
                    applyConflictConsumer(conflictIds2[j], conflictConsumer);
                    j++;
                }
            } else if (conflictIds1 != null) {
                for (int conflictId : conflictIds1) {
                    applyConflictConsumer(conflictId, conflictConsumer);
                }
            } else if (conflictIds2 != null) {
                for (int conflictId : conflictIds2) {
                    applyConflictConsumer(conflictId, conflictConsumer);
                }
            }
        }
    }

    private void applyConflictConsumer(int conflictId, Consumer<Conflict> conflictConsumer) {
        Conflict conflict = conflictMap.get(conflictId);
        if (conflict != null) {
            conflictConsumer.accept(conflict);
        }
    }

    public void updateWar(DBWar war, long turn) {
        if (turn > lastTurn) initTurn();
        applyConflicts(turn, war.getAttacker_aa(), war.getDefender_aa(), f -> f.updateWar(war, turn));
    }

    public void updateAttack(DBWar war, AbstractCursor attack) {
        long turn = TimeUtil.getTurn(attack.getDate());
        if (turn > lastTurn) initTurn();
        applyConflicts(turn, war.getAttacker_aa(), war.getDefender_aa(), f -> f.updateAttack(war, attack, turn));
    }

    private void recreateConflictsByAlliance() {
        synchronized (activeConflictsByAllianceId) {
            activeConflictsByAllianceId.clear();
            for (int id : activeConflicts) {
                addConflictsByAlliance(conflictMap.get(id));
            }
        }
    }

    private void addConflictsByAlliance(Conflict conflict) {
        if (conflict == null) return;
        synchronized (activeConflictsByAllianceId) {
            for (int aaId : conflict.getAllianceIds()) {
                activeConflictsByAllianceId.computeIfAbsent(aaId, k -> new IntArraySet()).add(conflict.getId());
            }
        }
    }

    public ConflictManager(WarDB db) {
        this.db = db;
    }

    protected void loadConflicts() {
        conflictMap.clear();
        db.query("SELECT * FROM conflicts", stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                long start = rs.getLong("start");
                long end = rs.getLong("end");
                conflictMap.put(id, new Conflict(id, name, start, end));
            }
        });
        db.query("SELECT * FROM conflict_participant", stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int conflictId = rs.getInt("conflict_id");
                int allianceId = rs.getInt("alliance_id");
                boolean side = rs.getBoolean("side");
                long start = rs.getLong("start");
                long end = rs.getLong("end");
                Conflict conflict = conflictMap.get(conflictId);
                if (conflict != null) {
                    conflict.addParticipant(allianceId, side, false, start, end);
                }
            }
        });
        // load legacyNames
        db.query("SELECT * FROM legacy_names", stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                legacyNames.put(id, name);
                legacyIds.putIfAbsent(name.toLowerCase(Locale.ROOT), id);
                System.out.println("Add legacy name " + name + "," + id);
            }
        });

        if (legacyNames.isEmpty()) {
            saveDefaultNames();
        }
        initTurn();
    }

    private void saveDefaultNames() {
        Map<String, Integer> legacyIds = new HashMap<>();
        legacyIds.put("Convent of Atom",7531);
        legacyIds.put("The Ampersand",5722);
        legacyIds.put("Brotherhood of the Clouds",7703);
        legacyIds.put("Bank Robbers",7923);
        legacyIds.put("The Outhouse",7990);
        legacyIds.put("Not Rohans Bank",8014);
        legacyIds.put("Democracy",8060);
        legacyIds.put("Prusso Roman Imperial Union",7920);
        legacyIds.put("Avalanche",8150);
        legacyIds.put("Sanctuary",8368);
        legacyIds.put("Union of Soviet Socialist Republics",8531);
        legacyIds.put("Ad Astra",7719);
        legacyIds.put("Otaku Shougaku",8594);
        legacyIds.put("Wizards",8624);
        legacyIds.put("MDC",8615);
        legacyIds.put("Christmas",8614);
        legacyIds.put("MySpacebarIsBroken",8678);
        legacyIds.put("Lords of Wumbology",8703);
        legacyIds.put("Paragon",8502);
        legacyIds.put("Shuba2M",8909);
        legacyIds.put("Shuba69M",8929);
        legacyIds.put("Mensa HQ",8930);
        legacyIds.put("Shuba99M",8955);
        legacyIds.put("Not A Scam",8984);
        legacyIds.put("The Dead Rabbits",7540);
        legacyIds.put("The Vatican",9321);
        legacyIds.put("High Temple",9341);
        legacyIds.put("Shuba666M",9385);
        legacyIds.put("Crimson Dragons",9406);
        legacyIds.put("Apollo",9427);
        legacyIds.put("Nibelheim",9580);
        legacyIds.put("Starfleet",9850);
        legacyIds.put("OTSN",9883);
        legacyIds.put("The Knights Of The Round Table",9830);
        legacyIds.put("Wayne Enterprises",9931);
        legacyIds.put("LegoLand",9961);
        legacyIds.put("Wayne Enterprises Inc",9971);
        legacyIds.put("Paradise",9986);
        legacyIds.put("The Afterlyfe",10060);
        legacyIds.put("Esquire Templar",10070);
        legacyIds.put("The Naughty Step",10074);
        legacyIds.put("The Cove",10104);
        legacyIds.put("Pacific Polar",10248);
        legacyIds.put("Stigma",10326);
        legacyIds.put("Sparkle Party People",10329);
        legacyIds.put("Age of Darkness",10100);
        legacyIds.put("Lunacy",9278);
        legacyIds.put("The Entente",10396);
        legacyIds.put("Crawling Crawfish Conundrum",10398);
        legacyIds.put("Western Republic",10408);
        legacyIds.put("General Patton",10411);
        legacyIds.put("Crab Creation Contraption",10416);
        legacyIds.put("The Bugs palace",10414);
        legacyIds.put("Aggravated Conch Assault",10425);
        legacyIds.put("Castle Wall",10436);
        legacyIds.put("Mukbang Lobster ASMR",10440);
        legacyIds.put("House of the Dragon",10445);
        legacyIds.put("lobster emoji",10447);
        legacyIds.put("LobsterGEDDON",10449);
        legacyIds.put("ARMENIA FOREVER",10452);
        legacyIds.put("Stigma 1",10450);
        legacyIds.put("General Custer",10454);
        legacyIds.put("Scyllarides Saloon",10464);
        legacyIds.put("Camelot Squires",10468);
        legacyIds.put("bruh momento",10467);
        legacyIds.put("Limp Lobster",10474);
        legacyIds.put("OSNAP",10472);
        legacyIds.put("AAAAAAAAA",10486);
        legacyIds.put("Alpha Lobster",10485);
        legacyIds.put("Shuba73M",10489);
        legacyIds.put("Borgs Assisted Loot Liberation Service",10504);
        legacyIds.put("Cornhub",10521);
        legacyIds.put("xXxJaredLetoFanxXx",10529);
        legacyIds.put("Anti-Horridism Obocchama Kun Fan Club",10540);
        legacyIds.put("Iraq Lobster",10552);
        legacyIds.put("Mole Rats",10574);
        legacyIds.put("God I Love Frogs",10573);
        legacyIds.put("A-HOK Fan Club Fan Club",10583);
        legacyIds.put("MyKeyboardIsBroken",10683);
        legacyIds.put("Show Me The Money",10694);
        legacyIds.put("Banana Stand London Branch",10709);
        legacyIds.put("Sparkle of the Night",10712);
        legacyIds.put("Cru Whole Hole",10716);
        legacyIds.put("Arrghs offshore",10717);
        legacyIds.put("Banana Stand New York",10720);
        legacyIds.put("Anything",10739);
        legacyIds.put("Banana Stand Los Angeles",10733);
        legacyIds.put("Wayne Foundation",10746);
        legacyIds.put("Banana Stand On The Run",10747);
        legacyIds.put("Master Basters",10759);
        legacyIds.put("Turkey land",10757);
        legacyIds.put("Theres No Place Like Home",10764);
        legacyIds.put("Drake - Hotline Bling",8520);
        legacyIds.put("Yer A Wizard Harry",10783);
        legacyIds.put("A Truth Universally Acknowledged",10805);
        legacyIds.put("An Offer He Cant Refuse",10815);
        legacyIds.put("HAHA England lost to France",10834);
        legacyIds.put("Banco dei Medici",8520);
        legacyIds.put("The Bank of Orbis",10092);
        legacyIds.put("May The Force Be With You",10839);
        legacyIds.put("Shaken Not Stirred",10845);
        legacyIds.put("Shaken Not Stired",10848);
        legacyIds.put("ET Phone Home",10854);
        legacyIds.put("Cock of destiny",10855);
        legacyIds.put("Yo Adrian",10868);
        legacyIds.put("Autocephalous Patriarchate of the Free",10869);
        legacyIds.put("Mama Always Said",10878);
        legacyIds.put("offshoreassss",10887);
        legacyIds.put("Youre Tacky and I Hate You",10905);
        legacyIds.put("O Captain My Captain",10912);
        legacyIds.put("HIDUDE GIB TIERING REPORT",10917);
        legacyIds.put("Bank of The Holy Grail",10925);
        legacyIds.put("Shuba45M",10933);
        legacyIds.put("The IX Legion",10934);
        legacyIds.put("Sparkle Forever",10946);
        legacyIds.put("BOSNIA MODE",10949);
        legacyIds.put("Jotunheimr",8429);
        legacyIds.put("Fallen Monarchy",10988);
        legacyIds.put("Gunga Ginga",11005);
        legacyIds.put("Grand Union of Nations",11018);
        legacyIds.put("Calamity",11019);
        legacyIds.put("borgborgborgborgborgborgborg",11023);
        legacyIds.put("Fargos",11027);
        legacyIds.put("Event Horizon",11039);
        legacyIds.put("CATA_IS_SO_COOL",11036);
        legacyIds.put("Pasta Factory",11042);
        legacyIds.put("MERDE",11059);
        legacyIds.put("The Black League",11066);
        legacyIds.put("United Nations Space Command",10995);
        legacyIds.put("Loopsnake alliance",11064);
        legacyIds.put("Old Praxis",11075);
        legacyIds.put("DecaDeezKnuttz",11077);
        legacyIds.put("Eurovision 2023 incoming",11090);
        legacyIds.put("Animal Pharm",11165);
        legacyIds.put("The Imperial Vault",11209);
        legacyIds.put("The House of Bugs",11288);
        legacyIds.put("Midnight Blues",11304);
        legacyIds.put("Mace & Chain",11312);
        legacyIds.put("Skull & Bones",11008);
        legacyIds.put("Swiss Account",11350);
        legacyIds.put("No offshore here",11353);
        legacyIds.put("Dunce Cap Supreme",11359);
        legacyIds.put("Aunt Jemima",11360);
        legacyIds.put("Fortuna sucks",11372);
        legacyIds.put("Home Hero",11375);
        legacyIds.put("Pharm Animal",11368);
        legacyIds.put("The Children of Yakub",11370);
        legacyIds.put("Shuba65M",11371);
        legacyIds.put("Tower of London",11376);
        legacyIds.put("Tintagel Castle",11384);
        legacyIds.put("Killer Tomatoes",11386);
        legacyIds.put("Nessa Barrett",11391);
        legacyIds.put("Legion of Dusk",11390);
        legacyIds.put("The Semimortals",11394);
        legacyIds.put("State of Orbis",11401);
        legacyIds.put("King Tiger",11398);
        legacyIds.put("Toilet Worshipping Lunatics",11403);
        legacyIds.put("The Peaceful Warmongers",11405);
        legacyIds.put("North Mexico",11407);
        legacyIds.put("Ketamine Therapy",11406);
        legacyIds.put("Kiwi Taxidermy",11420);
        legacyIds.put("Kazakhstani Tramway",11435);
        legacyIds.put("Prenadores de Burras Profesionales",11441);
        legacyIds.put("Kidney Transplant",11444);
        legacyIds.put("Shuba63m",11450);
        legacyIds.put("Knockoff Tetragrammatons",11457);
        legacyIds.put("Castillo de Coca",11454);
        legacyIds.put("Kangaroo Testicles",11473);
        legacyIds.put("New Church Republic",11494);
        legacyIds.put("Kleptomaniac Tunisians",11493);
        legacyIds.put("Shuba777M",11510);
        legacyIds.put("Kitten Toes",11514);
        legacyIds.put("Atlas Three",11515);
        legacyIds.put("Kaleidoscope Technology",11525);
        legacyIds.put("Koala Tornado",11531);
        legacyIds.put("Skylines",11533);
        legacyIds.put("Palo Mayombe",11604);
        legacyIds.put("Mouseleys Superfan Fun Cheese Corner 5",11619);
        legacyIds.put("General Area of Two Ostritches",11643);
        legacyIds.put("The Persian Empire",10671);
        legacyIds.put("Bakerstreet",11699);
        legacyIds.put("The Radiant Syndication",11719);
        legacyIds.put("Make More Monitors",11714);
        legacyIds.put("Quack",11718);
        legacyIds.put("Panama City Beach",11710);
        legacyIds.put("Shadowhunters",11715);
        legacyIds.put("Storm",11721);
        legacyIds.put("Three Inch Surprise",11730);
        legacyIds.put("Greywater Watch",11731);
        legacyIds.put("Port St Lucie",11740);
        legacyIds.put("The Orphanage",11746);
        legacyIds.put("Halo Revived",11751);
        legacyIds.put("Saint Augustine",11753);
        legacyIds.put("Orange Brotherhood",11764);
        legacyIds.put("Demon Slayer",11765);
        legacyIds.put("The Hippo Horde",11763);
        legacyIds.put("Yeehaw Junction",11769);
        legacyIds.put("House Weeb",11772);
        legacyIds.put("Ockey Multi Mass Production Facility 7",11779);
        legacyIds.put("TCM Extension",11797);
        legacyIds.put("Bohemian Grove",11811);
        legacyIds.put("House Stark Crypto Wallet",11805);
        legacyIds.put("Two Egg",11817);
        legacyIds.put("Elfers",11830);
        legacyIds.put("Gamblers Anonymous",11862);
        legacyIds.put("Humza Useless",11876);
        legacyIds.put("The Merry Men",11900);
        legacyIds.put("Jacobite Rebellion",11899);
        legacyIds.put("The Media",11912);
        legacyIds.put("World of Farce",11952);
        legacyIds.put("Lyra",12022);
        legacyIds.put("Free Alrea",12029);
        legacyIds.put("Black Banana",12031);
        legacyIds.put("Cassiopeia",12034);
        legacyIds.put("Basil Land",12036);
        legacyIds.put("Better eclipse",12037);
        legacyIds.put("Planet express",12043);
        legacyIds.put("Seven WHO",12047);
        legacyIds.put("Aquila",12057);
        legacyIds.put("Rum Raiders",12062);
        legacyIds.put("Free Alrea 3",12067);
        legacyIds.put("Zapp spammigan",12068);
        legacyIds.put("Eridanus",12064);
        legacyIds.put("Neighborhood watch alliance",12066);
        legacyIds.put("House Apathy",12069);
        legacyIds.put("Free Alrea 4",12076);
        legacyIds.put("Taurus",12084);
        legacyIds.put("Vela",12090);
        legacyIds.put("Chavez Nuestro que Estas en los Cielos",12102);
        legacyIds.put("Cygnus",12134);
        legacyIds.put("Thin Skin Singularity",12190);
        legacyIds.put("Red Wine on THT",12261);
        legacyIds.put("Cute Cats Cuddling in a Cayak",12290);
        legacyIds.put("Narutos",12318);
        legacyIds.put("insane",12344);
        legacyIds.put("enasni",12362);
        legacyIds.put("Tax Scheme",12364);
        legacyIds.put("aneins",12369);
        legacyIds.put("aneane",12380);
        legacyIds.put("insane transposed",12421);
        legacyIds.put("anti insane",12429);
        legacyIds.put("Biker Haven", 11389);
        legacyIds.put("Hegemoney", 11709);

        for (Map.Entry<String, Integer> entry : legacyIds.entrySet()) {
            addLegacyName(entry.getValue(), entry.getKey());
        }

    }

    public void createTables() {
        db.executeStmt("CREATE TABLE IF NOT EXISTS conflicts (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR NOT NULL, start BIGINT NOT NULL, end BIGINT NOT NULL)");
        db.executeStmt("CREATE TABLE IF NOT EXISTS conflict_participant (conflict_id INTEGER NOT NULL, alliance_id INTEGER NOT NULL, side BOOLEAN, start BIGINT NOT NULL, end BIGINT NOT NULL, PRIMARY KEY (conflict_id, alliance_id), FOREIGN KEY(conflict_id) REFERENCES conflicts(id))");
        db.executeStmt("CREATE TABLE IF NOT EXISTS legacy_names (id INTEGER PRIMARY KEY, name VARCHAR NOT NULL)");
//        db.executeStmt("DELETE FROM conflict_participant");
//        db.executeStmt("DELETE FROM conflicts");
        loadConflicts();
    }

    public void addLegacyName(int id, String name) {
        if (legacyNames.containsKey(id)) return;
        legacyNames.put(id, name);
        legacyIds.putIfAbsent(name.toLowerCase(Locale.ROOT), id);
        db.update("INSERT OR IGNORE INTO legacy_names (id, name) VALUES (?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, id);
            stmt.setString(2, name);
        });
    }

    public Conflict addConflict(String name, long turnStart, long turnEnd) {
        String query = "INSERT INTO conflicts (name, start, end) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setLong(2, turnStart);
            stmt.setLong(3, turnEnd);
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int id = rs.getInt(1);
                Conflict conflict = new Conflict(id, name, turnStart, turnEnd);
                conflictMap.put(id, conflict);

                synchronized (activeConflicts) {
                    long turn = TimeUtil.getTurn();
                    if (turnEnd > turn) {
                        activeConflicts.add(id);
                        addConflictsByAlliance(conflict);
                    }
                }

                return conflict;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected void updateConflict(int conflictId, long start, long end) {
        synchronized (activeConflicts) {
            if (activeConflicts.contains(conflictId)) {
                if (end <= TimeUtil.getTurn()) {
                    activeConflicts.remove(conflictId);
                    recreateConflictsByAlliance();
                }
            }
        }
        db.update("UPDATE conflicts SET start = ?, end = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, start);
            stmt.setLong(2, end);
            stmt.setInt(3, conflictId);
        });
    }

    protected void addParticipant(int allianceId, int conflictId, boolean side, long start, long end) {
        db.update("INSERT OR REPLACE INTO conflict_participant (conflict_id, alliance_id, side, start, end) VALUES (?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflictId);
            stmt.setInt(2, allianceId);
            stmt.setBoolean(3, side);
            stmt.setLong(4, start);
            stmt.setLong(5, end);
        });
        DBAlliance aa = DBAlliance.get(allianceId);
        if (aa != null) addLegacyName(allianceId, aa.getName());
        synchronized (activeConflicts) {
            if (activeConflicts.contains(conflictId)) {
                addConflictsByAlliance(conflictMap.get(conflictId));
            }
        }
    }

    protected void removeParticipant(int allianceId, int conflictId) {
        db.update("DELETE FROM conflict_participant WHERE alliance_id = ? AND conflict_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, allianceId);
            stmt.setInt(2, conflictId);
        });
        if (activeConflicts.contains(conflictId)) {
            recreateConflictsByAlliance();
        }
    }

    public Map<Integer, Conflict> getConflictMap() {
        synchronized (conflictMap) {
            return new Int2ObjectOpenHashMap<>(conflictMap);
        }
    }

    public List<Conflict> getActiveConflicts() {
        return conflictMap.values().stream().filter(conflict -> conflict.getEndTurn() == Long.MAX_VALUE).toList();
    }

    public Conflict getConflict(String conflictName) {
        for (Conflict conflict : getConflictMap().values()) {
            if (conflict.getName().equalsIgnoreCase(conflictName)) {
                return conflict;
            }
        }
        return null;
    }

    public Integer getLegacyId(String name) {
        return legacyIds.get(name.toLowerCase(Locale.ROOT));
    }

    public String getAllianceName(int id) {
        DBAlliance alliance = DBAlliance.get(id);
        if (alliance != null) return alliance.getName();
        String name;
        synchronized (legacyNames) {
            name = legacyNames.get(id);
        }
        if (name != null) return name;
        return "AA:" + id;
    }

    public void deleteConflict(Conflict conflict) {
        synchronized (activeConflicts) {
            if (activeConflicts.contains(conflict.getId())) {
                activeConflicts.remove(conflict.getId());
                recreateConflictsByAlliance();
            }
        }
        synchronized (conflictMap) {
            conflictMap.remove(conflict.getId());
        }
        db.update("DELETE FROM conflicts WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflict.getId());
        });
        db.update("DELETE FROM conflict_participant WHERE conflict_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflict.getId());
        });
    }

    public Conflict getConflictById(int id) {
        synchronized (conflictMap) {
            return conflictMap.get(id);
        }
    }

    public Set<String> getConflictNames() {
        Set<String> names = new ObjectOpenHashSet<>();
        synchronized (conflictMap) {
            for (Conflict conflict : conflictMap.values()) {
                names.add(conflict.getName());
            }
        }
        return names;
    }
}
