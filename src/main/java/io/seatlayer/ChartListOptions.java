package io.seatlayer;

/** Filters and paging for {@link Charts#list}. */
public record ChartListOptions(
        String workspaceId, String externalRef, boolean archived, Integer limit, String cursor) {

    public static Builder builder() {
        return new Builder();
    }

    ChartListOptions withCursor(String next) {
        return new ChartListOptions(workspaceId, externalRef, archived, limit, next);
    }

    /** Builder, because five nullable constructor arguments is a bug waiting to happen. */
    public static final class Builder {
        private String workspaceId;
        private String externalRef;
        private boolean archived;
        private Integer limit;
        private String cursor;

        public Builder workspaceId(String value) {
            this.workspaceId = value;
            return this;
        }

        public Builder externalRef(String value) {
            this.externalRef = value;
            return this;
        }

        public Builder archived(boolean value) {
            this.archived = value;
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

        public ChartListOptions build() {
            return new ChartListOptions(workspaceId, externalRef, archived, limit, cursor);
        }
    }
}
