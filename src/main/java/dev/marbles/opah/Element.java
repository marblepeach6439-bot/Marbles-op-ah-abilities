package dev.marbles.opah;

public enum Element {
    FIRE("Fire", "Inferno", "Flame Dash"),
    WATER("Water", "Tsunami", "Whirlpool"),
    LIGHTNING("Lightning", "Thunderbolt", "Chain Lightning"),
    EARTH("Earth", "Earthquake", "Stone Prison"),
    ICE("Ice", "Glacial Burst", "Piercing Icicle"),
    WIND("Wind", "Cyclone", "Gale Strike"),
    VOID("Void", "Event Horizon", "Riftwalk"),
    NATURE("Nature", "Worldroot", "Rebirth"),
    LIGHT("Light", "Divine Smite", "Flashbang"),
    MAGMA("Magma", "Eruption", "Molten Prison");

    public final String display, a1, a2;

    Element(String display, String a1, String a2) { this.display = display; this.a1 = a1; this.a2 = a2; }

    public String ability(int slot) { return slot == 0 ? a1 : a2; }
    public String key() { return name().toLowerCase(); }
    /** config path (under "abilities.") of an ability, e.g. fire.flame-dash */
    public String path(int slot) { return key() + "." + ability(slot).toLowerCase().replace(' ', '-'); }
    public String passivePath() { return key() + ".passive"; }
    public int cooldown(int slot) { return (int) Abilities.v(path(slot) + ".cooldown"); }

    public static Element parse(String s) {
        for (Element e : values()) if (e.name().equalsIgnoreCase(s)) return e;
        return null;
    }
}
