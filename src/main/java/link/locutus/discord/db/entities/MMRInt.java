package link.locutus.discord.db.entities;

import java.util.Arrays;

public class MMRInt extends MMRDouble{

    public MMRInt(int[] mmr) {
        super(Arrays.stream(mmr).asDoubleStream().toArray());
    }

    public static MMRInt fromString(String input) {
        int[] mmr = new int[4];
        if (input.length() == 4) {
            mmr[0] = input.charAt(0) - '0';
            mmr[1] = input.charAt(1) - '0';
            mmr[2] = input.charAt(2) - '0';
            mmr[3] = input.charAt(3) - '0';
        } else if (input.contains("/") || input.contains(",")) {
            String[] split = input.split("[/,]");
            mmr[0] = Integer.parseInt(split[0]);
            mmr[1] = Integer.parseInt(split[1]);
            mmr[2] = Integer.parseInt(split[2]);
            mmr[3] = Integer.parseInt(split[3]);
        } else {
            throw new IllegalArgumentException("MMR must be 4 numbers. Provided value: `" + input + "`");
        }
        return new MMRInt(mmr);
    }
}
