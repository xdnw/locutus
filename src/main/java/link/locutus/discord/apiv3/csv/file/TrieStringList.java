package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TrieStringList implements ICodedStringMap {
    private final List<Object> optimized = new ObjectArrayList<>();
    private final LongOpenHashSet hashes = new LongOpenHashSet();
    private ITrieNode root = new MultiChild('\0', null, new ITrieNode[0]);

    @Override
    public boolean insert(String value) {
        if (value.isEmpty()) throw new IllegalArgumentException("Empty string");
        long hash = StringMan.hash(value);
        if (!hashes.add(hash)) {
            return false;
        }
        if (value.length() < 64) {
            optimized.add(value);
            return true;
        }
        ITrieNode current = root;
        boolean created = false;
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            ITrieNode child = current.getChildOrNull(c);
            if (child == null) {
                child = current.createChild(c);
            }
            current = child;
            i++;
        }
        optimized.add(current);
        return created;
    }


    public static class EndNode implements ITrieNode {
        private final char c;
        private final ITrieNode parentOrNull;
        // No children

        @Override
        public char getChar() {
            return c;
        }

        @Override
        public ITrieNode getParentOrNull() {
            return parentOrNull;
        }

        public EndNode(char c, ITrieNode parentOrNull) {
            this.c = c;
            this.parentOrNull = parentOrNull;
        }

        @Override
        public ITrieNode[] getChildrenSorted() {
            return new ITrieNode[0];
        }

        @Override
        public ITrieNode getChildOrNull(char c) {
            return null;
        }

        @Override
        public void replaceChild(ITrieNode oldChild, ITrieNode newChild) {
            throw new UnsupportedOperationException("EndNode has no children");
        }

        @Override
        public ITrieNode createChild(char c) {
            SingleChild newNode = new SingleChild(getChar(), getParentOrNull(), c);
            getParentOrNull().replaceChild(this, newNode);
            return newNode.child;
        }

        @Override
        public String toString() {
            return toStringBase();
        }
    }

    public static class SingleChild implements ITrieNode {
        private final char c;
        private final ITrieNode parentOrNull;
        private ITrieNode child;

        @Override
        public char getChar() {
            return c;
        }

        @Override
        public ITrieNode getParentOrNull() {
            return parentOrNull;
        }

        @Override
        public ITrieNode[] getChildrenSorted() {
            return new ITrieNode[]{child};
        }

        @Override
        public ITrieNode getChildOrNull(char c) {
            return this.child.getChar() == c ? this.child : null;
        }

        @Override
        public void replaceChild(ITrieNode oldChild, ITrieNode newChild) {
            this.child = newChild;
        }

        public SingleChild(char c, ITrieNode parentOrNull, ITrieNode child) {
            this.c = c;
            this.parentOrNull = parentOrNull;
            this.child = child;
        }

        public SingleChild(char c, ITrieNode parentOrNull, char c2) {
            this.c = c;
            this.parentOrNull = parentOrNull;
            this.child = new EndNode(c2, this);
        }

        @Override
        public ITrieNode createChild(char c) {
            MultiChild newNode = new MultiChild(getChar(), getParentOrNull());
            EndNode created = new EndNode(c, newNode);
            newNode.initUnordered(this.child, created);
            getParentOrNull().replaceChild(this, newNode);
            return created;
        }

        @Override
        public String toString() {
            return toStringBase();
        }
    }

    public static class MultiChild implements ITrieNode {
        private final char c;
        private final ITrieNode parentOrNull;
        private ITrieNode[] childrenSorted;

        @Override
        public char getChar() {
            return c;
        }

        @Override
        public ITrieNode getChildOrNull(char c) {
            int index = binarySearch(childrenSorted, c);
            if (index < 0) {
                return null;
            } else {
                return childrenSorted[index];
            }
        }

        @Override
        public ITrieNode[] getChildrenSorted() {
            return childrenSorted;
        }

        @Override
        public void replaceChild(ITrieNode oldChild, ITrieNode newChild) {
            int index = binarySearch(childrenSorted, oldChild.getChar());
            if (index >= 0) {
                childrenSorted[index] = newChild;
            } else {
                throw new IllegalArgumentException("Child not found");
            }
        }

        @Override
        public ITrieNode getParentOrNull() {
            return parentOrNull;
        }

        public MultiChild(char c, ITrieNode parentOrNull, ITrieNode[] childrenSorted) {
            this.c = c;
            this.parentOrNull = parentOrNull;
            this.childrenSorted = childrenSorted;
        }

        public MultiChild(char c, ITrieNode parentOrNull) {
            this.c = c;
            this.parentOrNull = parentOrNull;
        }

        protected void initUnordered(ITrieNode child1, ITrieNode child2) {
            this.childrenSorted = new ITrieNode[2];
            if (child1.getChar() < child2.getChar()) {
                this.childrenSorted[0] = child1;
                this.childrenSorted[1] = child2;
            } else {
                this.childrenSorted[1] = child1;
                this.childrenSorted[0] = child2;
            }
        }

        @Override
        public ITrieNode createChild(char c) {
            int index = binarySearch(childrenSorted, c);
            if (index < 0) {
                index = -(index + 1);
                ITrieNode[] newChildren = new ITrieNode[childrenSorted.length + 1];
                System.arraycopy(childrenSorted, 0, newChildren, 0, index);
                TrieStringList.EndNode node = new EndNode(c, this);
                newChildren[index] = node;
                System.arraycopy(childrenSorted, index, newChildren, index + 1, childrenSorted.length - index);
                childrenSorted = newChildren;
                return node;
            }
            throw new IllegalArgumentException("Child already exists");
        }

        public static int binarySearch(ITrieNode[] array, char c) {
            int low = 0;
            int high = array.length - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                char midVal = array[mid].getChar();
                if (midVal < c) {
                    low = mid + 1;
                } else if (midVal > c) {
                    high = mid - 1;
                } else {
                    return mid; // key found
                }
            }
            return -(low + 1); // key not found
        }

        @Override
        public String toString() {
            return toStringBase();
        }
    }

    @Override
    public String get(int index) {
        Object strOrTrie = optimized.get(index);
        if (strOrTrie instanceof String str) {
            return str;
        } else if (strOrTrie instanceof ITrieNode node) {
            return node.toFull();
        } else {
            throw new IllegalArgumentException("Unknown type");
        }
    }

    @Override
    public int size() {
        return optimized.size();
    }

    @Override
    public void finishLoad() {

    }
}