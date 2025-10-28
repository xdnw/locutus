package link.locutus.discord.apiv1.entities;

import java.math.BigInteger;
import java.util.Arrays;

public record PwUid(byte[] data) {

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PwUid)) return false;
        PwUid other = (PwUid) obj;
        return Arrays.equals(this.data, other.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    public static PwUid fromHexString(String hex) {
        if (hex == null) {
            throw new NullPointerException("hex string is null");
        }
        int len = hex.length();
        int pos = 0;
        boolean negative = false;
        if (len > 0) {
            char c0 = hex.charAt(0);
            if (c0 == '+' || c0 == '-') {
                negative = c0 == '-';
                pos++;
            }
        }
        if (pos >= len) {
            throw new NumberFormatException("Zero length");
        }

        int hexDigits = len - pos;
        int byteLen = (hexDigits + 1) / 2;
        byte[] mag = new byte[byteLen];
        int bi = 0;

        if ((hexDigits & 1) == 1) {
            mag[bi++] = (byte) hexVal(hex.charAt(pos++));
        }
        while (pos < len) {
            int hi = hexVal(hex.charAt(pos++));
            int lo = hexVal(hex.charAt(pos++));
            mag[bi++] = (byte) ((hi << 4) | lo);
        }

        // Trim leading zero bytes
        int firstNonZero = 0;
        while (firstNonZero < mag.length && mag[firstNonZero] == 0) {
            firstNonZero++;
        }
        if (firstNonZero == mag.length) {
            return new PwUid(new byte[] { 0 });
        }
        if (firstNonZero > 0) {
            int newLen = mag.length - firstNonZero;
            byte[] tmp = new byte[newLen];
            System.arraycopy(mag, firstNonZero, tmp, 0, newLen);
            mag = tmp;
        }

        if (!negative) {
            // If MSB would be interpreted as negative, prefix a 0x00
            if ((mag[0] & 0x80) != 0) {
                byte[] out = new byte[mag.length + 1];
                out[0] = 0;
                System.arraycopy(mag, 0, out, 1, mag.length);
                mag = out;
            }
            return new PwUid(mag);
        } else {
            // Two's complement of the magnitude
            byte[] res = new byte[mag.length];
            int carry = 1;
            for (int i = mag.length - 1; i >= 0; i--) {
                int b = (~mag[i]) & 0xFF;
                int sum = b + carry;
                res[i] = (byte) sum;
                carry = (sum >>> 8) & 1; // 0 or 1
            }

            // Ensure negative sign (MSB must be 1). If not, prepend 0xFF.
            if ((res[0] & 0x80) == 0) {
                byte[] tmp = new byte[res.length + 1];
                tmp[0] = (byte) 0xFF;
                System.arraycopy(res, 0, tmp, 1, res.length);
                res = tmp;
            }

            // Trim redundant leading 0xFF sign-extension bytes
            int start = 0;
            while (res.length - start > 1
                    && res[start] == (byte) 0xFF
                    && (res[start + 1] & 0x80) != 0) {
                start++;
            }
            if (start != 0) {
                byte[] tmp = new byte[res.length - start];
                System.arraycopy(res, start, tmp, 0, tmp.length);
                res = tmp;
            }
            return new PwUid(res);
        }
    }

    private static int hexVal(char c) {
        int v = Character.digit(c, 16);
        if (v == -1) {
            throw new NumberFormatException("Invalid hex character: " + c);
        }
        return v;
    }

    public String toHexString() {
        return new BigInteger(this.data()).toString(16);
    }
}
