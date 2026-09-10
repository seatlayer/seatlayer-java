package io.seatlayer;

/** Provider-neutral Event inventory placement preferences. */
public enum EventHostingRegion {
    WESTERN_EUROPE("western-europe"),
    EASTERN_EUROPE("eastern-europe"),
    NORTH_AMERICA_EAST("north-america-east"),
    NORTH_AMERICA_WEST("north-america-west"),
    SOUTH_AMERICA("south-america"),
    ASIA_PACIFIC("asia-pacific"),
    NORTHEAST_ASIA("northeast-asia"),
    SOUTHEAST_ASIA("southeast-asia"),
    OCEANIA("oceania"),
    AFRICA("africa"),
    MIDDLE_EAST("middle-east");

    private final String value;

    EventHostingRegion(String value) { this.value = value; }

    public String value() { return value; }
}
