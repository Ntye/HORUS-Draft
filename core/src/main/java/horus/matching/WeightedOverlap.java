package horus.matching;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Weighted Jaccard over two multisets, with greedy one-to-one pairing. Exact matches are paired
// first, then "loose" matches (an initial standing for a longer token) among what is left, so an
// exact token is never spent on an initial. Items are sorted first (I-4).
final class WeightedOverlap {

    record Item(String key, double weight) {
    }

    private WeightedOverlap() {
    }

    static double jaccard(List<Item> a, List<Item> b, boolean expandInitials) {
        List<Item> left = sorted(a);
        List<Item> right = sorted(b);
        double sumLeft = left.stream().mapToDouble(Item::weight).sum();
        double sumRight = right.stream().mapToDouble(Item::weight).sum();
        boolean[] usedLeft = new boolean[left.size()];
        boolean[] usedRight = new boolean[right.size()];
        double matched = 0.0;

        matched += pair(left, right, usedLeft, usedRight, false);
        if (expandInitials) {
            matched += pair(left, right, usedLeft, usedRight, true);
        }
        double union = sumLeft + sumRight - matched;
        return union <= 0.0 ? 0.0 : Math.min(1.0, matched / union);
    }

    private static double pair(List<Item> left, List<Item> right, boolean[] usedLeft, boolean[] usedRight,
            boolean loose) {
        double matched = 0.0;
        for (int i = 0; i < left.size(); i++) {
            if (usedLeft[i]) {
                continue;
            }
            for (int j = 0; j < right.size(); j++) {
                if (usedRight[j]) {
                    continue;
                }
                boolean hit = loose
                        ? initialOf(left.get(i).key(), right.get(j).key())
                        : left.get(i).key().equals(right.get(j).key());
                if (hit) {
                    usedLeft[i] = true;
                    usedRight[j] = true;
                    matched += Math.min(left.get(i).weight(), right.get(j).weight());
                    break;
                }
            }
        }
        return matched;
    }

    // "j" stands for "john" (spec §30 A5), in either direction.
    private static boolean initialOf(String x, String y) {
        return (x.length() == 1 && y.length() > 1 && Character.isLetter(x.charAt(0)) && y.startsWith(x))
                || (y.length() == 1 && x.length() > 1 && Character.isLetter(y.charAt(0)) && x.startsWith(y));
    }

    private static List<Item> sorted(List<Item> items) {
        List<Item> copy = new ArrayList<>(items);
        copy.sort(Comparator.comparing(Item::key).thenComparingDouble(Item::weight));
        return copy;
    }
}
