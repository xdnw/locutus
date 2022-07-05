package link.locutus.discord.util.battle.sim;

import com.fasterxml.jackson.databind.MappingIterator;
import it.unimi.dsi.fastutil.bytes.ByteOpenHashSet;
import it.unimi.dsi.fastutil.bytes.ByteSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashBigSet;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AttackTypeNode {
    public static abstract class AttackTypeNodeComparator {
        public abstract boolean equals(AttackTypeNode a, AttackTypeNode b);
        public int hashCode(AttackTypeNode node) {
            if (node.parent != null) return node.parent.hashcode + node.type.hashCode();
            return node.type.hashCode();
        }

        public String toStringUnordered(AttackTypeNode node) {
            return StringMan.getString(node.getPathMap());
        }

        public String toStringOrdered(AttackTypeNode node) {
            return StringMan.getString(node.getPath());
        }

        public String toString(AttackTypeNode node) {
            return toStringUnordered(node);
        }
    }

    public final AttackTypeNodeComparator comparator;
    public final AttackTypeNode parent;
    public final AttackType type;
    public final int map;
    public final int resistance;
    //        int depth;
//        byte[] attackMapCache;
    public final int hashcode;

    public AttackTypeNode(AttackType type, AttackTypeNodeComparator comparator) {
        this.parent = null;
        this.type = type;
        this.map = type.getMapUsed();
        this.resistance = type.getResistanceIT();
        this.comparator = comparator;
        this.hashcode = comparator.hashCode(this);
    }

    public AttackTypeNode(AttackTypeNode parent, AttackType type) {
        this.parent = parent;
        this.type = type;
        this.map = parent.map + type.getMapUsed();
        this.resistance = parent.resistance + type.getResistanceIT();
//            this.depth = parent.depth++;
        this.comparator = parent.comparator;
        this.hashcode = comparator.hashCode(this);
    }

    public AttackTypeNode branch(AttackType type) {
        return new AttackTypeNode(this, type);
    }

    public Map<AttackType, Long> getPathMap() {
        return getPath().stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    public List<AttackType> getPath() {
        return getPathNodes().stream().map(f -> f.type).collect(Collectors.toList());
    }

    public List<AttackTypeNode> getPathNodes() {
        List<AttackTypeNode> output = new ArrayList<>();
        AttackTypeNode root = this;
        do {
            output.add(root);
            root = root.parent;
        } while (root != null);
        return output;
    }

    @Override
    public int hashCode() {
        return hashcode;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AttackTypeNode)) return false;
        AttackTypeNode other = (AttackTypeNode) obj;
        return comparator.equals(this, other);
    }

    public AttackTypeNode getRoot() {
        AttackTypeNode root = this;
        while (root.parent != null) {
            root = root.parent;
        }
        return root;
    }

    @Override
    public String toString() {
        return comparator.toString(this);
    }

    public static AttackTypeNodeComparator ORDERED = new AttackTypeNodeComparator() {
        @Override
        public boolean equals(AttackTypeNode a, AttackTypeNode b) {
            if (a.hashcode != b.hashcode) return false;
            if (a.map != b.map) return false;
            if (a.resistance != b.resistance) return false;
            AttackTypeNode root1 = a;
            AttackTypeNode root2 = b;
            do {
                if (root1.type != root2.type) return false;
                root1 = root1.parent;
                root2 = root2.parent;
                if ((root1 == null) != (root2 == null)) return false;
            } while (root1 != null);
            return true;
        }

        @Override
        public int hashCode(AttackTypeNode node) {
            if (node.parent != null) {
                return node.type.ordinal() + 31 * node.parent.hashcode;
            }
            return node.type.ordinal();
        }

        @Override
        public String toString(AttackTypeNode node) {
            return toStringOrdered(node);
        }
    };

    public static AttackTypeNodeComparator UNORDERED = new AttackTypeNodeComparator() {
        @Override
        public boolean equals(AttackTypeNode a, AttackTypeNode b) {
            if (a.hashcode != b.hashcode) return false;
            if (a.map != b.map) return false;
            if (a.resistance != b.resistance) return false;
            byte[] diff = new byte[AttackType.values.length];
            AttackTypeNode root1 = a;
            AttackTypeNode root2 = b;
            do {
                diff[root1.type.ordinal()]++;
                diff[root2.type.ordinal()]--;
                root1 = root1.parent;
                root2 = root2.parent;
                if ((root1 == null) != (root2 == null)) return false;
            } while (root1 != null);
            for (int i = 0; i < diff.length; i++) {
                if (diff[i] != 0) return false;
            }
            return true;
        }
    };

    public static AttackTypeNodeComparator UNORDERED(Map<AttackType, Integer> attackOptions) {
        return new AttackTypeNodeComparator() {
            @Override
            public boolean equals(AttackTypeNode a, AttackTypeNode b) {
                if (a.hashcode != b.hashcode) return false;
                if (a.map != b.map) return false;
                if (a.resistance != b.resistance) return false;
                byte[] diff = new byte[attackOptions.size()];
                AttackTypeNode root1 = a;
                AttackTypeNode root2 = b;
                do {
                    diff[attackOptions.get(root1.type)]++;
                    diff[attackOptions.get(root2.type)]--;
                    root1 = root1.parent;
                    root2 = root2.parent;
                    if ((root1 == null) != (root2 == null)) return false;
                } while (root1 != null);
                for (int i = 0; i < diff.length; i++) {
                    if (diff[i] != 0) return false;
                }
                return true;
            }
        };
    }

    public static AttackTypeNodeComparator MAP_RES_HASH = new AttackTypeNodeComparator() {
        @Override
        public boolean equals(AttackTypeNode a, AttackTypeNode b) {
            if (a.hashcode != b.hashcode) return false;
            if (a.map != b.map) return false;
            if (a.resistance != b.resistance) return false;
            return true;
        }
    };

    public static AttackTypeNodeComparator MAP_RES = new AttackTypeNodeComparator() {
        @Override
        public int hashCode(AttackTypeNode node) {
            return (node.map << 16) | (node.resistance & 0xFFFF);
        }

        @Override
        public boolean equals(AttackTypeNode a, AttackTypeNode b) {
            if (a.map != b.map) return false;
            if (a.resistance != b.resistance) return false;
            return true;
        }
    };

    public static AttackTypeNode findQuickest(List<AttackType> allowedAttacks, int resistance) {
        ArrayList<AttackType> types = new ArrayList<>(allowedAttacks);
        types.removeIf(f -> f.getResistanceIT() <= 0);

        Map<Map.Entry<Integer, Integer>, List<AttackType>> typesByMapResistance = new HashMap<>();
        for (AttackType type : types) {
            Map.Entry<Integer, Integer> mapRes = Map.entry(type.getMapUsed(), type.getResistanceIT());
            typesByMapResistance.computeIfAbsent(mapRes, f -> new ArrayList<>()).add(type);
        }

        // remove attack types that use more MAP per resistance (e.g. nuke will always be worse than ground, if ground is available)
        List<AttackType> useless = new ArrayList<>();
        for (AttackType type : types) {
            for (Map.Entry<Integer, Integer> mapRes : typesByMapResistance.keySet()) {
                int amt = type.getMapUsed() / mapRes.getKey();
                if (amt * mapRes.getValue() > type.getResistanceIT()) {
                    useless.add(type);
                    break;
                }
            }
        }
        types.removeAll(useless);

        Map<AttackType, Integer> attackOptions = new EnumMap<>(AttackType.class);
        for (int i = 0; i < types.size(); i++) attackOptions.put(types.get(i), i);
        AttackTypeNodeComparator comparator = UNORDERED(attackOptions);

        Predicate<AttackTypeNode> goalFunc = f -> f.resistance >= resistance;
        Function<AttackTypeNode, Integer> valueFunc = f -> -f.map;

        return findGoal(comparator, types, goalFunc, valueFunc, 1000);
    }

    public static AttackTypeNode findGoal(AttackTypeNodeComparator comparator, List<AttackType> types, Predicate<AttackTypeNode> goalFunc, Function<AttackTypeNode, Integer> valueFunc, long timeout) {
        ObjectArrayFIFOQueue<AttackTypeNode> queue = new ObjectArrayFIFOQueue<>();
        Set<AttackTypeNode> visited = new ObjectOpenHashBigSet<>();

        AttackTypeNode bestGoal = null;
        int bestGoalVal = Integer.MIN_VALUE;
        int numChecked = 0;

        for (AttackType type : types) {
            AttackTypeNode next = new AttackTypeNode(type, comparator);
            if (goalFunc.test(next)) {
                int value = valueFunc.apply(next);
                if (value > bestGoalVal) {
                    bestGoalVal = value;
                    bestGoal = next;
                }
                continue;
            }
            queue.enqueue(next);
            visited.add(next);
        }

        long start = System.currentTimeMillis();

        while (!queue.isEmpty()) {
            numChecked++;
            AttackTypeNode root = queue.dequeue();
            for (AttackType type : types) {
                AttackTypeNode next = root.branch(type);
                if (goalFunc.test(next)) {
                    int value = valueFunc.apply(next);
                    if (value > bestGoalVal) {
                        bestGoalVal = value;
                        bestGoal = next;
                    }
                    continue;
                }
                if (!visited.add(next)) continue;

                queue.enqueue(next);
            }
            if ((numChecked & 0xFFFF) == 0) {
                long now = System.currentTimeMillis();
                long diff = now - start;
                if (diff > timeout) {
                    StringBuilder response = new StringBuilder("Took too long");
                    response.append("\nNum checked: " + numChecked);
                    response.append("\nQueue size: " + queue.size());
                    response.append("\nDiff: " + diff);
                    throw new IllegalStateException(response.toString());
                }
            }
        }

        return bestGoal;
    }
}
