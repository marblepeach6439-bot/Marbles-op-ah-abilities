package dev.marbles.opah;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

final class Abilities {
    private Abilities() {}

    // ---------- config access ----------
    static MarblesPlugin pl() { return MarblesPlugin.I; }
    static double v(String k) { return pl().getConfig().getDouble("abilities." + k); }
    static int ticks(String k) { return (int) Math.round(v(k) * 20); }
    static String str(String k) { return pl().getConfig().getString("abilities." + k, ""); }
    static double cap() { return pl().getConfig().getDouble("damage-cap-hearts", 4) * 2; }

    static final class Ctx {
        final Player p; final Map<UUID, Double> dealt = new HashMap<>();
        Ctx(Player p) { this.p = p; }
    }

    static final Set<PotionEffectType> POS = Set.of(PotionEffectType.SPEED, PotionEffectType.HASTE, PotionEffectType.STRENGTH,
            PotionEffectType.JUMP_BOOST, PotionEffectType.REGENERATION, PotionEffectType.RESISTANCE, PotionEffectType.FIRE_RESISTANCE,
            PotionEffectType.WATER_BREATHING, PotionEffectType.INVISIBILITY, PotionEffectType.NIGHT_VISION, PotionEffectType.HEALTH_BOOST,
            PotionEffectType.ABSORPTION, PotionEffectType.SATURATION, PotionEffectType.SLOW_FALLING, PotionEffectType.CONDUIT_POWER,
            PotionEffectType.DOLPHINS_GRACE, PotionEffectType.LUCK, PotionEffectType.HERO_OF_THE_VILLAGE, PotionEffectType.INSTANT_HEALTH);
    static final Set<PotionEffectType> NEG = Set.of(PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE, PotionEffectType.NAUSEA,
            PotionEffectType.BLINDNESS, PotionEffectType.HUNGER, PotionEffectType.WEAKNESS, PotionEffectType.POISON, PotionEffectType.WITHER,
            PotionEffectType.LEVITATION, PotionEffectType.UNLUCK, PotionEffectType.DARKNESS, PotionEffectType.BAD_OMEN);

    static final Color ORANGE = Color.fromRGB(255, 140, 30), RED = Color.fromRGB(220, 40, 20), DARKRED = Color.fromRGB(120, 10, 10),
            AQUA = Color.fromRGB(80, 200, 255), BLUE = Color.fromRGB(30, 90, 230), ICE = Color.fromRGB(190, 240, 255),
            VIOLET = Color.fromRGB(170, 90, 255), PURPLE = Color.fromRGB(110, 30, 170), BLACK = Color.fromRGB(10, 0, 20),
            YELLOW = Color.fromRGB(255, 240, 120), GOLD = Color.fromRGB(255, 215, 80), WHITE = Color.WHITE,
            BROWN = Color.fromRGB(130, 95, 60), GREEN = Color.fromRGB(70, 200, 70), LIME = Color.fromRGB(170, 255, 120),
            GRAY = Color.fromRGB(200, 215, 220);

    // ---------- entry ----------
    static boolean cast(Player p, Element el, int slot) {
        Ctx c = new Ctx(p);
        String k = el.path(slot);
        return switch (el) {
            case FIRE -> slot == 0 ? inferno(c, k) : dash(c, k, Fx2.both(Fx2.trans(ORANGE, RED, 1.4f), Fx2.sometimes(0.4, Fx2.pt(Particle.FLAME, 1, 0.05, 0.02))), Particle.FLAME, false);
            case WATER -> slot == 0 ? tsunami(c, k) : whirlpool(c, k);
            case LIGHTNING -> slot == 0 ? thunderbolt(c, k) : chain(c, k);
            case EARTH -> slot == 0 ? earthquake(c, k) : stonePrison(c, k);
            case ICE -> slot == 0 ? glacialBurst(c, k) : icicle(c, k);
            case WIND -> slot == 0 ? cyclone(c, k) : dash(c, k, Fx2.both(Fx2.dust(GRAY, 1.3f), Fx2.sometimes(0.5, Fx2.pt(Particle.CLOUD, 1, 0.05, 0.01))), Particle.CLOUD, true);
            case VOID -> slot == 0 ? eventHorizon(c, k) : riftwalk(c, k);
            case NATURE -> slot == 0 ? worldroot(c, k) : rebirth(c, k);
            case LIGHT -> slot == 0 ? smite(c, k) : flashbang(c, k);
            case MAGMA -> slot == 0 ? eruption(c, k) : moltenPrison(c, k);
        };
    }

    // ---------- helpers ----------
    static boolean valid(Player p, Entity e) {
        if (!(e instanceof LivingEntity le) || e == p || e instanceof ArmorStand || le.isDead() || !le.isValid()) return false;
        if (e instanceof Player o && (o.getGameMode() == GameMode.SPECTATOR || o.getGameMode() == GameMode.CREATIVE)) return false;
        return true;
    }

    static List<LivingEntity> enemies(Player p, Location center, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, r, r, r))
            if (valid(p, e) && ((LivingEntity) e).getBoundingBox().getCenter().distanceSquared(center.toVector()) <= r * r)
                out.add((LivingEntity) e);
        return out;
    }

    static LivingEntity target(Player p, double range) {
        Location eye = p.getEyeLocation();
        RayTraceResult r = p.getWorld().rayTraceEntities(eye, eye.getDirection(), range, 1.0, e -> valid(p, e));
        if (r == null || r.getHitEntity() == null) return null;
        RayTraceResult b = p.getWorld().rayTraceBlocks(eye, eye.getDirection(), r.getHitPosition().distance(eye.toVector()), FluidCollisionMode.NEVER, true);
        return b != null ? null : (LivingEntity) r.getHitEntity();
    }

    static Location aim(Player p, double range) {
        LivingEntity t = target(p, range);
        if (t != null) return t.getLocation();
        Location eye = p.getEyeLocation();
        RayTraceResult r = p.getWorld().rayTraceBlocks(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true);
        if (r != null) return r.getHitPosition().toLocation(p.getWorld()).add(0, 0.2, 0);
        return eye.clone().add(eye.getDirection().multiply(range));
    }

    /** True damage: ignores armor/Protection, capped per activation per target. */
    static void hurt(Ctx c, LivingEntity t, double hearts) {
        UUID id = t.getUniqueId();
        double done = c.dealt.getOrDefault(id, 0.0);
        double amt = Math.min(hearts * 2, cap() - done);
        if (amt <= 0 || t.isDead()) return;
        c.dealt.put(id, done + amt);
        t.playHurtAnimation(0f);
        Location hc = t.getBoundingBox().getCenter().toLocation(t.getWorld());
        Fx2.p(hc, Particle.CRIT, 12, 0.3, 0.4, 0.3, 0.25);
        Fx2.p(hc, Particle.DAMAGE_INDICATOR, 3, 0.2, 0.3, 0.2, 0);
        Flair.hit(c.p, hc);
        if (t instanceof Mob m) m.setTarget(c.p);
        pl().leech(c.p, amt);
        double nh = t.getHealth() - amt;
        if (nh <= 0) { if (popTotem(t)) return; t.setKiller(c.p); t.setHealth(0); } else t.setHealth(nh);
    }

    /** Lethal ability damage respects Totems of Undying (main hand or off hand), like vanilla. */
    static boolean popTotem(LivingEntity t) {
        org.bukkit.inventory.EntityEquipment eq = t.getEquipment();
        if (eq == null) return false;
        org.bukkit.inventory.EquipmentSlot slot;
        if (eq.getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING) slot = org.bukkit.inventory.EquipmentSlot.HAND;
        else if (eq.getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) slot = org.bukkit.inventory.EquipmentSlot.OFF_HAND;
        else return false;
        org.bukkit.event.entity.EntityResurrectEvent ev = new org.bukkit.event.entity.EntityResurrectEvent(t, slot);
        org.bukkit.Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return false;
        if (slot == org.bukkit.inventory.EquipmentSlot.HAND) eq.setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.AIR));
        else eq.setItemInOffHand(new org.bukkit.inventory.ItemStack(Material.AIR));
        t.setHealth(1.0);
        for (PotionEffect e : new ArrayList<>(t.getActivePotionEffects())) t.removePotionEffect(e.getType());
        t.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1));
        t.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));
        t.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0));
        t.playEffect(org.bukkit.EntityEffect.PROTECTED_FROM_DEATH);
        return true;
    }

    static void bonusHit(Player p, LivingEntity t, double hearts) { hurt(new Ctx(p), t, hearts); }

    static void passiveBolt(Player p, LivingEntity t) {
        t.getWorld().strikeLightningEffect(t.getLocation());
        hurt(new Ctx(p), t, v("lightning.passive.damage"));
    }

    static void snd(Location l, Sound s, float vol, float pitch) { l.getWorld().playSound(l, s, vol, pitch); }

    static void zone(int duration, IntConsumer each, Runnable end) {
        new BukkitRunnable() {
            int t = 0;
            public void run() {
                if (t >= duration) { if (end != null) end.run(); cancel(); return; }
                each.accept(t++);
            }
        }.runTaskTimer(pl(), 0L, 1L);
    }

    static void pull(LivingEntity e, Location to, double strength) {
        Vector d = to.toVector().subtract(e.getBoundingBox().getCenter());
        d.setY(0);
        if (d.lengthSquared() > 0.5) d.normalize().multiply(strength); else d.zero();
        d.setY(Math.max(0, e.getVelocity().getY()));
        e.setVelocity(d);
    }

    static boolean dash(Ctx c, String k, Consumer<Location> trail, Particle accent, boolean knock) {
        Player p = c.p;
        Vector dir = p.getEyeLocation().getDirection();
        dir.setY(Math.max(-0.3, Math.min(0.5, dir.getY()))).normalize();
        int n = (int) Math.ceil(v(k + ".distance") / 1.5);
        double dmg = v(k + ".damage"), kb = knock ? v(k + ".knockback") : 0;
        Set<UUID> hit = new HashSet<>();
        snd(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
        Fx2.radial(p.getLocation().add(0, 1, 0), accent, 30, 0.3);
        zone(n, t -> {
            if (!p.isOnline() || p.isDead()) return;
            p.setVelocity(dir.clone().multiply(1.5)); p.setFallDistance(0);
            Location l = p.getLocation().add(0, 1, 0);
            Fx2.ring(l, 0.8, 12, trail);
            Fx2.ring(l.clone().add(0, 0.5, 0), 0.55, 8, trail);
            Fx2.helix(p.getLocation(), 0.9, 2, 12, 1, t * 1.3, Fx2.pt(accent));
            Fx2.p(l, accent, 6, 0.3, 0.4, 0.3, 0.03);
            for (LivingEntity e : enemies(p, l, 1.8)) if (hit.add(e.getUniqueId())) {
                hurt(c, e, dmg);
                Location ec = e.getBoundingBox().getCenter().toLocation(e.getWorld());
                Fx2.radial(ec, accent, 30, 0.45);
                Fx2.p(ec, Particle.SWEEP_ATTACK, 2, 0.3, 0.3, 0.3, 0);
                if (kb > 0) e.setVelocity(dir.clone().multiply(kb).setY(0.4));
            }
        }, () -> { p.setVelocity(dir.clone().multiply(0.2)); Fx2.radial(p.getLocation().add(0, 1, 0), accent, 25, 0.25); });
        return true;
    }

    // ---------- FIRE ----------
    static boolean inferno(Ctx c, String k) {
        Location l = c.p.getLocation().add(0, 1, 0), base = c.p.getLocation().add(0, 0.15, 0);
        double r = v(k + ".radius");
        Consumer<Location> fire = Fx2.both(Fx2.trans(ORANGE, DARKRED, 1.7f), Fx2.sometimes(0.5, Fx2.pt(Particle.FLAME, 1, 0.05, 0.02)));
        l.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, l, 1);
        Fx2.radial(l, Particle.FLAME, 140, 0.7);
        Fx2.radial(l, Particle.SMALL_FLAME, 80, 0.4);
        snd(l, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
        zone(10, t -> {
            double rr = r * (t + 1) / 10;
            Fx2.ring(base, rr, (int) (rr * 14), fire);
            Fx2.ring(base.clone().add(0, 0.8, 0), rr * 0.85, (int) (rr * 6), Fx2.sometimes(0.6, Fx2.pt(Particle.LAVA)));
            Fx2.helix(base, Math.max(0.5, rr * 0.4), 3, 18, 2, t * 0.8, Fx2.pt(Particle.SMALL_FLAME));
        }, () -> Fx2.p(l, Particle.LARGE_SMOKE, 40, r / 3, 0.8, r / 3, 0.02));
        for (LivingEntity e : enemies(c.p, l, r)) {
            hurt(c, e, v(k + ".damage")); e.setFireTicks(ticks(k + ".fire-seconds"));
            Fx2.helix(e.getLocation(), 0.6, 2, 24, 3, 0, fire);
        }
        return true;
    }

    // ---------- WATER ----------
    static boolean tsunami(Ctx c, String k) {
        Player p = c.p;
        Location origin = p.getLocation().add(0, 0.5, 0);
        Vector dir = p.getLocation().getDirection().setY(0).normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        int steps = (int) Math.ceil(v(k + ".range") / 1.5); double width = v(k + ".width");
        Consumer<Location> water = Fx2.both(Fx2.trans(AQUA, BLUE, 1.5f), Fx2.sometimes(0.5, Fx2.pt(Particle.SPLASH, 2, 0.1, 0.05)));
        Set<UUID> hit = new HashSet<>();
        snd(origin, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 0.8f);
        zone(steps, t -> {
            Location f = origin.clone().add(dir.clone().multiply(1.5 * (t + 1)));
            for (double w2 = -width; w2 <= width; w2 += 0.55) {
                if (!Fx2.roll()) continue;
                Location q = f.clone().add(side.clone().multiply(w2));
                double crest = 1.3 + Math.sin(w2 * 1.3 + t) * 0.5;
                for (double y = 0; y <= crest; y += 0.45) water.accept(q.clone().add(0, y, 0));
                Fx2.p(q.clone().add(0, crest, 0), Particle.FALLING_WATER, 2, 0.1, 0.1, 0.1, 0);
                if (Math.random() < 0.25) Fx2.p(q, Particle.BUBBLE_POP, 2, 0.2, 0.2, 0.2, 0);
            }
            Fx2.p(f.clone().add(0, 1.2, 0), Particle.CLOUD, 6, width / 2, 0.4, 0.2, 0.02);
            for (LivingEntity e : enemies(p, f.clone().add(0, 1, 0), width)) {
                Vector pv = origin.toVector().subtract(e.getLocation().toVector()).normalize().multiply(v(k + ".pull-strength")).setY(0.25);
                e.setVelocity(pv);
                if (hit.add(e.getUniqueId())) {
                    hurt(c, e, v(k + ".damage"));
                    Fx2.sphere(e.getBoundingBox().getCenter().toLocation(e.getWorld()), 0.9, 20, water);
                }
            }
        }, null);
        return true;
    }

    static boolean whirlpool(Ctx c, String k) {
        Location ctr = aim(c.p, v(k + ".range")); World w = ctr.getWorld();
        double r = v(k + ".radius"), str = v(k + ".pull-strength");
        Consumer<Location> water = Fx2.both(Fx2.trans(AQUA, BLUE, 1.4f), Fx2.sometimes(0.3, Fx2.pt(Particle.SPLASH, 2, 0.1, 0.05)));
        snd(ctr, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1f, 0.6f);
        zone(ticks(k + ".duration"), t -> {
            for (int arm = 0; arm < 3; arm++) for (int i = 0; i < 18; i++) {
                double a = t * 0.3 + arm * 2.094 + i * 0.3, rr = r * (1 - i / 20.0);
                Location q = new Location(w, ctr.getX() + Math.cos(a) * rr, ctr.getY() + 0.2 + i * 0.03, ctr.getZ() + Math.sin(a) * rr);
                water.accept(q);
                if (i % 4 == 0) Fx2.p(q, Particle.BUBBLE_POP, 1, 0, 0, 0, 0);
            }
            Fx2.ring(ctr.clone().add(0, 0.1, 0), r, 40, Fx2.dust(AQUA, 1.2f));
            Fx2.column(ctr, 3, 0.5, Fx2.sometimes(0.3, Fx2.pt(Particle.SPLASH, 3, 0.2, 0.1)));
            for (LivingEntity e : enemies(c.p, ctr, r)) pull(e, ctr, str);
        }, () -> Fx2.radial(ctr.clone().add(0, 0.5, 0), Particle.SPLASH, 80, 0.5));
        return true;
    }

    // ---------- LIGHTNING ----------
    static final Consumer<Location> ZAP = Fx2.both(Fx2.dust(YELLOW, 1.3f), Fx2.dust(VIOLET, 1.1f), Fx2.sometimes(0.5, Fx2.pt(Particle.ELECTRIC_SPARK, 1, 0.05, 0.05)));

    static void shock(Location at) {
        Fx2.radial(at.clone().add(0, 0.6, 0), Particle.ELECTRIC_SPARK, 60, 0.5);
        Fx2.radial(at.clone().add(0, 0.6, 0), Particle.END_ROD, 25, 0.25);
        Location base = at.clone().add(0, 0.15, 0);
        zone(6, t -> Fx2.ring(base, (t + 1) * 0.8, 24 + t * 6, Fx2.trans(VIOLET, YELLOW, 1.5f)), null);
    }

    static boolean thunderbolt(Ctx c, String k) {
        LivingEntity t = target(c.p, v(k + ".range"));
        if (t == null) return false;
        Location tl = t.getLocation(), sky = tl.clone().add(0, 14, 0);
        Fx2.bolt(sky, tl, ZAP);
        Fx2.bolt(sky.clone().add(2, 0, 1), tl, ZAP);
        t.getWorld().strikeLightningEffect(tl);
        shock(tl);
        hurt(c, t, v(k + ".damage"));
        return true;
    }

    static boolean chain(Ctx c, String k) {
        List<LivingEntity> chain = new ArrayList<>();
        Location from = c.p.getEyeLocation(); double range = v(k + ".range");
        for (int i = 0; i < (int) v(k + ".targets"); i++) {
            LivingEntity best = null; double bd = Double.MAX_VALUE;
            for (LivingEntity e : enemies(c.p, from, range)) {
                double d = e.getBoundingBox().getCenter().distanceSquared(from.toVector());
                if (!chain.contains(e) && d < bd) { bd = d; best = e; }
            }
            if (best == null) break;
            chain.add(best); from = best.getEyeLocation(); range = v(k + ".jump-range");
        }
        if (chain.isEmpty()) return false;
        long delay = (long) v(k + ".delay-ticks");
        for (int i = 0; i < chain.size(); i++) {
            LivingEntity e = chain.get(i);
            Location prev = i == 0 ? c.p.getEyeLocation().add(c.p.getEyeLocation().getDirection().multiply(0.6)) : chain.get(i - 1).getEyeLocation();
            org.bukkit.Bukkit.getScheduler().runTaskLater(pl(), () -> {
                if (e.isDead()) return;
                Fx2.bolt(prev, e.getEyeLocation(), ZAP);
                e.getWorld().strikeLightningEffect(e.getLocation());
                Fx2.sphere(e.getBoundingBox().getCenter().toLocation(e.getWorld()), 1.0, 24, ZAP);
                shock(e.getLocation());
                hurt(c, e, v(k + ".damage"));
            }, i * delay);
        }
        return true;
    }

    // ---------- EARTH ----------
    static boolean earthquake(Ctx c, String k) {
        Location l = c.p.getLocation(), base = l.clone().add(0, 0.15, 0); double r = v(k + ".radius");
        Consumer<Location> rock = Fx2.both(Fx2.block(Material.STONE), Fx2.block(Material.DIRT), Fx2.sometimes(0.3, Fx2.pt(Particle.CAMPFIRE_COSY_SMOKE, 1, 0.05, 0.02)));
        snd(l, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.5f);
        l.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, l, 1);
        zone(10, t -> {
            double rr = r * (t + 1) / 10;
            Fx2.ring(base, rr, (int) (rr * 10), rock);
            Fx2.ring(base, rr * 0.9, (int) (rr * 6), Fx2.dust(BROWN, 1.4f));
            for (int spoke = 0; spoke < 8; spoke++) {
                double a = spoke * Math.PI / 4;
                Fx2.line(base.clone().add(Math.cos(a) * rr * 0.6, 0, Math.sin(a) * rr * 0.6),
                        base.clone().add(Math.cos(a) * rr, 0, Math.sin(a) * rr), 0.5, Fx2.dust(Color.fromRGB(60, 40, 25), 1.2f));
            }
        }, null);
        for (LivingEntity e : enemies(c.p, l, r)) {
            hurt(c, e, v(k + ".damage"));
            e.setVelocity(new Vector(0, v(k + ".launch"), 0));
            Fx2.sphere(e.getBoundingBox().getCenter().toLocation(e.getWorld()), 0.9, 18, rock);
        }
        return true;
    }

    static boolean stonePrison(Ctx c, String k) {
        LivingEntity t = target(c.p, v(k + ".range"));
        if (t == null) return false;
        Material mat = Material.matchMaterial(str(k + ".block")); if (mat == null) mat = Material.STONE;
        int dur = ticks(k + ".duration");
        if (!pl().root(t, dur)) return true;
        Block base = t.getLocation().getBlock();
        List<Block> placed = new ArrayList<>();
        Consumer<Location> rock = Fx2.block(mat);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            for (int dy = 0; dy <= 2; dy++) {
                Block b = base.getRelative(dx, dy, dz);
                if (b.getType().isAir()) {
                    b.setType(mat); placed.add(b); pl().temp.add(b);
                    Fx2.sphere(b.getLocation().add(0.5, 0.5, 0.5), 0.6, 8, rock);
                }
            }
        }
        snd(t.getLocation(), Sound.BLOCK_STONE_PLACE, 1f, 0.7f);
        Fx2.ring(t.getLocation().add(0, 0.2, 0), 1.6, 24, Fx2.dust(BROWN, 1.5f));
        zone(dur, tick -> {
            if (tick % 4 == 0) Fx2.helix(t.getLocation(), 1.3, 3, 20, 1, tick * 0.3, Fx2.pt(Particle.ENCHANT, 1, 0.05, 0.1));
        }, null);
        final Material fm = mat;
        org.bukkit.Bukkit.getScheduler().runTaskLater(pl(), () -> {
            for (Block b : placed) {
                if (b.getType() == fm) { Fx2.sphere(b.getLocation().add(0.5, 0.5, 0.5), 0.7, 10, rock); b.setType(Material.AIR); }
                pl().temp.remove(b);
            }
            snd(t.getLocation(), Sound.BLOCK_STONE_BREAK, 1f, 0.6f);
        }, dur);
        return true;
    }

    // ---------- ICE ----------
    static boolean glacialBurst(Ctx c, String k) {
        Location l = c.p.getLocation().add(0, 1, 0), base = c.p.getLocation().add(0, 0.15, 0); double r = v(k + ".radius");
        Consumer<Location> frost = Fx2.both(Fx2.trans(ICE, AQUA, 1.5f), Fx2.sometimes(0.5, Fx2.pt(Particle.SNOWFLAKE, 1, 0.05, 0.02)));
        Fx2.radial(l, Particle.SNOWFLAKE, 150, 0.5);
        Fx2.radial(l, Particle.END_ROD, 60, 0.3);
        snd(l, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.6f);
        zone(10, t -> {
            double rr = r * (t + 1) / 10;
            Fx2.sphere(l, rr, (int) (rr * rr * 2.5), Fx2.sometimes(0.6, frost));
            Fx2.ring(base, rr, (int) (rr * 12), frost);
            Fx2.ring(base.clone().add(0, 0.5, 0), rr * 0.8, (int) (rr * 5), Fx2.pt(Particle.END_ROD));
        }, null);
        for (LivingEntity e : enemies(c.p, l, r)) {
            hurt(c, e, v(k + ".damage"));
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks(k + ".slowness-seconds"), (int) v(k + ".slowness-level") - 1));
            Fx2.sphere(e.getBoundingBox().getCenter().toLocation(e.getWorld()), 0.9, 20, frost);
        }
        return true;
    }

    static boolean icicle(Ctx c, String k) {
        Location eye = c.p.getEyeLocation(); Vector dir = eye.getDirection(); Set<UUID> hit = new HashSet<>();
        Vector up = Math.abs(dir.getY()) > 0.95 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector p1 = dir.clone().crossProduct(up).normalize(), p2 = dir.clone().crossProduct(p1).normalize();
        Consumer<Location> ice = Fx2.both(Fx2.trans(ICE, AQUA, 1.2f), Fx2.sometimes(0.4, Fx2.pt(Particle.SNOWFLAKE, 1, 0.03, 0.01)));
        snd(eye, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.6f);
        Fx2.ring(eye.clone().add(dir.clone().multiply(1.2)), 0.5, 12, Fx2.pt(Particle.END_ROD));
        for (double d = 1; d <= v(k + ".range"); d += 0.6) {
            Location l = eye.clone().add(dir.clone().multiply(d));
            if (l.getBlock().getType().isSolid()) { Fx2.radial(l, Particle.SNOWFLAKE, 40, 0.4); break; }
            double a = d * 2.4;
            for (int s = 0; s < 2; s++) {
                double aa = a + s * Math.PI;
                ice.accept(l.clone().add(p1.clone().multiply(Math.cos(aa) * 0.35)).add(p2.clone().multiply(Math.sin(aa) * 0.35)));
            }
            Fx2.p(l, Particle.END_ROD, 1, 0, 0, 0, 0);
            for (LivingEntity e : enemies(c.p, l, 1.3)) if (hit.add(e.getUniqueId())) {
                hurt(c, e, v(k + ".damage"));
                e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks(k + ".weakness-seconds"), (int) v(k + ".weakness-level") - 1));
                Location ec = e.getBoundingBox().getCenter().toLocation(e.getWorld());
                Fx2.radial(ec, Particle.SNOWFLAKE, 40, 0.4);
                Fx2.sphere(ec, 0.9, 20, ice);
            }
        }
        return true;
    }

    // ---------- WIND ----------
    static boolean cyclone(Ctx c, String k) {
        Location ctr = aim(c.p, v(k + ".range")); double r = v(k + ".radius");
        Consumer<Location> wind = Fx2.both(Fx2.dust(GRAY, 1.3f), Fx2.sometimes(0.5, Fx2.pt(Particle.CLOUD, 1, 0.05, 0.01)));
        Set<UUID> hit = new HashSet<>();
        snd(ctr, Sound.ENTITY_PHANTOM_FLAP, 1.5f, 0.5f);
        zone(ticks(k + ".duration"), t -> {
            for (double h = 0; h < 6; h += 0.35) {
                if (!Fx2.roll()) continue;
                double rr = 0.4 + h * 0.5;
                for (int arm = 0; arm < 3; arm++) {
                    double a = t * 0.55 + h * 1.1 + arm * 2.094;
                    wind.accept(ctr.clone().add(Math.cos(a) * rr, h, Math.sin(a) * rr));
                }
            }
            Fx2.ring(ctr.clone().add(0, 0.1, 0), r, 30, Fx2.sometimes(0.5, wind));
            if (t % 3 == 0) Fx2.p(ctr.clone().add(0, 0.3, 0), Particle.WHITE_ASH, 10, r / 2, 0.2, r / 2, 0.05);
            if (t % 4 == 0) for (LivingEntity e : enemies(c.p, ctr, r)) {
                e.setVelocity(new Vector(0, v(k + ".lift"), 0));
                if (hit.add(e.getUniqueId())) hurt(c, e, v(k + ".damage"));
            }
        }, () -> Fx2.radial(ctr.clone().add(0, 1, 0), Particle.CLOUD, 60, 0.5));
        return true;
    }

    // ---------- VOID ----------
    static void rift(Location c, Vector right) {
        Consumer<Location> f = Fx2.trans(VIOLET, BLACK, 1.5f);
        int k = Fx2.n(28);
        for (int i = 0; i < k; i++) {
            double a = 2 * Math.PI * i / k;
            f.accept(c.clone().add(right.clone().multiply(Math.cos(a) * 0.9)).add(0, Math.sin(a) * 1.3, 0));
        }
        Fx2.p(c, Particle.REVERSE_PORTAL, 30, 0.4, 0.8, 0.4, 0.1);
    }

    static boolean eventHorizon(Ctx c, String k) {
        Location ctr = aim(c.p, v(k + ".range")).add(0, 1, 0); World w = ctr.getWorld();
        double pr = v(k + ".pull-radius"), str = v(k + ".pull-strength"), er = v(k + ".explosion-radius");
        Consumer<Location> disc = Fx2.trans(VIOLET, PURPLE, 1.4f), core = Fx2.dust(BLACK, 1.8f);
        snd(ctr, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.4f);
        zone(ticks(k + ".pull-seconds"), t -> {
            Fx2.sphere(ctr, 0.9 + 0.1 * Math.sin(t * 0.5), 26, core);
            for (int arm = 0; arm < 2; arm++) for (int i = 0; i < 30; i++) {
                double a = t * 0.35 + arm * Math.PI + i * 0.22, rr = 4.0 - i * 0.12;
                if (rr < 1 || !Fx2.roll()) continue;
                disc.accept(new Location(w, ctr.getX() + Math.cos(a) * rr, ctr.getY() + Math.sin(a * 2) * 0.15, ctr.getZ() + Math.sin(a) * rr));
            }
            Fx2.ring(ctr, pr, 40, Fx2.sometimes(0.4, Fx2.pt(Particle.REVERSE_PORTAL)));
            Fx2.p(ctr, Particle.PORTAL, 30, pr / 2, pr / 2, pr / 2, 1.0);
            for (LivingEntity e : enemies(c.p, ctr, pr)) {
                Vector d = ctr.toVector().subtract(e.getBoundingBox().getCenter());
                if (d.lengthSquared() > 1) e.setVelocity(d.normalize().multiply(str));
            }
        }, () -> {
            Fx2.radial(ctr, Particle.END_ROD, 100, 0.8);
            Fx2.radial(ctr, Particle.PORTAL, 120, 1.2);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, ctr, 2);
            snd(ctr, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
            zone(8, t -> Fx2.sphere(ctr, er * (t + 1) / 8, 60, Fx2.trans(VIOLET, BLACK, 1.6f)), null);
            for (LivingEntity e : enemies(c.p, ctr, er)) hurt(c, e, v(k + ".damage"));
        });
        return true;
    }

    static boolean riftwalk(Ctx c, String k) {
        Player p = c.p;
        LivingEntity t = target(p, v(k + ".range"));
        if (t == null) return false;
        Location tl = t.getLocation();
        Vector back = tl.getDirection().setY(0);
        if (back.lengthSquared() < 1e-6) back = p.getLocation().getDirection().setY(0);
        back.normalize().multiply(-1.5);
        Location to = tl.clone().add(back);
        if (!to.getBlock().isPassable() || !to.clone().add(0, 1, 0).getBlock().isPassable()) to = tl.clone();
        to.setDirection(tl.toVector().subtract(to.toVector()));
        Location from = p.getLocation().add(0, 1, 0);
        Vector look = p.getLocation().getDirection().setY(0);
        Vector right = look.lengthSquared() < 1e-6 ? new Vector(1, 0, 0) : new Vector(-look.getZ(), 0, look.getX()).normalize();
        rift(from, right);
        Fx2.line(from, to.clone().add(0, 1, 0), 0.5, Fx2.sometimes(0.6, Fx2.pt(Particle.PORTAL, 2, 0.1, 0.2)));
        p.teleport(to);
        Location arrive = to.clone().add(0, 1, 0);
        rift(arrive, new Vector(-to.getDirection().getZ(), 0, to.getDirection().getX()).normalize());
        Fx2.radial(arrive, Particle.END_ROD, 40, 0.4);
        Fx2.radial(arrive, Particle.PORTAL, 60, 0.8);
        snd(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks(k + ".blindness-seconds"), 0));
        pl().rift.put(p.getUniqueId(), System.currentTimeMillis() + (long) (v(k + ".bonus-window-seconds") * 1000));
        return true;
    }

    // ---------- NATURE ----------
    static boolean worldroot(Ctx c, String k) {
        Location l = c.p.getLocation(), base = l.clone().add(0, 0.15, 0); double r = v(k + ".radius"); int dur = ticks(k + ".duration");
        Consumer<Location> leaf = Fx2.both(Fx2.trans(GREEN, LIME, 1.3f), Fx2.sometimes(0.3, Fx2.pt(Particle.HAPPY_VILLAGER, 1, 0.1, 0)));
        snd(l, Sound.BLOCK_GRASS_BREAK, 1.5f, 0.5f);
        Fx2.ring(base, 1.2, 20, leaf);
        zone(10, t -> {
            double rr = r * (t + 1) / 10;
            Fx2.ring(base, rr, (int) (rr * 12), leaf);
            for (int spoke = 0; spoke < 6; spoke++) {
                double a = spoke * Math.PI / 3 + t * 0.1;
                Fx2.line(base, base.clone().add(Math.cos(a) * rr, 0, Math.sin(a) * rr), 0.5, Fx2.sometimes(0.4, Fx2.dust(Color.fromRGB(90, 60, 30), 1.4f)));
            }
        }, null);
        for (LivingEntity e : enemies(c.p, l, r)) {
            hurt(c, e, v(k + ".damage"));
            pl().root(e, dur);
            Fx2.helix(e.getLocation(), 0.7, 2.5, 30, 3, 0, leaf);
            Fx2.sphere(e.getBoundingBox().getCenter().toLocation(e.getWorld()), 0.8, 16, Fx2.block(Material.MOSS_BLOCK));
            Fx2.p(e.getLocation().add(0, 0.5, 0), Particle.SPORE_BLOSSOM_AIR, 15, 0.5, 0.8, 0.5, 0);
        }
        return true;
    }

    static boolean rebirth(Ctx c, String k) {
        Player p = c.p;
        double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.min(max, p.getHealth() + v(k + ".heal-hearts") * 2));
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) if (NEG.contains(e.getType())) p.removePotionEffect(e.getType());
        pl().leech.put(p.getUniqueId(), new double[]{System.currentTimeMillis() + v(k + ".leech-seconds") * 1000, 0});
        Consumer<Location> heal = Fx2.both(Fx2.trans(GOLD, GREEN, 1.3f), Fx2.sometimes(0.25, Fx2.pt(Particle.HAPPY_VILLAGER, 1, 0.05, 0)));
        snd(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        Fx2.radial(p.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 40, 0.4);
        zone(24, t -> {
            Location feet = p.getLocation();
            Fx2.helix(feet, 0.9, 2.2, 24, 2, t * 0.5, heal);
            Fx2.helix(feet, 0.9, 2.2, 24, 2, t * 0.5 + Math.PI, heal);
            Fx2.ring(feet.clone().add(0, 0.1, 0), 0.4 + t * 0.06, 16, Fx2.dust(LIME, 1.1f));
            if (t % 5 == 0) Fx2.p(feet.clone().add(0, 2, 0), Particle.HEART, 1, 0.4, 0.2, 0.4, 0);
        }, null);
        return true;
    }

    // ---------- LIGHT ----------
    static boolean smite(Ctx c, String k) {
        LivingEntity t = target(c.p, v(k + ".range"));
        if (t == null) return false;
        Location l = t.getLocation();
        Consumer<Location> holy = Fx2.both(Fx2.trans(WHITE, GOLD, 1.6f), Fx2.sometimes(0.4, Fx2.pt(Particle.END_ROD, 1, 0.05, 0.02)));
        snd(l, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.8f);
        zone(8, tick -> {
            double top = 22 - tick * 3, bot = Math.max(0, 22 - (tick + 1) * 3);
            for (double y = bot; y <= top; y += 0.5) { Fx2.ring(l.clone().add(0, y, 0), 0.6, 8, holy); Fx2.p(l.clone().add(0, y, 0), Particle.END_ROD, 1, 0.1, 0, 0.1, 0); }
        }, () -> {
            Location ic = l.clone().add(0, 0.8, 0);
            Fx2.radial(ic, Particle.END_ROD, 80, 0.6);
            Fx2.radial(ic, Particle.FIREWORK, 40, 0.3);
            Fx2.sphere(ic, 1.2, 30, holy);
            Fx2.ring(l.clone().add(0, 0.15, 0), 2.0, 30, Fx2.dust(GOLD, 1.8f));
            zone(10, tk -> Fx2.column(l, 22, 0.6, Fx2.sometimes(0.5, holy)), null);
        });
        for (PotionEffect e : new ArrayList<>(t.getActivePotionEffects())) if (POS.contains(e.getType())) t.removePotionEffect(e.getType());
        hurt(c, t, v(k + ".damage"));
        return true;
    }

    static boolean flashbang(Ctx c, String k) {
        Location l = c.p.getLocation().add(0, 1, 0), base = c.p.getLocation().add(0, 0.15, 0); double r = v(k + ".radius");
        snd(l, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2f, 1.2f);
        Fx2.radial(l, Particle.END_ROD, 200, 1.0);
        Fx2.radial(l, Particle.FIREWORK, 60, 0.4);
        Fx2.p(l, Particle.EXPLOSION, 3, 0.3, 0.3, 0.3, 0);
        Fx2.sphere(l, 1.0, 30, Fx2.dust(WHITE, 2.5f));
        zone(10, t -> {
            double rr = r * (t + 1) / 10;
            Fx2.sphere(l, rr, (int) (rr * rr * 2), Fx2.sometimes(0.5, Fx2.dust(WHITE, 2f)));
            Fx2.ring(base, rr, (int) (rr * 10), Fx2.dust(YELLOW, 1.6f));
        }, null);
        long until = System.currentTimeMillis() + (long) (v(k + ".disable-seconds") * 1000);
        for (LivingEntity e : enemies(c.p, l, r)) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks(k + ".blind-seconds"), 0));
            if (e instanceof Player) pl().disabled.put(e.getUniqueId(), until);
            Fx2.radial(e.getEyeLocation(), Particle.END_ROD, 20, 0.3);
        }
        return true;
    }

    // ---------- MAGMA ----------
    static boolean eruption(Ctx c, String k) {
        Location l = aim(c.p, v(k + ".range")); World w = l.getWorld(); double r = v(k + ".radius");
        Location base = l.clone().add(0, 0.15, 0);
        Consumer<Location> magma = Fx2.both(Fx2.trans(ORANGE, DARKRED, 1.7f), Fx2.sometimes(0.4, Fx2.pt(Particle.FLAME, 1, 0.1, 0.02)));
        w.spawnParticle(Particle.EXPLOSION_EMITTER, l, 1);
        Fx2.radial(l, Particle.FLAME, 150, 0.7);
        Fx2.radial(l.clone().add(0, 0.5, 0), Particle.LARGE_SMOKE, 50, 0.4);
        snd(l, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
        zone(14, t -> {
            double rr = r * Math.min(1, (t + 1) / 6.0);
            Fx2.ring(base, rr, (int) (rr * 12), magma);
            if (t % 3 == 0) for (int spoke = 0; spoke < 6; spoke++) {
                double a = spoke * Math.PI / 3 + t * 0.05;
                Fx2.line(base, base.clone().add(Math.cos(a) * rr, 0, Math.sin(a) * rr), 0.5, Fx2.dust(DARKRED, 1.3f));
            }
            Fx2.fountain(l, Particle.FLAME, 12, 0.5, 0.6);
            Fx2.fountain(l, Particle.SMALL_FLAME, 8, 0.8, 0.5);
            Fx2.p(l.clone().add(0, 1, 0), Particle.LAVA, 6, 0.5, 0.5, 0.5, 0);
            if (t % 2 == 0) Fx2.fountain(l, Particle.LARGE_SMOKE, 3, 0.4, 0.3);
            Fx2.p(base.clone().add((Math.random() - .5) * r * 2, 0.2, (Math.random() - .5) * r * 2), Particle.DRIPPING_LAVA, 3, 0.2, 0.1, 0.2, 0);
        }, null);
        for (LivingEntity e : enemies(c.p, l, r)) {
            hurt(c, e, v(k + ".damage"));
            e.setVelocity(new Vector(0, v(k + ".launch"), 0));
            Fx2.helix(e.getLocation(), 0.6, 2, 24, 3, 0, magma);
        }
        return true;
    }

    static boolean moltenPrison(Ctx c, String k) {
        LivingEntity t = target(c.p, v(k + ".range"));
        if (t == null) return false;
        int dur = ticks(k + ".duration");
        if (!pl().root(t, dur)) return true;
        Consumer<Location> magma = Fx2.both(Fx2.trans(ORANGE, DARKRED, 1.6f), Fx2.sometimes(0.4, Fx2.pt(Particle.FLAME, 1, 0.05, 0.02)));
        snd(t.getLocation(), Sound.BLOCK_LAVA_POP, 1.5f, 0.6f);
        zone(dur, tick -> {
            Location feet = t.getLocation();
            double rad = 1.8 - 0.8 * tick / (double) dur;
            for (int i = 0; i < 8; i++) {
                double a = tick * 0.15 + i * Math.PI / 4;
                Fx2.column(feet.clone().add(Math.cos(a) * rad, 0, Math.sin(a) * rad), 1.8, 0.3, magma);
            }
            Fx2.ring(feet.clone().add(0, 0.1, 0), rad, 20, magma);
            Fx2.ring(feet.clone().add(0, 1.8, 0), rad, 12, Fx2.pt(Particle.LAVA));
        }, () -> {
            if (t.isDead()) return;
            Location ec = t.getBoundingBox().getCenter().toLocation(t.getWorld());
            Fx2.radial(ec, Particle.FLAME, 80, 0.5);
            Fx2.sphere(ec, 1.2, 30, magma);
            snd(ec, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.3f);
            hurt(c, t, v(k + ".damage"));
        });
        return true;
    }
}

/** Particle toolkit: rings, spheres, helixes, jagged bolts, colored dust. Respects particles.enabled / particles.density. */
final class Fx2 {
    private Fx2() {}

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
        if (!Fx2.on()) return;
        Theme th = theme(el);
        double big = slot == 0 ? 1.0 : 1.4; int pts = slot == 0 ? 6 : 8; double dir = slot == 0 ? 1 : -1;
        Consumer<Location> dust = Fx2.trans(th.a(), th.b(), 1.6f);
        Consumer<Location> spark = Fx2.both(Fx2.sometimes(0.5, Fx2.pt(th.main(), 1, 0.05, 0.02)), Fx2.sometimes(0.3, Fx2.pt(th.spark(), 1, 0.05, 0.02)));
        Consumer<Location> both = Fx2.both(dust, spark);
        Location c0 = p.getLocation().add(0, 1, 0);
        Fx2.radial(c0, th.main(), (int) (90 * big), 0.5);
        Fx2.radial(c0, th.spark(), (int) (60 * big), 0.3);
        Fx2.sphere(c0, 1.6 * big, (int) (50 * big), dust);
        Abilities.zone(24, t -> {
            if (!p.isOnline()) return;
            Location feet = p.getLocation().add(0, 0.1, 0);
            double rot = t * 0.12 * dir, R = 2.3 * big;
            if (t % 2 == 0) {
                for (int i = 1; i <= 3; i++) Fx2.ring(feet, (0.8 + i * 0.55) * big, 10 + i * 7, both);
                for (int i = 0; i < pts; i++) {
                    double a1 = rot + 2 * Math.PI * i / pts, a2 = rot + 2 * Math.PI * ((i + 2) % pts) / pts;
                    Fx2.line(feet.clone().add(Math.cos(a1) * R, 0, Math.sin(a1) * R), feet.clone().add(Math.cos(a2) * R, 0, Math.sin(a2) * R), 0.4, dust);
                }
            }
            if (t % 3 == 0) for (int i = 0; i < pts; i++) {
                double a = rot + 2 * Math.PI * i / pts;
                Fx2.column(feet.clone().add(Math.cos(a) * R, 0, Math.sin(a) * R), 0.8 + (t % 6) * 0.2, 0.3, spark);
            }
            Fx2.helix(p.getLocation(), 1.1 * big, 2.6, 18, 1.5, t * 0.5, dust);
            Fx2.helix(p.getLocation(), 1.1 * big, 2.6, 18, 1.5, t * 0.5 + Math.PI, spark);
            if (t % 4 == 0) Fx2.p(p.getLocation().add(0, 2.8, 0), th.spark(), 12, 0.5, 0.2, 0.5, 0.05);
        }, () -> { if (p.isOnline()) Fx2.radial(p.getLocation().add(0, 1, 0), th.spark(), 60, 0.6); });
    }

    /** Themed burst on every enemy an ability damages. */
    static void hit(Player p, Location hc) {
        if (!Fx2.on()) return;
        Element el = MarblesPlugin.I.element(p);
        if (el == null) return;
        Theme th = theme(el);
        Consumer<Location> dust = Fx2.trans(th.a(), th.b(), 1.4f);
        Fx2.ring(hc, 0.9, 18, dust);
        Fx2.ring(hc.clone().add(0, 0.6, 0), 0.7, 12, dust);
        Fx2.radial(hc, th.spark(), 30, 0.35);
        Fx2.p(hc, th.main(), 12, 0.4, 0.5, 0.4, 0.05);
    }
}
