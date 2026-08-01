package io.seatlayer;

/** Filters and paging for {@link Events#list}. */
public record EventListOptions(
        String workspaceId, String externalRef, Integer limit, String cursor, boolean counts) {

    public static Builder builder() {
        return new Builder();
    }

    EventListOptions withCursor(String next) {
        return new EventListOptions(workspaceId, externalRef, limit, next, counts);
    }

    public static final class Builder {
        private String workspaceId;
        private String externalRef;
        private Integer limit;
        private String cursor;
        // Most callers of a single page want counts; listAll flips this off.
        private boolean counts = true;

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

        public EventListOptions build() {
            return new EventListOptions(workspaceId, externalRef, limit, cursor, counts);
        }
    }
}
