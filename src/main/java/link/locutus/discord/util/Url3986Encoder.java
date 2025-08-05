package link.locutus.discord.util;

import java.math.BigInteger;
import java.util.Arrays;

public class Url3986Encoder {
    private static final String CHARSET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    + "abcdefghijklmnopqrstuvwxyz"
                    + "0123456789-._~:/?#[]@!$&'()*+,;=";
    private static final BigInteger RADIX = BigInteger.valueOf(CHARSET.length());

    /** Encode arbitrary bytes into a Base86 string using the RFC 3986 set. */
    public static String encode(byte[] data) {
        BigInteger num = new BigInteger(1, data);
        StringBuilder sb = new StringBuilder();
        while (num.signum() > 0) {
            BigInteger[] divRem = num.divideAndRemainder(RADIX);
            num = divRem[0];
            sb.append(CHARSET.charAt(divRem[1].intValue()));
        }
        if (sb.length() == 0) {
            sb.append(CHARSET.charAt(0));
        }
        return sb.reverse().toString();
    }

    /** Decode a Base86 string back into the original byte array. */
    public static byte[] decode(String text) {
        BigInteger num = BigInteger.ZERO;
        for (char c : text.toCharArray()) {
            int idx = CHARSET.indexOf(c);
            if (idx < 0) {
                throw new IllegalArgumentException("Invalid character: " + c);
            }
            num = num.multiply(RADIX).add(BigInteger.valueOf(idx));
        }
        byte[] bytes = num.toByteArray();
        // strip leading zero if present
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }
}
