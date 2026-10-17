package dev.marbles.opah;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

import java.util.function.Consumer;

/** Particle toolkit: rings, spheres, helixes, jagged bolts, colored dust. Respects particles.enabled / particles.density. */
final class Fx {
    private Fx() {}

    static boolean on() { return MarblesPlugin.I.getConfig().getBoolean("particles.enabled", true); }
    static double dens() { return Math.min(2.0, Math.max(0.05, MarblesPlugin.I.getConfig().getDouble("particles.density", 1.0))); }
    static int n(int c) { return Math.max(1, (int) Math.round(c * dens())); }
    static boolean roll() { return Math.random() < dens(); }

    static void p(Location l, Particle part, int c, double dx, double dy, double dz, double sp) {
        if (on()) l.getWorld().spawnParticle(part, l, n(c), dx, dy, dz, sp);
    }

    // ---- point painters ----
    static Consumer<Location> pt(Particle part) { return pt(part, 1, 0, 0); }
    static Consumer<Location> pt(Particle part, int c, double spread, double sp) {
        return l -> { if (on()) l.getWorld().spawnParticle(part, l, c, spread, spread, spread, sp); };
    }
    static Consumer<Location> dust(Color c, float size) {
        return l -> { if (on()) l.getWorld().spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, new Particle.DustOptions(c, size)); };
    }
    static Consumer<Location> trans(Color a, Color b, float size) {
        return l -> { if (on()) l.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0, new Particle.DustTransition(a, b, size)); };
    }
    static Consumer<Location> block(Material m) {
        BlockData d = m.createBlockData();
        return l -> { if (on()) l.getWorld().spawnParticle(Particle.BLOCK, l, 2, 0.15, 0.1, 0.15, d); };
    }
    @SafeVarargs
    static Consumer<Location> both(Consumer<Location>... fs) {
        return l -> { for (Consumer<Location> f : fs) f.accept(l); };
    }
    static Consumer<Location> sometimes(double chance, Consumer<Location> f) {
        return l -> { if (Math.random() < chance) f.accept(l); };
    }

    // ---- shapes ----
    static void ring(Location c, double r, int pts, Consumer<Location> f) {
        int k = n(pts);
        for (int i = 0; i < k; i++) {
            double a = 2 * Math.PI * i / k;
            f.accept(new Location(c.getWorld(), c.getX() + Math.cos(a) * r, c.getY(), c.getZ() + Math.sin(a) * r));
        }
    }

    static void sphere(Location c, double r, int pts, Consumer<Location> f) {
        int k = n(pts); double g = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < k; i++) {
            double y = 1 - 2.0 * (i + 0.5) / k, rad = Math.sqrt(1 - y * y), th = g * i;
            f.accept(new Location(c.getWorld(), c.getX() + Math.cos(th) * rad * r, c.getY() + y * r, c.getZ() + Math.sin(th) * rad * r));
        }
    }

    static void helix(Location base, double r, double h, int pts, double turns, double phase, Consumer<Location> f) {
        int k = n(pts);
        for (int i = 0; i < k; i++) {
            double t = (double) i / k, a = phase + t * turns * 2 * Math.PI;
            f.accept(new Location(base.getWorld(), base.getX() + Math.cos(a) * r, base.getY() + t * h, base.getZ() + Math.sin(a) * r));
        }
    }

    static void column(Location base, double h, double step, Consumer<Location> f) {
        for (double y = 0; y <= h; y += step) f.accept(base.clone().add(0, y, 0));
    }

    static void line(Location a, Location b, double step, Consumer<Location> f) {
        Vector d = b.toVector().subtract(a.toVector()); double len = d.length();
        if (len < 0.01) return;
        d.normalize();
        for (double i = 0; i <= len; i += step) f.accept(a.clone().add(d.clone().multiply(i)));
    }

    /** Jagged lightning bolt from a to b. */
    static void bolt(Location a, Location b, Consumer<Location> f) {
        Vector d = b.toVector().subtract(a.toVector());
        int segs = Math.max(3, (int) (d.length() / 1.2));
        Location prev = a.clone();
        for (int i = 1; i <= segs; i++) {
            Location next = a.clone().add(d.clone().multiply((double) i / segs));
            if (i < segs) next.add((Math.random() - .5) * 1.3, (Math.random() - .5) * 1.3, (Math.random() - .5) * 1.3);
            line(prev, next, 0.25, f);
            prev = next;
        }
    }

    /** Directional burst: particles fly outward in all directions from c. */
    static void radial(Location c, Particle part, int pts, double speed) {
        if (!on()) return;
        int k = n(pts); double g = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < k; i++) {
            double y = 1 - 2.0 * (i + 0.5) / k, rad = Math.sqrt(1 - y * y), th = g * i;
            c.getWorld().spawnParticle(part, c, 0, Math.cos(th) * rad, y, Math.sin(th) * rad, speed);
        }
    }

    /** Upward spray of directional particles. */
    static void fountain(Location c, Particle part, int count, double spread, double speed) {
        if (!on()) return;
        for (int i = 0; i < n(count); i++)
            c.getWorld().spawnParticle(part, c, 0, (Math.random() - .5) * spread, 1, (Math.random() - .5) * spread, speed);
    }
}
