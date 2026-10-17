package dev.marbles.opah;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/** Extra element-themed particle layers: cast sigil + aura on every ability, and a burst on every hit. */
final class Flair {
    private Flair() {}

    record Theme(Color a, Color b, Particle main, Particle spark) {}

    static Theme theme(Element el) {
        return switch (el) {
            case FIRE -> new Theme(Abilities.ORANGE, Abilities.DARKRED, Particle.FLAME, Particle.SMALL_FLAME);
            case WATER -> new Theme(Abilities.AQUA, Abilities.BLUE, Particle.SPLASH, Particle.BUBBLE_POP);
            case LIGHTNING -> new Theme(Abilities.YELLOW, Abilities.VIOLET, Particle.ELECTRIC_SPARK, Particle.END_ROD);
            case EARTH -> new Theme(Abilities.BROWN, Color.fromRGB(60, 40, 25), Particle.ASH, Particle.CRIT);
            case ICE -> new Theme(Abilities.ICE, Abilities.AQUA, Particle.SNOWFLAKE, Particle.END_ROD);
            case WIND -> new Theme(Abilities.GRAY, Abilities.WHITE, Particle.CLOUD, Particle.WHITE_ASH);
            case VOID -> new Theme(Abilities.VIOLET, Abilities.BLACK, Particle.PORTAL, Particle.REVERSE_PORTAL);
            case NATURE -> new Theme(Abilities.GREEN, Abilities.LIME, Particle.HAPPY_VILLAGER, Particle.SPORE_BLOSSOM_AIR);
            case LIGHT -> new Theme(Abilities.WHITE, Abilities.GOLD, Particle.END_ROD, Particle.FIREWORK);
            case MAGMA -> new Theme(Abilities.ORANGE, Abilities.DARKRED, Particle.LAVA, Particle.FLAME);
        };
    }

    /** Rotating rune sigil under the caster, a double-helix aura around them, and a radial burst. Ability 2 is bigger. */
    static void cast(Player p, Element el, int slot) {
        if (!Fx.on()) return;
        Theme th = theme(el);
        double big = slot == 0 ? 1.0 : 1.4; int pts = slot == 0 ? 6 : 8; double dir = slot == 0 ? 1 : -1;
        Consumer<Location> dust = Fx.trans(th.a(), th.b(), 1.6f);
        Consumer<Location> spark = Fx.both(Fx.sometimes(0.5, Fx.pt(th.main(), 1, 0.05, 0.02)), Fx.sometimes(0.3, Fx.pt(th.spark(), 1, 0.05, 0.02)));
        Consumer<Location> both = Fx.both(dust, spark);
        Location c0 = p.getLocation().add(0, 1, 0);
        Fx.radial(c0, th.main(), (int) (90 * big), 0.5);
        Fx.radial(c0, th.spark(), (int) (60 * big), 0.3);
        Fx.sphere(c0, 1.6 * big, (int) (50 * big), dust);
        Abilities.zone(24, t -> {
            if (!p.isOnline()) return;
            Location feet = p.getLocation().add(0, 0.1, 0);
            double rot = t * 0.12 * dir, R = 2.3 * big;
            if (t % 2 == 0) {
                for (int i = 1; i <= 3; i++) Fx.ring(feet, (0.8 + i * 0.55) * big, 10 + i * 7, both);
                for (int i = 0; i < pts; i++) {
                    double a1 = rot + 2 * Math.PI * i / pts, a2 = rot + 2 * Math.PI * ((i + 2) % pts) / pts;
                    Fx.line(feet.clone().add(Math.cos(a1) * R, 0, Math.sin(a1) * R), feet.clone().add(Math.cos(a2) * R, 0, Math.sin(a2) * R), 0.4, dust);
                }
            }
            if (t % 3 == 0) for (int i = 0; i < pts; i++) {
                double a = rot + 2 * Math.PI * i / pts;
                Fx.column(feet.clone().add(Math.cos(a) * R, 0, Math.sin(a) * R), 0.8 + (t % 6) * 0.2, 0.3, spark);
            }
            Fx.helix(p.getLocation(), 1.1 * big, 2.6, 18, 1.5, t * 0.5, dust);
            Fx.helix(p.getLocation(), 1.1 * big, 2.6, 18, 1.5, t * 0.5 + Math.PI, spark);
            if (t % 4 == 0) Fx.p(p.getLocation().add(0, 2.8, 0), th.spark(), 12, 0.5, 0.2, 0.5, 0.05);
        }, () -> { if (p.isOnline()) Fx.radial(p.getLocation().add(0, 1, 0), th.spark(), 60, 0.6); });
    }

    /** Themed burst on every enemy an ability damages. */
    static void hit(Player p, Location hc) {
        if (!Fx.on()) return;
        Element el = MarblesPlugin.I.element(p);
        if (el == null) return;
        Theme th = theme(el);
        Consumer<Location> dust = Fx.trans(th.a(), th.b(), 1.4f);
        Fx.ring(hc, 0.9, 18, dust);
        Fx.ring(hc.clone().add(0, 0.6, 0), 0.7, 12, dust);
        Fx.radial(hc, th.spark(), 30, 0.35);
        Fx.p(hc, th.main(), 12, 0.4, 0.5, 0.4, 0.05);
    }
}
