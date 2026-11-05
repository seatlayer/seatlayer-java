package io.seatlayer;

/**
 * Filters and paging for {@link Events#list}.
 *
 * @param workspaceId the workspace to list, or {@code null} for the caller's
 * @param externalRef only the event with this caller-owned reference, or {@code null}
 * @param limit the page size, or {@code null} for the default
 * @param cursor the cursor from the previous page, or {@code null} to start
 * @param counts whether to include live availability counts
 * @param seriesKey only the dates of this event series, or {@code null}
 */
public record EventListOptions(
        String workspaceId, String externalRef, Integer limit, String cursor, boolean counts,
        String seriesKey) {

    /**
     * The options without a series filter, as before {@code seriesKey} was added.
     *
     * @param workspaceId the workspace to list, or {@code null} for the caller's
     * @param externalRef only the event with this caller-owned reference, or {@code null}
     * @param limit the page size, or {@code null} for the default
     * @param cursor the cursor from the previous page, or {@code null} to start
     * @param counts whether to include live availability counts
     */
    public EventListOptions(
            String workspaceId, String externalRef, Integer limit, String cursor, boolean counts) {
        this(workspaceId, externalRef, limit, cursor, counts, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    EventListOptions withCursor(String next) {
        return new EventListOptions(workspaceId, externalRef, limit, next, counts, seriesKey);
    }

    public static final class Builder {
        private String workspaceId;
        private String externalRef;
        private Integer limit;
        private String cursor;
        // Most callers of a single page want counts; listAll flips this off.
        private boolean counts = true;
        private String seriesKey;

        public Builder workspaceId(String value) {
            this.workspaceId = value;
            return this;
        }

        public Builder externalRef(String value) {
            this.externalRef = value;
            return this;
        }

        /** Page size. Clamped server-side; asking for more is not an error. */
        public Builder limit(Integer value) {
            this.limit = value;
            return this;
        }

        public Builder cursor(String value) {
            this.cursor = value;
            return this;
        }

        /** Include live availability counts. One server round-trip per event. */
        public Builder counts(boolean value) {
            this.counts = value;
            return this;
        }

        /**
         * Lists only the dates of one event series.
         *
         * @param value the event series key
         * @return this builder
         */
        public Builder seriesKey(String value) {
            this.seriesKey = value;
            return this;
        }

        public EventListOptions build() {
            return new EventListOptions(workspaceId, externalRef, limit, cursor, counts, seriesKey);
        }
    }
}
