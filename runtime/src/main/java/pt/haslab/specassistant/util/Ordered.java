package pt.haslab.specassistant.util;

public interface Ordered<T> extends Comparable<T> {

    static <T extends Comparable<? super T>> T min(T a, T b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    static <T extends Comparable<? super T>> T max(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    default boolean isLessThan(T other) {
        return compareTo(other) < 0;
    }

    default boolean isLessOrEqualTo(T other) {
        return compareTo(other) <= 0;
    }

    default boolean isGreaterThan(T other) {
        return compareTo(other) > 0;
    }

    default boolean isGreaterOrEqualTo(T other) {
        return compareTo(other) >= 0;
    }

}