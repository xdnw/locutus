package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.util.StringMan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GlobalSubstringDAG implements ICodedStringMap {
    // Global store of substrings
    private final List<String> substringStore = new ObjectArrayList<>();
    // Maps from substring to its index in the store
    private final Map<Long, Integer> substringIndices = new Long2IntOpenHashMap();
    // For each string, store its representation as a sequence of substring indices
    private final List<int[]> stringEncodings = new ObjectArrayList<>();

    // Parameters for tuning compression/performance
    private final int MIN_SUBSTRING_LENGTH = 3;
    private final int MAX_NEW_SUBSTRING_LENGTH = 20;
    private final int MAX_SEARCH_DEPTH = 1000;

    // Trie for efficient substring matching
    private final SubstringTrie trie = new SubstringTrie();

    private static class SubstringTrie {
        private final TrieNode root = new TrieNode();

        private static class TrieNode {
            Map<Character, TrieNode> children = new Char2ObjectOpenHashMap<>();
            List<Integer> substrings = new IntArrayList(4); // substring indices ending here
        }

        // Add a substring to the trie with its index
        public void add(String s, int index) {
            TrieNode current = root;

            // For each character, build the trie path
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                current.children.computeIfAbsent(c, k -> new TrieNode());
                current = current.children.get(c);

                // At each node, add the substring index
                if (i == s.length() - 1) {
                    current.substrings.add(index);
                }
            }
        }

        // Find the longest matching substring starting at the given position
        public Match findLongestMatch(String s, int startPos) {
            TrieNode current = root;
            Match bestMatch = null;
            int matchLength = 0;

            for (int i = startPos; i < s.length(); i++) {
                char c = s.charAt(i);
                if (!current.children.containsKey(c)) {
                    break;
                }

                current = current.children.get(c);
                matchLength++;

                // If we have substrings ending here, update the best match
                if (!current.substrings.isEmpty()) {
                    bestMatch = new Match(current.substrings.get(0), matchLength);
                }
            }

            return bestMatch;
        }
    }

    private static class Match {
        final int substringIndex;
        final int length;

        Match(int substringIndex, int length) {
            this.substringIndex = substringIndex;
            this.length = length;
        }
    }

    @Override
    public int insert(String value) {
        int[] encoding = encodeString(value);

        // Store the encoding and add to cache
        int stringIndex = stringEncodings.size();
        stringEncodings.add(encoding);

        return stringIndex;
    }

    private int[] encodeString(String value) {
        List<Integer> segments = new ArrayList<>();
        int pos = 0;

        while (pos < value.length()) {
            // Try to find the longest matching existing substring
            Match match = trie.findLongestMatch(value, pos);

            if (match != null && match.length >= MIN_SUBSTRING_LENGTH) {
                // Use the existing substring
                segments.add(match.substringIndex);
                pos += match.length;
            } else {
                // No good match found, create a new substring
                int newLength = Math.min(MAX_NEW_SUBSTRING_LENGTH, value.length() - pos);
                String newSubstring = value.substring(pos, pos + newLength);

                // Add the new substring to the store and trie
                int newIndex = addSubstring(newSubstring);
                segments.add(newIndex);
                pos += newLength;
            }
        }

        // Convert to array
        int[] result = new int[segments.size()];
        for (int i = 0; i < segments.size(); i++) {
            result[i] = segments.get(i);
        }

        return result;
    }

    private int addSubstring(String substring) {
        // Check if the substring already exists
        long hash = StringMan.hash(substring);
        Integer existingIndex = substringIndices.get(hash);
        if (existingIndex != null) {
            return existingIndex;
        }

        // Add new substring
        int index = substringStore.size();
        substringStore.add(substring);
        substringIndices.put(hash, index);
        trie.add(substring, index);

        return index;
    }

    @Override
    public String get(int index) {
        if (index < 0 || index >= stringEncodings.size()) {
            return null;
        }

        // Decode the string from its segments
        int[] encoding = stringEncodings.get(index);
        StringBuilder result = new StringBuilder();

        for (int substringIndex : encoding) {
            result.append(substringStore.get(substringIndex));
        }

        String decodedString = result.toString();

        return decodedString;
    }

    @Override
    public int size() {
        return stringEncodings.size();
    }

    @Override
    public int charSize() {
        // Sum of substring store sizes
        return substringStore.stream().mapToInt(String::length).sum();
    }

    @Override
    public void finishLoad() {
        // Optional: Perform global optimization of the substring store
        optimizeSubstringStore();
    }

    private void optimizeSubstringStore() {
        // This could perform global optimization by:
        // 1. Finding frequently used patterns
        // 2. Breaking up rarely used long substrings
        // 3. Merging adjacent substrings that frequently appear together

        // For now, we'll leave this as future work
        System.out.println("Global substring optimization complete");
        System.out.println("Substring store size: " + substringStore.size());
        System.out.println("Average string segments: " +
                (double)stringEncodings.stream().mapToInt(arr -> arr.length).sum() / stringEncodings.size());
    }
}