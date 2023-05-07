package link.locutus.discord.pnw;

import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CityRanges {
    private final List<Map.Entry<Integer, Integer>> ranges;

    public static CityRanges parse(String input) {
        List<Map.Entry<Integer, Integer>> cityRanges = new ArrayList<>();
        for (String rangeStr : input.split(",")) {
            Map.Entry<Integer, Integer> range = DiscordUtil.getCityRange(rangeStr);
            if (range == null) throw new IllegalArgumentException("Invalid range: `" + input + "`");
            cityRanges.add(range);
        }
        return new CityRanges(cityRanges);
    }

    public CityRanges(List<Map.Entry<Integer, Integer>> ranges) {
        this.ranges = ranges;
    }

    public boolean contains(int cityCount) {
        for (Map.Entry<Integer, Integer> range : ranges) {
            if (cityCount >= range.getKey() && cityCount <= range.getValue()) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        List<String> rangeStrings = new ArrayList<>();
        for (Map.Entry<Integer, Integer> range : ranges) {
            rangeStrings.add(DiscordUtil.cityRangeToString(range));
        }
        return StringMan.join(rangeStrings, ",");
    }

    public List<Map.Entry<Integer, Integer>> getRanges() {
        return ranges;
    }

    public boolean overlaps(CityRanges range) {
        for (Map.Entry<Integer, Integer> myRange : ranges) {
            for (Map.Entry<Integer, Integer> otherRange : range.ranges) {
                int thisX = myRange.getKey();
                int thisY = myRange.getValue();

                int otherX = otherRange.getKey();
                int otherY = otherRange.getValue();

                return thisX <= otherY && thisY >= otherX;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CityRanges that = (CityRanges) o;
        return Objects.equals(ranges, that.ranges);
    }

    @Override
    public int hashCode() {
        return ranges != null ? ranges.hashCode() : 0;
    }
}
