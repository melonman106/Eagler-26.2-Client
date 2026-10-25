package net.fabricmc.loader.api;

public final class SimpleVersion implements Version {
    private final String value;
    public SimpleVersion(String value) { this.value = value == null ? "unknown" : value; }
    @Override public String getFriendlyString() { return value; }
    @Override public String toString() { return value; }
}
