package link.locutus.discord.util.offshore.test;

import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.commands.manager.v2.builder.SummedMapRankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.scheduler.KeyValue;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * testing keno inputs / outputs
 * result:
 *  - Keno is random
 *  - more than 10 numbers are ignored
 *  - numbers outside the range can be entered (1X payout rate, so no issues)
 */
public class Keno {
    private final Auth auth;

    public Keno(Auth auth) {
        this.auth = auth;
    }

    /**
     *
     * @param picks
     * @param amount
     * @return (list of numbers, message)
     */
    public Map.Entry<Set<Integer>, String> bet(List<Integer> picks, int amount, Consumer<String> response) {
        String picksStr = StringMan.join(picks, ",");

        Map<String, String> post = new HashMap<String, String>();
        post.put("betamount", amount + "");

        post.put("picks", picksStr);
        post.put("g-recaptcha-response", "");
        post.put("action", "validate_captcha");
        String url = Settings.PNW_URL() + "/casino/keno/#play";

        return PW.withLogin(new Callable<Map.Entry<Set<Integer>, String>>() {
            @Override
            public Map.Entry<Set<Integer>, String> call() throws Exception {
                int same = 0;
                int notSame = 0;
                String result = auth.readStringFromURL(PagePriority.KENO, url, post);
                Document dom = Jsoup.parse(result);
                StringBuilder response = new StringBuilder();

                if (!result.contains("numbers_drawn")) {
                    throw new IllegalArgumentException("No number_drawn");
                }
                String drawn1 = result.substring(result.indexOf("numbers_drawn = "));
                String drawn2 = result.substring(result.indexOf("numbers_drawn2 = "));
                String[] hit = drawn1.split(" = ")[1].split(";")[0].replace("[", "").replace("]", "").split(",");
                String[] miss = drawn2.split(" = ")[1].split(";")[0].replace("[", "").replace("]", "").split(",");
                Set<Integer> picks = new IntOpenHashSet();
                for (String num : hit) {
                    if (num.isEmpty()) continue;
                    int id = Integer.parseInt(num);
                    picks.add(id);
                }
                for (String num : miss) {
                    if (num.isEmpty()) continue;
                    int id = Integer.parseInt(num);
                    picks.add(id);
                }

                for (Element element : dom.getElementsByClass("alert")) {
                    String text = element.text();
                    if (text.startsWith("Player Advertisement by ")) {
                        continue;
                    }
                    response.append('\n').append(element.text());
                }

                return KeyValue.of(picks, response.toString());
            }
        }, auth);
    }
    public void add(List<Set<Integer>> options, Set<Set<Integer>> visited, int index, Set<Integer> base, Consumer<Set<Integer>> withResult) {
        for (int i = index; i < options.size(); i++) {
            Set<Integer> option = options.get(i);
            if (!base.isEmpty()) {
                int contains = 0;
                for (Integer integer : option) {
                    if (base.contains(integer)) contains++;
                }
                if (contains <= 2 || contains == option.size()) continue;
                int numToAdd = option.size() - contains;
                if (base.size() + numToAdd > 10) continue;
            }
            Set<Integer> newSet = new IntLinkedOpenHashSet(base);
            newSet.addAll(option);
            if (!visited.add(newSet)) continue;
            if (newSet.size() > 10) continue;
            else if (newSet.size() == 10) {
                withResult.accept(newSet);
                continue;
            } else {
                add(options, visited, i + 1, newSet, withResult);
            }
        }
    }

    public List<Set<Integer>> getPicks(String input) {
        List<Set<Integer>> picks = new ArrayList<>();
        for (String line : input.split("\n")) {
            if (!line.startsWith("Picks ")) continue;
            List<Integer> split = Arrays.asList(line.split("\\(")[1].split("\\)")[0].trim().split(",")).stream().map(Integer::parseInt).collect(Collectors.toList());
            picks.add(new HashSet<>(split));
        }
        return picks;
    }

    public Map<Integer, Integer> print(Map<Integer, Integer> totals) {
        for (int i = 1; i <= 80; i++) {
            System.out.print(totals.get(i) + "\t");
            if (i % 10 == 0) System.out.println();
        }
        return totals;
    }

    public Map<Integer, Integer> getTotals(List<Set<Integer>> sets) {
        Map<Integer, Integer> totals = new HashMap<>();
        for (Set<Integer> set : sets) {
            for (Integer integer : set) {
                totals.put(integer, totals.getOrDefault(integer, 0) + 1);
            }
        }
        return new SummedMapRankBuilder<>(totals).sort().get();
    }

    public int count(List<Set<Integer>> sets, int... numbers) {
        int count = 0;
        outer:
        for (Set<Integer> pick : sets) {
            for (int i : numbers) {
                if (!pick.contains(i)) continue outer;
            }
            count++;
        }
        return count;
    }

    public int getMatches(List<Set<Integer>> picks, Set<Integer> chosen) {
        int total = 0;
        for (Set<Integer> pick : picks) {
            int count = 0;
            for (Integer integer : chosen) {
                if (pick.contains(integer)) count++;
            }
            if (count > 3) {
                total += count;
            } else if (count == 1 || count == 2 || count == 0) {
                total -= 2;
            } else if (count == 3) {
                total -= 1;
            }
        }
        return total;
    }

    public double getExpectedValue(List<Set<Integer>> picks, Set<Integer> chosen) {
        double value = 0;
        int contains = 0;
        for (Set<Integer> pick : picks) {
            value -= 1000;

            int count = 0;
            for (Integer integer : chosen) {
                if (pick.contains(integer)) count++;
            }
            contains += count;
            switch (count) {
                case 0:
                    value += 1000;
                    break;
                case 1:
                case 2:
                    break;
                case 3:
                    value += 500;
                    break;
                case 4:
                    value += 2000;
                    break;
                case 5:
                    value += 5000;
                    break;
                case 6:
                    value += 10000;
                    break;
                case 7:
                    value += 25000;
                    break;
                case 8:
                    value += 500000;
                    break;
                case 9:
                    value += 2000000;
                    break;
                case 10:
                default:
                    value += 10000000;
            }
        }
        return value;
    }

    public Map<Integer, Integer> getPairs(List<Set<Integer>> picks) {
        Map<Integer, Integer> pairs = new HashMap<>();
        for (Set<Integer> pick : picks) {
            ArrayList<Integer> pickList = new ArrayList<>(pick);
            for (int i = 0; i < pickList.size(); i++) {
                for (int j = i + 1; j < pickList.size(); j++) {
                    int a = pickList.get(i);
                    int b = pickList.get(j);
                    if (a > b) {
                        int tmp = b;
                        b = a;
                        a = tmp;
                    }
                    int pair = MathMan.pair((short) a, (short) b);
                    pairs.put(pair, pairs.getOrDefault(pair, 0) + 1);
                }
            }
        }
        return pairs;
    }
}
