package link.locutus.discord.apiv3.csv.file;

public interface ITrieNode {
    char getChar();
    ITrieNode getParentOrNull();
    ITrieNode[] getChildrenSorted();
    ITrieNode getChildOrNull(char c);
    void replaceChild(ITrieNode oldChild, ITrieNode newChild);
    ITrieNode createChild(char c);

    default String toFull() {
        StringBuilder sb = new StringBuilder();
        ITrieNode current = this;
        while (current.getParentOrNull() != null) {
            sb.insert(0, current.getChar());
            current = current.getParentOrNull();
        }
        return sb.toString();
    }

    default String toStringBase() {
        String r = (getParentOrNull() == null ? "@" : "") + getClass().getSimpleName() + ":" + getChar();
        ITrieNode[] children = getChildrenSorted();
        if (children.length == 0) return r;
        return r + "(" + children.length + ")";
    }
}
