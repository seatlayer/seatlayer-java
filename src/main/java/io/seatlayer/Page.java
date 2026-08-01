package io.seatlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * One page of a list endpoint, plus the cursor for the next.
 *
 * @param items the rows on this page
 * @param nextCursor empty once the list is exhausted
 */
public record Page<T>(List<T> items, Optional<String> nextCursor) {

    @SuppressWarnings("unchecked")
    static Page<Map<String, Object>> of(Map<String, Object> response, String key) {
        List<Map<String, Object>> items = response.get(key) instanceof List<?> list
                ? (List<Map<String, Object>>) (List<?>) list
                : List.of();
        return new Page<>(items, SeatLayerHttpClient.stringAt(response, "nextCursor"));
    }

    /**
     * Walk every page lazily.
     *
     * <p>An {@link Iterable} rather than a materialised {@code List}: the point of
     * paginating was to stop holding an unbounded result set in memory, and returning a
     * list would hand that problem straight back to the caller.
     */
    static <T> Iterable<T> paginate(Function<String, Page<T>> fetch) {
        return () -> new Iterator<>() {
            private Iterator<T> current = List.<T>of().iterator();
            private String cursor = null;
            private boolean exhausted = false;

            @Override
            public boolean hasNext() {
                while (!current.hasNext() && !exhausted) {
                    Page<T> page = fetch.apply(cursor);
                    current = new ArrayList<>(page.items()).iterator();
                    cursor = page.nextCursor().orElse(null);
                    // Absent cursor terminates, so a caller looping cannot spin forever.
                    exhausted = cursor == null;
                }
                return current.hasNext();
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                return current.next();
            }
        };
    }
}
