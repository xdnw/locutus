package link.locutus.discord.gpt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class Chunker {
    // Tunable knobs:
    private static final double INITIAL_CHARS_PER_TOKEN = 4.0;
    private static final double SAFETY_UNDER = 0.90;
    private static final double SCALE_SAFETY = 0.98;
    private static final double EWMA_ALPHA = 0.25;
    private static final double MIN_CHARS_PER_TOKEN = 0.5;
    private static final double MAX_CHARS_PER_TOKEN = 20.0;
    private static final double MAX_GROW_FACTOR = 1.60;
    private static final double MIN_SHRINK_FACTOR = 0.50;
    private static final int MAX_REFINEMENT_STEPS = 4;

    // Convenience: one-off next chunk. Optimized for the common case where the whole text fits (1 API call).
    public static String getNextChunk(String text, int tokenSizeCap, Function<String, Integer> getSize) {
        if (text == null || text.isEmpty()) return "";
        if (tokenSizeCap <= 0) throw new IllegalArgumentException("tokenSizeCap must be > 0");

        // If it all fits, return as-is with a single count call.
        int full = getSize.apply(text);
        if (full <= tokenSizeCap) return text;

        // Otherwise use a short-lived session (no global state).
        Session session = new Session(tokenSizeCap, getSize);
        int end = session.nextChunkEnd(text, 0, false); // skip full-fit check (we already did it)
        return text.substring(0, end);
    }

    // Convenience: split entire text.
    public static List<String> getChunks(String text, int tokenSizeCap, Function<String, Integer> getSize) {
        if (text == null) return List.of();
        if (text.isEmpty() || tokenSizeCap == Integer.MAX_VALUE) return List.of(text);
        if (tokenSizeCap <= 0) throw new IllegalArgumentException("tokenSizeCap must be > 0");

        Session session = new Session(tokenSizeCap, getSize);

        // Fast path: single call if it fits.
        int full = getSize.apply(text);
        if (full <= tokenSizeCap) return List.of(text);

        List<String> out = new ArrayList<>();
        int n = text.length();
        int start = 0;
        while (start < n) {
            int end = session.nextChunkEnd(text, start, false);
            out.add(text.substring(start, end));
            start = end;
        }
        return out;
    }

    // Optional reusable state to minimize getSize calls across multiple operations.
    public static final class Session {
        private final int tokenSizeCap;
        private final Function<String, Integer> getSize;
        private double charsPerToken = INITIAL_CHARS_PER_TOKEN;

        public Session(int tokenSizeCap, Function<String, Integer> getSize) {
            if (tokenSizeCap <= 0) throw new IllegalArgumentException("tokenSizeCap must be > 0");
            this.tokenSizeCap = tokenSizeCap;
            this.getSize = getSize;
        }

        public String nextChunk(String text) {
            return nextChunk(text, true);
        }

        public String nextChunk(String text, boolean checkFullFitFirst) {
            if (text == null || text.isEmpty()) return "";
            if (checkFullFitFirst) {
                int full = getSize.apply(text);
                if (full <= tokenSizeCap) return text;
            }
            int end = nextChunkEnd(text, 0, false);
            return text.substring(0, end);
        }

        public List<String> getChunks(String text) {
            if (text == null) return List.of();
            if (text.isEmpty() || tokenSizeCap == Integer.MAX_VALUE) return List.of(text);

            // Optional one-time check to short-circuit when it all fits.
            int full = getSize.apply(text);
            if (full <= tokenSizeCap) return List.of(text);

            List<String> out = new ArrayList<>();
            int n = text.length();
            int start = 0;
            while (start < n) {
                int end = nextChunkEnd(text, start, false);
                out.add(text.substring(start, end));
                start = end;
            }
            return out;
        }

        // Core routine: compute end of next chunk starting at 'start'.
        private int nextChunkEnd(String text, int start, boolean checkFullFitFirst) {
            int n = text.length();
            int remaining = n - start;

            if (remaining <= 0) return start;

            if (checkFullFitFirst) {
                int tAll = measure(text, start, remaining, null);
                if (tAll <= tokenSizeCap) return n;
            }

            // Per-chunk cache of length->token count.
            Map<Integer, Integer> cache = new HashMap<>();

            // 1) Initial guess
            int guess = clampInt(1, remaining,
                    (int) Math.floor(tokenSizeCap * charsPerToken * SAFETY_UNDER));

            int tGuess = measure(text, start, guess, cache);
            updateEstimate(guess, tGuess);

            int bestGoodLen = 0;
            int bestGoodTok = 0;
            Integer badLen = null;

            if (tGuess <= tokenSizeCap) {
                bestGoodLen = guess;
                bestGoodTok = tGuess;

                // 2a) Scale up toward cap
                double factor = Math.min(MAX_GROW_FACTOR, (tokenSizeCap / (double) tGuess) * SCALE_SAFETY);
                int up = clampInt(bestGoodLen + 1, remaining, (int) Math.floor(bestGoodLen * factor));
                if (up > bestGoodLen) {
                    int tUp = measure(text, start, up, cache);
                    updateEstimate(up, tUp);
                    if (tUp <= tokenSizeCap) {
                        bestGoodLen = up;
                        bestGoodTok = tUp;

                        // Optional second expansion
                        double factor2 = Math.min(MAX_GROW_FACTOR, (tokenSizeCap / (double) tUp) * SCALE_SAFETY);
                        int up2 = clampInt(bestGoodLen + 1, remaining, (int) Math.floor(bestGoodLen * factor2));
                        if (up2 > bestGoodLen) {
                            int tUp2 = measure(text, start, up2, cache);
                            updateEstimate(up2, tUp2);
                            if (tUp2 <= tokenSizeCap) {
                                bestGoodLen = up2;
                                bestGoodTok = tUp2;
                            } else {
                                badLen = up2;
                            }
                        }
                    } else {
                        badLen = up;
                    }
                }
            } else {
                // 2b) Too big: scale down
                double factor = Math.max(MIN_SHRINK_FACTOR, (tokenSizeCap / (double) tGuess) * SCALE_SAFETY);
                int down = clampInt(1, guess - 1, (int) Math.floor(guess * factor));
                if (down < guess) {
                    int tDown = measure(text, start, down, cache);
                    updateEstimate(down, tDown);
                    if (tDown <= tokenSizeCap) {
                        bestGoodLen = down;
                        bestGoodTok = tDown;
                        badLen = guess;
                    } else {
                        double factor2 = Math.max(MIN_SHRINK_FACTOR, (tokenSizeCap / (double) tDown) * SCALE_SAFETY);
                        int down2 = clampInt(1, down - 1, (int) Math.floor(down * factor2));
                        if (down2 < down) {
                            int tDown2 = measure(text, start, down2, cache);
                            updateEstimate(down2, tDown2);
                            if (tDown2 <= tokenSizeCap) {
                                bestGoodLen = down2;
                                bestGoodTok = tDown2;
                                badLen = down;
                            } else {
                                badLen = down2;
                            }
                        } else {
                            badLen = down;
                        }
                    }
                } else {
                    int t1 = measure(text, start, 1, cache);
                    updateEstimate(1, t1);
                    if (t1 > tokenSizeCap) {
                        throw new IllegalArgumentException(
                                "Token cap " + tokenSizeCap + " too small to fit even one character at position " + start);
                    }
                    bestGoodLen = 1;
                    bestGoodTok = t1;
                    badLen = guess;
                }
            }

            // 3) Small refinement if we have a bracket
            if (badLen != null && bestGoodLen > 0) {
                int lo = bestGoodLen + 1;
                int hi = Math.min(badLen - 1, remaining);
                int steps = 0;
                while (lo <= hi && steps < MAX_REFINEMENT_STEPS) {
                    int mid = lo + ((hi - lo) >>> 1);
                    int tMid = measure(text, start, mid, cache);
                    updateEstimate(mid, tMid);
                    if (tMid <= tokenSizeCap) {
                        bestGoodLen = mid;
                        bestGoodTok = tMid;
                        lo = mid + 1;
                    } else {
                        hi = mid - 1;
                    }
                    steps++;
                }
            }

            if (bestGoodLen == 0) {
                int t1 = measure(text, start, 1, cache);
                updateEstimate(1, t1);
                if (t1 > tokenSizeCap) {
                    throw new IllegalArgumentException(
                            "Token cap " + tokenSizeCap + " too small to fit even one character at position " + start);
                }
                bestGoodLen = 1;
                bestGoodTok = t1;
            }

            int endExclusive = start + bestGoodLen;

            // 4) Prefer a "nice" boundary at or before endExclusive (no extra token counts)
            int niceCut = findNiceCut(text, start, endExclusive);
            if (niceCut > start) {
                endExclusive = niceCut;
            }

            return endExclusive;
        }

        private int measure(String text, int start, int len, Map<Integer, Integer> cache) {
            if (cache != null) {
                Integer cached = cache.get(len);
                if (cached != null) return cached;
            }
            int tokens = getSize.apply(text.substring(start, start + len));
            if (cache != null) cache.put(len, tokens);
            return tokens;
        }

        private void updateEstimate(int chars, int tokens) {
            if (tokens <= 0) return;
            double sample = (double) chars / (double) tokens;
            double updated = charsPerToken * (1.0 - EWMA_ALPHA) + sample * EWMA_ALPHA;
            if (Double.isFinite(updated)) {
                if (updated < MIN_CHARS_PER_TOKEN) updated = MIN_CHARS_PER_TOKEN;
                if (updated > MAX_CHARS_PER_TOKEN) updated = MAX_CHARS_PER_TOKEN;
                charsPerToken = updated;
            }
        }
    }

    // Helper utilities
    private static int clampInt(int lo, int hi, int v) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static int findNiceCut(String s, int start, int endExclusive) {
        // prefer newline
        for (int i = endExclusive - 1; i >= start; i--) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') return i + 1;
        }
        // then whitespace
        for (int i = endExclusive - 1; i >= start; i--) {
            if (Character.isWhitespace(s.charAt(i))) return i + 1;
        }
        // then sentence-ending punctuation
        for (int i = endExclusive - 1; i >= start; i--) {
            char c = s.charAt(i);
            if (c == '.' || c == '!' || c == '?') return i + 1;
        }
        return start; // no nicer boundary found
    }
}
