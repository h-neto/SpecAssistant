package pt.haslab.specassistant.util;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface Static {
    static <K, V, R> Map<K, R> mapValues(Map<K, V> target, Function<V, R> mapping) {
        return target.entrySet().stream().map(x -> mapValue(x, mapping)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    static <K, V, R> Map<R, V> mapKeys(Map<K, V> target, Function<K, R> mapping) {
        return target.entrySet().stream().map(x -> mapKey(x, mapping)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    static <K, V, R> Map.Entry<K, R> mapValue(Map.Entry<K, V> target, Function<V, R> mapping) {
        return Map.entry(target.getKey(), mapping.apply(target.getValue()));
    }

    static <K, V, R> Map.Entry<R, V> mapKey(Map.Entry<K, V> target, Function<K, R> mapping) {
        return Map.entry(mapping.apply(target.getKey()), target.getValue());
    }

    static <K, V> List<Map<K, V>> getCombinations(Map<K, V> filler, List<Map.Entry<K, List<V>>> toArrange) {
        int[] counts = new int[toArrange.size()];
        int[] factorization = new int[toArrange.size()];
        factorization[0] = 1;
        counts[0] = toArrange.get(0).getValue().size();
        for (int i = 1; i < toArrange.size(); i++) {
            counts[i] = toArrange.get(i).getValue().size();
            factorization[i] = factorization[i - 1] * counts[i - 1];
        }
        int arrangement_count = Arrays.stream(counts).reduce(1, (x, y) -> x * y);

        List<Map<K, V>> result = new ArrayList<>();

        for (int i = 0; i < arrangement_count; i++) {
            Map<K, V> current = new HashMap<>(filler);
            for (int j = 0; j < toArrange.size(); j++) {
                Map.Entry<K, List<V>> entry = toArrange.get(j);
                current.put(entry.getKey(), entry.getValue().get((i / factorization[j]) % counts[j]));
            }
            result.add(current);
        }
        return result;
    }

    static <R> void mergeFutures(Collection<CompletableFuture<R>> futures, Consumer<R> process) {
        for (CompletableFuture<R> future : futures) {
            try {
                process.accept(future.get());
            } catch (ExecutionException e) {
                try {
                    throw new RuntimeException(e.getCause());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static <K, V> Map<K, V> mergeFutureEntries(Collection<CompletableFuture<Map.Entry<K, V>>> futures) {
        return mergeFutureEntries(futures, (a, b) -> b);
    }

    static <K, V> Map<K, V> mergeFutureEntries(Collection<CompletableFuture<Map.Entry<K, V>>> futures, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        Map<K, V> result = new HashMap<>();
        mergeFutures(futures, entry -> result.merge(entry.getKey(), entry.getValue(), remappingFunction));
        return result;
    }
}
