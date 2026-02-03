package link.locutus.discord.util.math;

import java.awt.Color;
import java.awt.color.ColorSpace;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class CIEDE2000 extends ColorSpace {
    public static final CIEDE2000 INSTANCE = new CIEDE2000();

    public static Color findContrast(Color... colors) {
        if (colors == null || colors.length == 0) return Color.BLACK;

        double[] L = new double[colors.length];
        for (int i = 0; i < colors.length; i++) {
            L[i] = relativeLuminance(colors[i]);
        }

        // Candidate target luminances: 0 (black), 1 (white), and pairwise equal-contrast points
        List<Double> candidates = new ArrayList<>();
        candidates.add(0.0);
        candidates.add(1.0);
        for (int i = 0; i < L.length; i++) {
            for (int j = i + 1; j < L.length; j++) {
                double a = Math.min(L[i], L[j]);
                double b = Math.max(L[i], L[j]);
                double y = Math.sqrt((a + 0.05) * (b + 0.05)) - 0.05; // equalizes contrast vs a and b
                if (y >= 0.0 && y <= 1.0) {
                    candidates.add(y);
                }
            }
        }

        double bestY = 0.0;
        double bestMinCR = -1.0;

        for (double y : candidates) {
            double minCR = Double.POSITIVE_INFINITY;
            for (double Li : L) {
                double hi = Math.max(y, Li);
                double lo = Math.min(y, Li);
                double cr = (hi + 0.05) / (lo + 0.05);
                if (cr < minCR) minCR = cr;
            }
            if (minCR > bestMinCR) {
                bestMinCR = minCR;
                bestY = y;
            }
        }

        return grayForLuminance(bestY);
    }

    // WCAG relative luminance (sRGB)
    private static double relativeLuminance(Color c) {
        double r = srgb8ToLinear(c.getRed());
        double g = srgb8ToLinear(c.getGreen());
        double b = srgb8ToLinear(c.getBlue());
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double srgb8ToLinear(int v8) {
        double v = v8 / 255.0;
        if (v <= 0.04045) return v / 12.92;
        return Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static int linearToSrgb8(double lin) {
        lin = Math.max(0.0, Math.min(1.0, lin));
        double v = (lin <= 0.0031308) ? (12.92 * lin) : (1.055 * Math.pow(lin, 1.0 / 2.4) - 0.055);
        int i = (int)Math.round(v * 255.0);
        return Math.max(0, Math.min(255, i));
    }

    // Returns a neutral gray whose luminance equals the target (optimal for contrast)
    private static Color grayForLuminance(double Y) {
        int c = linearToSrgb8(Y);
        return new Color(c, c, c);
    }

    public static Color randomColor(long seed, Color BG, Collection<Color> existingColors) {
        Random random = new Random(seed);

        Color color = null;
        double maxDiff = 0;
        for (int i = 0; i < 100; i++) {
            int nextInt = random.nextInt(0xffffff + 1);
            String colorCode = String.format("#%06x", nextInt);
            Color nextColor = Color.decode(colorCode);

            if (calculateDeltaE(BG, nextColor) < 12) continue;

            double minDiff = Double.MAX_VALUE;
            for (Color otherColor : existingColors) {
                minDiff = Math.min(minDiff, CIEDE2000.calculateDeltaE(nextColor, otherColor));
            }
            if (minDiff > maxDiff) {
                maxDiff = minDiff;
                color = nextColor;
            }
            if (minDiff > 12) break;
        }
        return color;
    }

    public static Color findComplement(Color color) {
        float[] hsv = new float[3];
        int c = color.getRGB();
        Color.RGBtoHSB(c>>16&0xFF, c>>8&0xFF, c&0xFF, hsv);
        hsv[0] = (float) ((hsv[0] + 0.5) % 1.0);

        // black or white? return opposite
        if (hsv[2] == 0) return new Color(255);
        else if (hsv[2] == 1.0) return new Color(0);

        // low value? otherwise, adjust that too
        if (hsv[2] < 0.5) {
            hsv[2] = (float) ((hsv[2] + 0.5) % 1.0);
        }

        return new Color(Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]));
    }

    public static double calculateDeltaE(Color a, Color b) {
        float[] lab1 = INSTANCE.fromRGB(a.getRGBColorComponents(null));
        float[] lab2 = INSTANCE.fromRGB(b.getRGBColorComponents(null));
        return calculateDeltaE(lab1, lab2);
    }

    /**
     * Calculate the colour difference value between two colours in lab space.
     * @param lab1 first colour
     * @param lab2 second colour
     * @return the CIE 2000 colour difference
     */
    public static float calculateDeltaE(float [] lab1, float[] lab2) {
        return (float) calculateDeltaE(lab1[0],lab1[1],lab1[2],lab2[0],lab2[1],lab2[2]);
    }

    /**
     * Calculate the colour difference value between two colours in lab space.
     * @param L1 first colour's L component
     * @param a1 first colour's a component
     * @param b1 first colour's b component
     * @param L2 second colour's L component
     * @param a2 second colour's a component
     * @param b2 second colour's b component
     * @return the CIE 2000 colour difference
     */
    public static double calculateDeltaE(double L1, double a1, double b1, double L2, double a2, double b2) {
        double Lmean = (L1 + L2) / 2.0; //ok
        double C1 =  Math.sqrt(a1*a1 + b1*b1); //ok
        double C2 =  Math.sqrt(a2*a2 + b2*b2); //ok
        double Cmean = (C1 + C2) / 2.0; //ok

        double G =  ( 1 - Math.sqrt( Math.pow(Cmean, 7) / (Math.pow(Cmean, 7) + Math.pow(25, 7)) ) ) / 2; //ok
        double a1prime = a1 * (1 + G); //ok
        double a2prime = a2 * (1 + G); //ok

        double C1prime =  Math.sqrt(a1prime*a1prime + b1*b1); //ok
        double C2prime =  Math.sqrt(a2prime*a2prime + b2*b2); //ok
        double Cmeanprime = (C1prime + C2prime) / 2; //ok

        double h1prime =  Math.atan2(b1, a1prime) + 2*Math.PI * (Math.atan2(b1, a1prime)<0 ? 1 : 0);
        double h2prime =  Math.atan2(b2, a2prime) + 2*Math.PI * (Math.atan2(b2, a2prime)<0 ? 1 : 0);
        double Hmeanprime =  ((Math.abs(h1prime - h2prime) > Math.PI) ? (h1prime + h2prime + 2*Math.PI) / 2 : (h1prime + h2prime) / 2); //ok

        double T =  1.0 - 0.17 * Math.cos(Hmeanprime - Math.PI/6.0) + 0.24 * Math.cos(2*Hmeanprime) + 0.32 * Math.cos(3*Hmeanprime + Math.PI/30) - 0.2 * Math.cos(4*Hmeanprime - 21*Math.PI/60); //ok

        double deltahprime =  ((Math.abs(h1prime - h2prime) <= Math.PI) ? h2prime - h1prime : (h2prime <= h1prime) ? h2prime - h1prime + 2*Math.PI : h2prime - h1prime - 2*Math.PI); //ok

        double deltaLprime = L2 - L1; //ok
        double deltaCprime = C2prime - C1prime; //ok
        double deltaHprime =  2.0 * Math.sqrt(C1prime*C2prime) * Math.sin(deltahprime / 2.0); //ok
        double SL =  1.0 + ( (0.015*(Lmean - 50)*(Lmean - 50)) / (Math.sqrt( 20 + (Lmean - 50)*(Lmean - 50) )) ); //ok
        double SC =  1.0 + 0.045 * Cmeanprime; //ok
        double SH =  1.0 + 0.015 * Cmeanprime * T; //ok

        double deltaTheta =  (30 * Math.PI / 180) * Math.exp(-((180/Math.PI*Hmeanprime-275)/25)*((180/Math.PI*Hmeanprime-275)/25));
        double RC =  (2 * Math.sqrt(Math.pow(Cmeanprime, 7) / (Math.pow(Cmeanprime, 7) + Math.pow(25, 7))));
        double RT =  (-RC * Math.sin(2 * deltaTheta));

        double KL = 1;
        double KC = 1;
        double KH = 1;

        double deltaE = Math.sqrt(
                ((deltaLprime/(KL*SL)) * (deltaLprime/(KL*SL))) +
                        ((deltaCprime/(KC*SC)) * (deltaCprime/(KC*SC))) +
                        ((deltaHprime/(KH*SH)) * (deltaHprime/(KH*SH))) +
                        (RT * (deltaCprime/(KC*SC)) * (deltaHprime/(KH*SH)))
        );

        return deltaE;
    }

    @Override
    public float[] fromCIEXYZ(float[] colorvalue) {
        double l = f(colorvalue[1]);
        double L = 116.0 * l - 16.0;
        double a = 500.0 * (f(colorvalue[0]) - l);
        double b = 200.0 * (l - f(colorvalue[2]));
        return new float[] {(float) L, (float) a, (float) b};
    }

    @Override
    public float[] fromRGB(float[] rgbvalue) {
        float[] xyz = CIEXYZ.fromRGB(rgbvalue);
        return fromCIEXYZ(xyz);
    }

    @Override
    public float getMaxValue(int component) {
        return 128f;
    }

    @Override
    public float getMinValue(int component) {
        return (component == 0)? 0f: -128f;
    }

    @Override
    public String getName(int idx) {
        return String.valueOf("Lab".charAt(idx));
    }

    @Override
    public float[] toCIEXYZ(float[] colorvalue) {
        double i = (colorvalue[0] + 16.0) * (1.0 / 116.0);
        double X = fInv(i + colorvalue[1] * (1.0 / 500.0));
        double Y = fInv(i);
        double Z = fInv(i - colorvalue[2] * (1.0 / 200.0));
        return new float[] {(float) X, (float) Y, (float) Z};
    }

    @Override
    public float[] toRGB(float[] colorvalue) {
        float[] xyz = toCIEXYZ(colorvalue);
        return CIEXYZ.toRGB(xyz);
    }

    CIEDE2000() {
        super(ColorSpace.TYPE_Lab, 3);
    }

    private static double f(double x) {
        if (x > 216.0 / 24389.0) {
            return Math.cbrt(x);
        } else {
            return (841.0 / 108.0) * x + N;
        }
    }

    private static double fInv(double x) {
        if (x > 6.0 / 29.0) {
            return x*x*x;
        } else {
            return (108.0 / 841.0) * (x - N);
        }
    }

    private static final ColorSpace CIEXYZ = ColorSpace.getInstance(ColorSpace.CS_CIEXYZ);

    private static final double N = 4.0 / 29.0;
}
