package link.locutus.discord.util.discord;

import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.ext.footnotes.FootnoteBlock;
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.text.BreakIterator;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownSplitter {
    private final Parser parser;
    private final int maxChars;
    private final BreakIterator sentenceBreak;

    private static final Pattern ITEM_MARKER = Pattern.compile("^([ \\t]*([*+-]|\\d+[.)])[ \\t]+)");

    public MarkdownSplitter(int maxChars, Locale locale) {
        this.maxChars = Math.max(1, maxChars);
        MutableDataSet opts = new MutableDataSet();
        // Enable common extensions required by the spec
        opts.set(Parser.EXTENSIONS, Arrays.asList(
//                TablesExtension.create(),
                FootnoteExtension.create(),
                TaskListExtension.create()
        ));
        this.parser = Parser.builder(opts).build();
        this.sentenceBreak = BreakIterator.getSentenceInstance(locale == null ? Locale.getDefault() : locale);
    }

    public List<String> split(String markdown) {
        Document doc = parser.parse(markdown);
        List<String> out = new ObjectArrayList<>();
        StringBuilder chunk = new StringBuilder(Math.min(markdown.length(), Math.max(64, maxChars)));
        int remaining = maxChars;

        for (Node node = doc.getFirstChild(); node != null; node = node.getNext()) {
            // Heading attach-to-next optimization:
            if (node instanceof Heading) {
                Node next = node.getNext();
                int hLen = node.getChars().length();
                int pairLen = hLen + (next != null ? next.getChars().length() : 0);
                if (next != null && hLen <= maxChars && next.getChars().length() <= maxChars && pairLen <= maxChars) {
                    if (chunk.length() > 0 && remaining < pairLen) {
                        out.add(chunk.toString());
                        chunk.setLength(0);
                        remaining = maxChars;
                    }
                }
            }

            List<Fragment> frags = splitTopLevelNode(node);
            for (Fragment f : frags) {
                int fl = f.length();
                if (fl <= remaining) {
                    f.appendTo(chunk);
                    remaining -= fl;
                } else if (fl <= maxChars) {
                    if (chunk.length() > 0) {
                        out.add(chunk.toString());
                        chunk.setLength(0);
                        remaining = maxChars;
                    }
                    f.appendTo(chunk);
                    remaining -= fl;
                } else {
                    // Force-split this fragment
                    for (Fragment sub : forceSplitFragment(f)) {
                        int sl = sub.length();
                        if (sl <= remaining) {
                            sub.appendTo(chunk);
                            remaining -= sl;
                        } else {
                            if (chunk.length() > 0) {
                                out.add(chunk.toString());
                                chunk.setLength(0);
                                remaining = maxChars;
                            }
                            sub.appendTo(chunk);
                            remaining -= sl;
                        }
                    }
                }
            }
        }

        if (chunk.length() > 0) {
            out.add(chunk.toString());
        }
        return out;
    }

    // Fragment abstraction to minimize copying. Prefer BasedSequence slices; synthesize only when needed.
    private interface Fragment {
        int length();

        void appendTo(StringBuilder sb);
    }

    private record SliceFragment(BasedSequence seq) implements Fragment {

        @Override
            public int length() {
                return seq.length();
            }

            @Override
            public void appendTo(StringBuilder sb) {
                sb.append(seq);
            }
        }

    private record StringFragment(String s) implements Fragment {
        @Override
            public int length() {
                return s.length();
            }

            @Override
            public void appendTo(StringBuilder sb) {
                sb.append(s);
            }
        }

    private List<Fragment> splitTopLevelNode(Node node) {
        BasedSequence chars = node.getChars();
        int len = chars.length();

        if (len <= maxChars) {
            // fast path
            return Collections.singletonList(new SliceFragment(chars));
        }

        return switch (node) {
            case Heading h -> splitByLinesOrForce(h.getChars());
            case Paragraph p -> splitParagraph(p);
            case ThematicBreak t -> Collections.singletonList(new SliceFragment(t.getChars()));
            case FencedCodeBlock f -> splitFencedCodeBlock(f);
            case IndentedCodeBlock ic -> splitIndentedCodeBlock(ic);
            case BlockQuote bq -> splitBlockQuote(bq);
            case BulletList bl -> splitList(bl);
            case OrderedList ol -> splitList(ol);
            case TableBlock tb -> splitTable(tb);
            case HtmlBlock hb -> splitByLinesOrForce(hb.getChars());
            case FootnoteBlock fb -> splitByLinesOrForce(fb.getChars());
            case HtmlCommentBlock hc -> splitByLinesOrForce(hc.getChars());
            default -> splitByLinesOrForce(chars);
        };
    }

    // Paragraph splitting: preserve inline atomics; split Text by newline > sentence > whitespace > forced
    private List<Fragment> splitParagraph(Paragraph p) {
        BasedSequence whole = p.getChars();
        if (whole.length() <= maxChars) {
            return Collections.singletonList(new SliceFragment(whole));
        }

        List<Fragment> frags = new ObjectArrayList<>();
        for (Node in = p.getFirstChild(); in != null; in = in.getNext()) {
            if (in instanceof Text) {
                BasedSequence t = in.getChars();
                frags.addAll(splitTextByPriority(t));
            } else if (in instanceof SoftLineBreak || in instanceof HardLineBreak) {
                frags.add(new SliceFragment(in.getChars()));
            } else if (in instanceof Code || in instanceof Emphasis || in instanceof StrongEmphasis ||
                    in instanceof Link || in instanceof Image || in instanceof HtmlInline) {
                BasedSequence atom = in.getChars();
                if (atom.length() <= maxChars) {
                    frags.add(new SliceFragment(atom));
                } else {
                    // Forced split inside atomic inline as last resort
                    frags.addAll(forceSplitByPriority(atom));
                }
            } else {
                // Any other inline: treat as atomic; force if needed
                BasedSequence atom = in.getChars();
                if (atom.length() <= maxChars) {
                    frags.add(new SliceFragment(atom));
                } else {
                    frags.addAll(forceSplitByPriority(atom));
                }
            }
        }
        return frags;
    }

    // Split text by pref order within max: newline > sentence > whitespace > forced hard split
    private List<Fragment> splitTextByPriority(BasedSequence seq) {
        return splitCharSequenceByPriority(seq);
    }

    private List<Fragment> forceSplitByPriority(BasedSequence seq) {
        return splitCharSequenceByPriority(seq);
    }

    private List<Fragment> splitCharSequenceByPriority(BasedSequence seq) {
        List<Fragment> out = new ObjectArrayList<>();
        int start = 0;
        int n = seq.length();

        // Set BreakIterator text ONCE per sequence. JDK8-compatible: use String
        String biText = seq.toString();
        sentenceBreak.setText(biText);

        while (start < n) {
            int remaining = n - start;
            if (remaining <= maxChars) {
                out.add(new SliceFragment(seq.subSequence(start, n)));
                break;
            }
            int limit = Math.min(n, start + maxChars);
            int cut = findBestCutWithPreSetBreakIterator(seq, biText, start, limit);
            if (cut <= start) {
                // Safety: hard split at limit (avoid splitting surrogate pairs)
                cut = safeEndIndex(seq, start, limit);
            }
            out.add(new SliceFragment(seq.subSequence(start, cut)));
            start = cut;
        }
        return out;
    }

    // Find best cut at or before limit: newline > sentence boundary > whitespace; else -1
    private int findBestCutWithPreSetBreakIterator(BasedSequence seq, String biText, int from, int limit) {
        // 1) newline boundary (treat CRLF atomically where possible)
        for (int i = limit - 1; i >= from; i--) {
            char c = seq.charAt(i);
            if (c == '\n') {
                return i + 1;
            }
            if (c == '\r') {
                // If CRLF entirely inside window, include both
                if (i + 1 < seq.length() && seq.charAt(i + 1) == '\n' && i + 2 <= limit) {
                    return i + 2;
                }
                return i + 1;
            }
        }

        // 2) sentence boundary (BreakIterator already set to biText)
        int b = sentenceBreak.preceding(limit);
        if (b != BreakIterator.DONE && b > from) {
            return b;
        }

        // 3) whitespace boundary
        for (int i = limit - 1; i >= from; i--) {
            if (Character.isWhitespace(seq.charAt(i))) {
                return (i > from) ? i : from + 1;
            }
        }

        // 4) none
        return -1;
    }

    // Fenced code: split by lines and re-wrap with synthetic fences
    private List<Fragment> splitFencedCodeBlock(FencedCodeBlock code) {
        BasedSequence content = code.getContentChars();
        String info = code.getInfo().toString().trim();
        char fenceChar = code.getOpeningMarker().isEmpty() ? '`' : code.getOpeningMarker().charAt(0);
        return splitCodeContentWithFences(content, info, fenceChar);
    }

    // Indented code: split by lines and re-wrap with synthetic fences
    private List<Fragment> splitIndentedCodeBlock(IndentedCodeBlock code) {
        BasedSequence content = code.getContentChars();
        return splitCodeContentWithFences(content, "", '`');
    }

    private List<Fragment> splitCodeContentWithFences(BasedSequence content, String info, char fenceChar) {
        String cont = content.toString();
        int longestRun = longestRunOfChar(cont, fenceChar);
        int fenceLen = Math.max(3, longestRun + 1);
        String fence = repeatChar(fenceChar, fenceLen);
        String opening = fence + (info.isEmpty() ? "" : info) + "\n";
        String closing = "\n" + fence;
        int overhead = opening.length() + closing.length();
        int maxBody = Math.max(1, maxChars - overhead);

        List<Fragment> out = new ObjectArrayList<>();
        List<String> lines = splitPreserveNewlines(cont);
        StringBuilder body = new StringBuilder(Math.min(cont.length(), Math.max(64, maxBody)));
        int bodyLen = 0;

        StringBuilder wrapper = new StringBuilder(Math.max(32, maxBody + overhead));

        for (String line : lines) {
            int lineLen = line.length();
            if (bodyLen + lineLen + overhead <= maxChars) {
                body.append(line);
                bodyLen += lineLen;
            } else {
                if (bodyLen > 0) {
                    wrapper.setLength(0);
                    wrapper.append(opening).append(body).append(closing);
                    out.add(new StringFragment(wrapper.toString()));
                    body.setLength(0);
                    bodyLen = 0;
                }
                if (lineLen + overhead <= maxChars) {
                    body.append(line);
                    bodyLen = lineLen;
                } else {
                    // Single line too large: hard-split line into parts that fit (avoid surrogate splits)
                    int s = 0;
                    while (s < lineLen) {
                        int e = Math.min(lineLen, s + maxBody);
                        e = safeEndIndex(line, s, e);
                        String piece = line.substring(s, e);
                        wrapper.setLength(0);
                        wrapper.append(opening).append(piece).append(closing);
                        out.add(new StringFragment(wrapper.toString()));
                        s = e;
                    }
                }
            }
        }
        if (bodyLen > 0) {
            wrapper.setLength(0);
            wrapper.append(opening).append(body).append(closing);
            out.add(new StringFragment(wrapper.toString()));
        }
        return out;
    }

    // BlockQuote: prefer splitting between child blocks; synthesize quoting when needed
    private List<Fragment> splitBlockQuote(BlockQuote bq) {
        BasedSequence all = bq.getChars();
        if (all.length() <= maxChars) {
            return Collections.singletonList(new SliceFragment(all));
        }

        // Try to split by children, concatenating into quote-wrapped segments
        List<List<Fragment>> childSegments = new ObjectArrayList<>();
        for (Node ch = bq.getFirstChild(); ch != null; ch = ch.getNext()) {
            // Use top-level splitter on each child block to respect their semantics
            List<Fragment> sub = splitTopLevelNode(ch);
            childSegments.add(sub);
        }

        List<Fragment> out = new ObjectArrayList<>();
        StringBuilder acc = new StringBuilder();
        int accLen = 0;

        for (List<Fragment> child : childSegments) {
            // materialize child's content to string (preserve original; we will re-wrap with '>')
            String childText = materialize(child);
            String quoted = quoteWrap(childText);

            int qLen = quoted.length();
            if (qLen <= 0) continue;

            if (accLen + qLen <= maxChars) {
                acc.append(quoted);
                accLen += qLen;
            } else if (qLen <= maxChars) {
                if (accLen > 0) {
                    out.add(new StringFragment(acc.toString()));
                    acc.setLength(0);
                    accLen = 0;
                }
                acc.append(quoted);
                accLen = qLen;
            } else {
                // quoted part itself too big: split by lines within quoting
                if (accLen > 0) {
                    out.add(new StringFragment(acc.toString()));
                    acc.setLength(0);
                    accLen = 0;
                }
                for (String line : splitPreserveNewlines(quoted)) {
                    if (line.length() <= maxChars) {
                        if (accLen + line.length() <= maxChars) {
                            acc.append(line);
                            accLen += line.length();
                        } else {
                            if (accLen > 0) {
                                out.add(new StringFragment(acc.toString()));
                                acc.setLength(0);
                                accLen = 0;
                            }
                            acc.append(line);
                            accLen = line.length();
                        }
                    } else {
                        // hard split line (avoid surrogate pairs)
                        int s = 0;
                        while (s < line.length()) {
                            int e = Math.min(line.length(), s + maxChars);
                            e = safeEndIndex(line, s, e);
                            out.add(new StringFragment(line.substring(s, e)));
                            s = e;
                        }
                    }
                }
            }
        }
        if (accLen > 0) {
            out.add(new StringFragment(acc.toString()));
        }
        return out;
    }

    private static String quoteWrap(String s) {
        if (s.isEmpty()) return s;
        // Estimate: add 2 chars per line for "> "
        StringBuilder out = new StringBuilder(s.length() + Math.max(8, s.length() >>> 5));
        int n = s.length();
        int i = 0;
        while (i < n) {
            out.append("> ");
            int lineStart = i;
            // read until EOL
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\r' || c == '\n') break;
                i++;
            }
            out.append(s, lineStart, i);
            // copy newline(s) verbatim
            if (i < n) {
                char c = s.charAt(i++);
                out.append(c);
                if (c == '\r' && i < n && s.charAt(i) == '\n') {
                    out.append('\n');
                    i++;
                }
            }
        }
        return out.toString();
    }

    // Lists: split between items; if one item oversize, split inside and synthesize single-item lists
    private List<Fragment> splitList(ListBlock list) {
        BasedSequence all = list.getChars();
        if (all.length() <= maxChars) {
            return Collections.singletonList(new SliceFragment(all));
        }

        List<Fragment> out = new ObjectArrayList<>();
        StringBuilder acc = new StringBuilder();
        int accLen = 0;

        for (Node it = list.getFirstChild(); it != null; it = it.getNext()) {
            if (!(it instanceof ListItem)) continue;
            ListItem item = (ListItem) it;
            BasedSequence itemSeq = item.getChars();
            if (itemSeq.length() <= maxChars) {
                // Try to pack this full item slice
                if (accLen + itemSeq.length() <= maxChars) {
                    acc.append(itemSeq);
                    accLen += itemSeq.length();
                } else {
                    if (accLen > 0) {
                        out.add(new StringFragment(acc.toString()));
                        acc.setLength(0);
                        accLen = 0;
                    }
                    acc.append(itemSeq);
                    accLen = itemSeq.length();
                }
            } else {
                // Item too large, split its children and synthesize as single-item lists
                if (accLen > 0) {
                    out.add(new StringFragment(acc.toString()));
                    acc.setLength(0);
                    accLen = 0;
                }
                List<Fragment> itemParts = splitListItem(item);
                out.addAll(itemParts);
            }
        }
        if (accLen > 0) {
            out.add(new StringFragment(acc.toString()));
        }
        return out;
    }

    private List<Fragment> splitListItem(ListItem item) {
        final String marker = extractListItemMarker(item);
        final int markerLen = marker.length();
        final int maxCharsLocal = this.maxChars; // cache field
        final int maxBody = Math.max(1, maxCharsLocal - markerLen); // body size when hard-splitting
        final String indent = repeatSpaces(markerLen); // continuation indent, same width as marker

// Render children
        List<Fragment> childParts = new ObjectArrayList<>();
        for (Node ch = item.getFirstChild(); ch != null; ch = ch.getNext()) {
            childParts.addAll(splitTopLevelNode(ch));
        }
        final String content = materialize(childParts);

        final List<Fragment> out = new ObjectArrayList<>();
        final StringBuilder acc = new StringBuilder(Math.min(1024, Math.max(64, content.length() + 16)));
        int accLen = 0;

// Stream over lines, preserving newlines (handles \n and \r\n)
        final int n = content.length();
        int i = 0;
        while (i < n) {
            int lineStart = i;
            int lineEnd = i;
            while (lineEnd < n) {
                char c = content.charAt(lineEnd);
                if (c == '\n') { lineEnd++; break; }
                if (c == '\r') {
                    if (lineEnd + 1 < n && content.charAt(lineEnd + 1) == '\n') lineEnd += 2;
                    else lineEnd += 1;
                    break;
                }
                lineEnd++;
            }
            // If we reached end without newline, lineEnd == n

            int lineLen = lineEnd - lineStart;
            int candidateLen = markerLen + lineLen; // same for first or continuation (marker and indent have equal length)

            if (accLen + candidateLen <= maxCharsLocal) {
                acc.append(accLen == 0 ? marker : indent);
                acc.append(content, lineStart, lineEnd);
                accLen += candidateLen;
            } else if (candidateLen <= maxCharsLocal) {
                if (accLen > 0) {
                    out.add(new StringFragment(acc.toString()));
                    acc.setLength(0);
                    accLen = 0;
                }
                acc.append(marker);
                acc.append(content, lineStart, lineEnd);
                accLen = candidateLen;
            } else {
                // The line cannot fit even alone; hard-split the line itself at safe boundaries
                if (accLen > 0) {
                    out.add(new StringFragment(acc.toString()));
                    acc.setLength(0);
                    accLen = 0;
                }

                // Create the line string once for safeEndIndex; avoids per-chunk substrings below
                final String lineStr = content.substring(lineStart, lineEnd);
                int s = 0;
                while (s < lineStr.length()) {
                    int e = Math.min(lineStr.length(), s + maxBody);
                    e = safeEndIndex(lineStr, s, e); // adjust to safe boundary
                    acc.append(s == 0 ? marker : indent);
                    acc.append(lineStr, s, e);
                    out.add(new StringFragment(acc.toString()));
                    acc.setLength(0);
                    s = e;
                }
            }

            i = lineEnd;
        }

        if (accLen > 0) {
            out.add(new StringFragment(acc.toString()));
        }
        return out;
    }

    private String extractListItemMarker(ListItem item) {
        CharSequence chars = item.getChars();
        int lineEnd = indexOfNewline(chars);
        CharSequence firstLine = chars.subSequence(0, lineEnd >= 0 ? lineEnd : chars.length());

        Matcher m = ITEM_MARKER.matcher(firstLine);
        if (m.find()) {
            return m.group(1);
        }

        return (item.getParent() instanceof OrderedList) ? "1. " : "- ";
    }

    private int indexOfNewline(CharSequence cs) {
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i);
            if (c == '\n' || c == '\r') return i;
        }
        return -1;
    }

    // Tables: prefer whole; else split by body rows, repeating header and separator
    private List<Fragment> splitTable(TableBlock table) {
        BasedSequence seq = table.getChars();
        if (seq.length() <= maxChars) {
            return Collections.singletonList(new SliceFragment(seq));
        }

        List<String> lines = splitPreserveNewlines(seq.toString());
        if (lines.size() < 2) {
            return splitByLinesOrForce(seq);
        }

        String header = lines.get(0);
        String sep = lines.get(1);
        int overhead = header.length() + sep.length();

        List<Fragment> out = new ObjectArrayList<>();
        StringBuilder acc = new StringBuilder();
        int accLen = 0;

        // Initialize accumulator with header + separator
        acc.append(header).append(sep);
        accLen = overhead;

        int maxRowPart = Math.max(1, maxChars - overhead);

        for (int i = 2; i < lines.size(); i++) {
            String row = lines.get(i);
            int rowLen = row.length();

            // If row fits in current accumulator
            if (accLen + rowLen <= maxChars) {
                acc.append(row);
                accLen += rowLen;
                continue;
            }

            // If row fits on its own, flush current accumulator
            if (overhead + rowLen <= maxChars) {
                if (accLen > overhead) {
                    out.add(new StringFragment(acc.toString()));
                }
                acc.setLength(0);
                acc.append(header).append(sep).append(row);
                accLen = overhead + rowLen;
                continue;
            }

            // Row too big: split it into chunks
            for (int s = 0; s < rowLen; s += maxRowPart) {
                int e = safeEndIndex(row, s, Math.min(s + maxRowPart, rowLen));
                out.add(new StringFragment(header + sep + row.substring(s, e)));
            }

            // Reset accumulator for next row
            acc.setLength(0);
            acc.append(header).append(sep);
            accLen = overhead;
        }

        if (accLen > overhead) {
            out.add(new StringFragment(acc.toString()));
        }

        return out;
    }

    // HTML / Footnotes / generic blocks: split by lines, else hard split
    private List<Fragment> splitByLinesOrForce(BasedSequence seq) {
        List<Fragment> out = new ObjectArrayList<>();
        int n = seq.length();
        int start = 0;

        while (start < n) {
            int end = Math.min(n, start + maxChars);
            // Try to find last newline within window (treat CRLF atomically when inside window)
            int cut = -1;
            for (int i = end - 1; i > start; i--) {
                char c = seq.charAt(i);
                if (c == '\n') {
                    cut = i + 1;
                    break;
                }
                if (c == '\r') {
                    if (i + 1 < n && seq.charAt(i + 1) == '\n' && i + 2 <= end) {
                        cut = i + 2;
                    } else {
                        cut = i + 1;
                    }
                    break;
                }
            }
            if (cut == -1) {
                cut = safeEndIndex(seq, start, end); // avoid splitting surrogate pairs
            }
            out.add(new SliceFragment(seq.subSequence(start, cut)));
            start = cut;
        }
        return out;
    }

    // Force split a fragment (materialize if needed)
    private List<Fragment> forceSplitFragment(Fragment f) {
        List<Fragment> out = new ObjectArrayList<>();
        if (f instanceof SliceFragment) {
            BasedSequence s = ((SliceFragment) f).seq();
            int n = s.length();
            for (int i = 0; i < n; ) {
                int e = Math.min(n, i + maxChars);
                e = safeEndIndex(s, i, e);
                out.add(new SliceFragment(s.subSequence(i, e)));
                i = e;
            }
            return out;
        }
        // fallback for StringFragment or other implementations
        String s = materialize(Collections.singletonList(f));
        int i = 0;
        while (i < s.length()) {
            int e = Math.min(s.length(), i + maxChars);
            e = safeEndIndex(s, i, e);
            out.add(new StringFragment(s.substring(i, e)));
            i = e;
        }
        return out;
    }

    private static String materialize(List<Fragment> fragments) {
        int totalLen = 0;
        for (Fragment f : fragments) totalLen += f.length();
        StringBuilder sb = new StringBuilder(totalLen);
        for (Fragment f : fragments) f.appendTo(sb);
        return sb.toString();
    }

    private static String firstLineOf(String s) {
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') return s.substring(0, i);
        }
        return s;
    }

    // Linear scanner that preserves CR/LF terminators; returns each physical line including its EOL
    private static List<String> splitPreserveNewlines(String s) {
        List<String> lines = new ObjectArrayList<>(Math.max(8, s.length() >>> 5));
        int start = 0, i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i++);
            if (c == '\n') {
                lines.add(s.substring(start, i));
                start = i;
            } else if (c == '\r') {
                if (i < n && s.charAt(i) == '\n') i++;
                lines.add(s.substring(start, i));
                start = i;
            }
        }
        if (start < n) lines.add(s.substring(start));
        return lines;
    }

    private static int longestRunOfChar(String s, char ch) {
        int max = 0, cur = 0, n = s.length();
        for (int i = 0; i < n; i++) {
            if (s.charAt(i) == ch) {
                max = Math.max(max, ++cur);
            } else {
                cur = 0;
            }
        }
        return max;
    }

    private static String repeatChar(char ch, int count) {
        if (count <= 0) return "";
        char[] a = new char[count];
        Arrays.fill(a, ch);
        return new String(a);
    }

    private static String repeatSpaces(int count) {
        return repeatChar(' ', count);
    }

    // Ensure we don't split surrogate pairs when slicing at [start, end)
    private static int safeEndIndex(CharSequence s, int start, int end) {
        if (end <= start || end > s.length()) return end;
        if (end < s.length() && Character.isSurrogatePair(s.charAt(end - 1), s.charAt(end))) {
            return end - 1;
        }
        return end;
    }
}