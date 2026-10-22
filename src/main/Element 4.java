package dev.marbles.opah;

import java.util.ArrayList;
import java.util.List;

public enum Element {
    FIRE("Fire", "Flame Dash", "Inferno"),
    WATER("Water", "Tsunami", "Whirlpool"),
    LIGHTNING("Lightning", "Thunderbolt", "Chain Lightning"),
    EARTH("Earth", "Earthquake", "Stone Prison"),
    ICE("Ice", "Glacial Burst", "Piercing Icicle"),
    WIND("Wind", "Gale Strike", "Cyclone"),
    VOID("Void", "Riftwalk", "Event Horizon"),
    NATURE("Nature", "Worldroot", "Rebirth"),
    LIGHT("Light", "Divine Smite", "Flashbang"),
    MAGMA("Magma", "Eruption", "Molten Prison"),
    // admin-only: never rolled, never given by rerolls
    DRAGON("Dragon", "Dragon's Descent", "Crystal Chaos", false),
    SCULK("Sculk", "Warden's Wrath", "Warden's Domain", false),
    DEATH("Death", "Reaper's Sentence", "Inevitable Death", false),
    DIVINE("Divine", "Divine Judgement", "Divine Authority", false);

    public final String display, a1, a2;
    public final boolean rollable;

    Element(String display, String a1, String a2) { this(display, a1, a2, true); }
    Element(String display, String a1, String a2, boolean rollable) {
        this.display = display; this.a1 = a1; this.a2 = a2; this.rollable = rollable;
    }

    public String ability(int slot) { return slot == 0 ? a1 : a2; }
    public String key() { return name().toLowerCase(); }
    /** config path (under "abilities.") of an ability, e.g. fire.flame-dash */
    public String path(int slot) { return key() + "." + ability(slot).toLowerCase().replace("'", "").replace(' ', '-'); }
    public String passivePath() { return key() + ".passive"; }

    /** Seconds. Warden's Domain's cooldown only starts after the domain ends, so its duration is added on. */
    public int cooldown(int slot) {
        int c = (int) Abilities.v(path(slot) + ".cooldown");
        if (this == SCULK && slot == 1) c += (int) Abilities.v(path(slot) + ".duration-seconds");
        return c;
    }

    public static List<Element> rollables() {
        List<Element> out = new ArrayList<>();
        for (Element e : values()) if (e.rollable) out.add(e);
        return out;
    }

    public static Element parse(String s) {
        for (Element e : values()) if (e.name().equalsIgnoreCase(s)) return e;
        return null;
    }
}
