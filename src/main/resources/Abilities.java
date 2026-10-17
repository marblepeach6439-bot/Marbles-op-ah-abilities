package dev.marbles.opah;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
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
            case FIRE -> slot == 0 ? inferno(c, k) : dash(c, k, Fx.both(Fx.trans(ORANGE, RED, 1.4f), Fx.sometimes(0.4, Fx.pt(Particle.FLAME, 1, 0.05, 0.02))), Particle.FLAME, false);
            case WATER -> slot == 0 ? tsunami(c, k) : whirlpool(c, k);
            case LIGHTNING -> slot == 0 ? thunderbolt(c, k) : chain(c, k);
            case EARTH -> slot == 0 ? earthquake(c, k) : stonePrison(c, k);
            case ICE -> slot == 0 ? glacialBurst(c, k) : icicle(c, k);
            case WIND -> slot == 0 ? cyclone(c, k) : dash(c, k, Fx.both(Fx.dust(GRAY, 1.3f), Fx.sometimes(0.5, Fx.pt(Particle.CLOUD, 1, 0.05, 0.01))), Particle.CLOUD, true);
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
        Fx.p(hc, Particle.CRIT, 12, 0.3, 0.4, 0.3, 0.25);
        Fx.p(hc, Particle.DAMAGE_INDICATOR, 3, 0.2, 0.3, 0.2, 0);
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
        Fx.radial(p.getLocation().add(0, 1, 0), accent, 30, 0.3);
        zone(n, t -> {
            if (!p.isOnline() || p.isDead()) return;
            p.setVelocity(dir.clone().multiply(1.5)); p.setFallDistance(0);
            Location l = p.getLocation().add(0, 1, 0);
            Fx.ring(l, 0.8, 12, trail);
            Fx.ring(l.clone().add(0, 0.5, 0), 0.55, 8, trail);
            Fx.helix(p.getLocation(), 0.9, 2, 12, 1, t * 1.3, Fx.pt(accent));
            Fx.p(l, accent, 6, 0.3, 0.4, 0.3, 0.03);
            for (LivingEntity e : enemies(p, l, 1.8)) if (hit.add(e.getUniqueId())) {
                hurt(c, e, dmg);
                Location ec = e.getBoundingBox().getCenter().toLocation(e.getWorld());
                Fx.radial(ec, accent, 30, 0.45);
                Fx.p(ec, Particle.SWEEP_ATTACK, 2, 0.3, 0.3, 0.3, 0);
                if (kb > 0) e.setVelocity(dir.clone().multiply(kb).setY(0.4));
            }
        }, () -> { p.setVelocity(dir.clone().multiply(0.2)); Fx.radial(p.getLocation().add(0, 1, 0), accent, 25, 0.25); });
        return true;
    }

    // ---------- FIRE ----------
    static boolean inferno(Ctx c, String k) {
        Location l = c.p.getLocation().add(0, 1, 0), base = c.p.getLocation().add(0, 0.15, 0);
        double r = v(k + ".radius");
        Consumer<Location> fire = Fx.both(Fx.trans(ORANGE, DARKRED, 1.7f), Fx.sometimes(0.5, Fx.pt(Particle.FLAME, 1, 0.05, 0.02)));
        l.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, l, 1);
        Fx.radial(l, Particle.FLAME, 140, 0.7);
        Fx.radial(l, Particle.SMALL_FLAME, 80, 0.4);
        snd(l, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
        zone(10, t -> {
            double rr = r * (t + 1) / 10;
            Fx.ring(base, rr, (int) (rr * 14), fire);
            Fx.ring(base.clone().add(0, 0.8, 0), rr * 0.85, (int) (rr * 6), Fx.sometimes(0.6, Fx.pt(Particle.LAVA)));
            Fx.helix(base, Math.max(0.5, rr * 0.4), 3, 18, 2, t * 0.8, Fx.pt(Particle.SMALL_FLAME));
        }, () -> Fx.p(l, Particle.LARGE_SMOKE, 40, r / 3, 0.8, r / 3, 0.02));
        for (LivingEntity e : enemies(c.p, l, r)) {
            hurt(c, e, v(k + ".damage")); e.setFireTicks(ticks(k + ".fire-seconds"));
            Fx.helix(e.getLocation(), 0.6, 2, 24, 3, 0, fire);
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
        Consumer<Location> water = Fx.both(Fx.trans(AQUA, BLUE, 1.5f), Fx.sometimes(0.5, Fx.pt(Particle.SPLASH, 2, 0.1, 0.05)));
        Set<UUID> hit = new HashSet<>();
        snd(origin, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 0.8f);
        zone(steps, t -> {
            Location f = origin.clone().add(dir.clone().multiply(1.5 * (t + 1)));
            for (double w2 = -width; w2 <= width; w2 += 0.55) {
                if (!Fx.roll()) continue;
                Location q = f.clone().add(side.clone().multiply(w2));
                double crest = 1.3 + Math.sin(w2 * 1.3 + t) * 0.5;
                for (double y = 0; y <= crest; y += 0.45) water.accept(q.clone().add(0, y, 0));
                Fx.p(q.clone().add(0, crest, 0), Particle.FALLING_WATER, 2, 0.1, 0.1, 0.1, 0);
                if (Math.random() < 0.25) Fx.p(q, Particle.BUBBLE_POP, 2, 0.2, 0.2, 0.2, 0);
            }
            Fx.p(f.clone().add(0, 1.2, 0), Particle.CLOUD, 6, width / 2, 0.4, 0.2, 0.02);
            for (LivingEntity e : enemies(p, f.clone().add(0, 1, 0), width)) {
                Vector pv = origin.toVector().subtract(e.getLocation().toVector()).normalize().multiply(v(k + ".pull-strength")).setY(0.25);
                e.setVelocity(pv);
                if (hit.add(e.getUniqueId())) {
                    hurt(c, e, v(k + ".damage"));
                    Fx.sphere(e.getBoundingBox().getCenter().toLocation(e.getWorld()), 0.9, 20, water);
                }
            }
        }, null);
        return true;
    }

    static boolean whirlpool(Ctx c, String k) {
        Location ctr = aim(c.p, v(k + ".range")); World w = ctr.getWorld();
        double r = v(k + ".radius"), str = v(k + ".pull-strength");
        Consumer<Location> water = Fx.both(Fx.trans(AQUA, BLUE, 1.4f), Fx.sometimes(0.3, Fx.pt(Particle.SPLASH, 2, 0.1, 0.05)));
        snd(ctr, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1f, 0.6f);
        zone(ticks(k + ".duration"), t -> {
            for (int arm = 0; arm < 3; arm++) for (int i = 0; i < 18; i++) {
                double a = t * 0.3 + arm * 2.094 + i * 0.3, rr = r * (1 - i / 20.0);
                Location q = new Location(w, ctr.getX() + Math.cos(a) * rr, ctr.getY() + 0.2 + i * 0.03, ctr.getZ() + Math.sin(a) * rr);
                water.accept(q);
                if (i % 4 == 0) Fx.p(q, Particle.BUBBLE_POP, 1, 0, 0, 0, 0);
            }
            Fx.ring(ctr.clone().add(0, 0.1, 0), r, 40, Fx.dust(AQUA, 1.2f));
            Fx.column(ctr, 3, 0.5, Fx.sometimes(0.3, Fx.pt(Particle.SPLASH, 3, 0.2, 0.1)));
            for (LivingEntity e : enemies(c.p, ctr, r)) pull(e, ctr, str);
        }, () -> Fx.radial(ctr.clone().add(0, 0.5, 0), Particle.SPLASH, 80, 0.5));
        return true;
    }

    // ---------- LIGHTNING ----------
    static final Consumer<Location> ZAP = Fx.both(Fx.dust(YELLOW, 1.3f), Fx.dust(VIOLET, 1.1f), Fx.sometimes(0.5, Fx.pt(Particle.ELECTRIC_SPARK, 1, 0.05, 0.05)));

    static void shock(Location at) {
        Fx.radial(at.clone().add(0, 0.6, 0), Particle.ELECTRIC_SPARK, 60, 0.5);
        Fx.radial(at.clone().add(0, 0.6, 0), Particle.END_ROD, 25, 0.25);
        Location base = at.clone().add(0, 0.15, 0);
        zone(6, t -> Fx.ring(base, (t + 1) * 0.8, 24 + t * 6, Fx.trans(VIOLET, YELLOW, 1.5f)), null);
    }

    static boolean thunderbolt(Ctx c, String k) {
        LivingEntity t = target(c.p, v(k + ".range"));
        if (t == null) return false;
        Location tl = t.getLocation(), sky = tl.clone().add(0, 14, 0);
        Fx.bolt(sky, tl, ZAP);
        Fx.bolt(sky.clone().add(2, 0, 1), tl, ZAP);
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
                Fx.bolt(prev, e.getEyeLocation(), ZAP);
                e.getWorld().strikeLightningEffect(e.getLocation());
                Fx.sphere(e.getBoundingBox().getCenter().toLocation(e.getWorld()), 1.0, 24, ZAP);
                shock(e.getLocation());
                hurt(c, e, v(k + ".damage"));
            }, i * delay);
        }
        return true;
    }

    // ---------- EARTH ----------
    static boolean earthquake(Ctx c, String k) {
        Location l = c.p.getLocation(), base = l.clone().add(0, 0.15, 0); double r = v(k + ".radius");
        Consumer<Location> rock = Fx.both(Fx.block(Material.STONE), Fx.block(Material.DIRT), Fx.sometimes(0.3, Fx.pt(Particle.CAMPFIRE_COSY_SMOKE, 1, 0.05, 0.02)));
        snd(l, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.5f);
        l.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, l, 1);
        zone(10, t -> {
            double rr = r * (t + 1) / 10;
            Fx.ring(base, rr, (int) (rr * 10), rock);
            Fx.ring(base, rr * 0.9, (int) (rr * 6), Fx.dust(BROWN, 1.4f));
            for (int spoke = 0; spoke < 8; spoke++) {
                double a = spoke * Math.PI / 4;
                Fx.line(base.clone().add(Math.cos(a) * rr * 0.6, 0, Math.sin(a) * rr * 0.6),
                        base.clone().add(Math.cos(a) * rr, 0, Math.sin(a) * rr), 0.5, Fx.dust(Color.fromRGB(60, 40, 25), 1.2f));
            }
        }, null);
        for (LivingEntity e : enemies(c.p, l, r)) {
            hurt(c, e, v(k + ".damage"));
            e.setVelocity(new Vector(0, v(k + ".launch"), 0));
            Fx.sphere(e.getBoundingBox().getCenter().toLocation(e.getWorld()), 0.9, 18, rock);
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
        Consumer<Location> rock = Fx.block(mat);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            for (int dy = 0; dy <= 2; dy++) {
                Block b = base.getRelative(dx, dy, dz);
                if (b.getType().isAir()) {
                    b.setType(mat); placed.add(b); pl().temp.add(b);
                    Fx.sphere(b.getLocation().add(0.5, 0.5, 0.5), 0.6, 8, rock);
                }
            }
        }
        snd(t.getLocation(), Sound.BLOCK_STONE_PLACE, 1f, 0.7f);
        Fx.ring(t.getLocation().add(0, 0.2, 0), 1.6, 24, Fx.dust(BROWN, 1.5f));
        zone(dur, tick -> {
            if (tick % 4 == 0) Fx.helix(t.getLocation(), 1.3, 3, 20, 1, tick * 0.3, Fx.pt(Particle.ENCHANT, 1, 0.05, 0.1));
        }, null);
        final Material fm = mat;
        org.bukkit.Bukkit.getScheduler().runTaskLater(pl(), () -> {
            for (Block b : placed) {
                if (b.getType() == fm) { Fx.sphere(b.getLocation().add(0.5, 0.5, 0.5), 0.7, 10, rock); b.setType(Material.AIR); }
                pl().temp.remove(b);
            }
            snd(t.getLocation(), Sound.BLOCK_STONE_BREAK, 1f, 0.6f);
        }, dur);
        return true;
    }

    // ---------- ICE ----------
    static boolean glacialBurst(Ctx c, String k) {
        Location l = c.p.getLocation().add(0, 1, 0), base = c.p.getLocation().add(0, 0.15, 0); double r = v(k + ".radius");
        Consumer<Location> frost = Fx.both(Fx.trans(ICE, AQUA, 1.5f), Fx.sometimes(0.5, Fx.pt(Particle.SNOWFLAKE, 1, 0.05, 0.02)));
        Fx.radial(l, Particle.SNOWFLAKE, 150, 0.5);
        Fx.radial(l, Particle.END_ROD, 60, 0.3);
        snd(l, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.6f);
        zone(10, t -> {
            double rr = r * (t + 1) / 10;
            Fx.sphere(l, rr, (int) (rr * rr * 2.5), Fx.sometimes(0.6, frost));
            Fx.ring(base, rr, (int) (rr * 12), frost);
            Fx.ring(base.clone().add(0, 0.5, 0), rr * 0.8, (int) (rr * 5), Fx.pt(Particle.END_ROD));
        }, null);
        for (LivingEntity e : enemies(c.p, l, r)) {
            hurt(c, e, v(k + ".damage"));
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks(k + ".slowness-seconds"), (int) v(k + ".slowness-level") - 1));
            Fx.sphere(e.getBoundingBox().getCenter().toLocation(e.getWorld()), 0.9, 20, frost);
        }
        return true;
    }

    static boolean icicle(Ctx c, String k) {
        Location eye = c.p.getEyeLocation(); Vector dir = eye.getDirection(); Set<UUID> hit = new HashSet<>();
        Vector up = Math.abs(dir.getY()) > 0.95 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector p1 = dir.clone().crossProduct(up).normalize(), p2 = dir.clone().crossProduct(p1).normalize();
        Consumer<Location> ice = Fx.both(Fx.trans(ICE, AQUA, 1.2f), Fx.sometimes(0.4, Fx.pt(Particle.SNOWFLAKE, 1, 0.03, 0.01)));
        snd(eye, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.6f);
        Fx.ring(eye.clone().add(dir.clone().multiply(1.2)), 0.5, 12, Fx.pt(Particle.END_ROD));
        for (double d = 1; d <= v(k + ".range"); d += 0.6) {
            Location l = eye.clone().add(dir.clone().multiply(d));
            if (l.getBlock().getType().isSolid()) { Fx.radial(l, Particle.SNOWFLAKE, 40, 0.4); break; }
            double a = d * 2.4;
            for (int s = 0; s < 2; s++) {
                double aa = a + s * Math.PI;
                ice.accept(l.clone().add(p1.clone().multiply(Math.cos(aa) * 0.35)).add(p2.clone().multiply(Math.sin(aa) * 0.35)));
            }
            Fx.p(l, Particle.END_ROD, 1, 0, 0, 0, 0);
            for (LivingEntity e : enemies(c.p, l, 1.3)) if (hit.add(e.getUniqueId())) {
                hurt(c, e, v(k + ".damage"));
                e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks(k + ".weakness-seconds"), (int) v(k + ".weakness-level") - 1));
                Location ec = e.getBoundingBox().getCenter().toLocation(e.getWorld());
                Fx.radial(ec, Particle.SNOWFLAKE, 40, 0.4);
                Fx.sphere(ec, 0.9, 20, ice);
            }
        }
        return true;
    }

    // ---------- WIND ----------
    static boolean cyclone(Ctx c, String k) {
        Location ctr = aim(c.p, v(k + ".range")); double r = v(k + ".radius");
        Consumer<Location> wind = Fx.both(Fx.dust(GRAY, 1.3f), Fx.sometimes(0.5, Fx.pt(Particle.CLOUD, 1, 0.05, 0.01)));
        Set<UUID> hit = new HashSet<>();
        snd(ctr, Sound.ENTITY_PHANTOM_FLAP, 1.5f, 0.5f);
        zone(ticks(k + ".duration"), t -> {
            for (double h = 0; h < 6; h += 0.35) {
                if (!Fx.roll()) continue;
                double rr = 0.4 + h * 0.5;
                for (int arm = 0; arm < 3; arm++) {
                    double a = t * 0.55 + h * 1.1 + arm * 2.094;
                    wind.accept(ctr.clone().add(Math.cos(a) * rr, h, Math.sin(a) * rr));
                }
            }
            Fx.ring(ctr.clone().add(0, 0.1, 0), r, 30, Fx.sometimes(0.5, wind));
            if (t % 3 == 0) Fx.p(ctr.clone().add(0, 0.3, 0), Particle.WHITE_ASH, 10, r / 2, 0.2, r / 2, 0.05);
            if (t % 4 == 0) for (LivingEntity e : enemies(c.p, ctr, r)) {
                e.setVelocity(new Vector(0, v(k + ".lift"), 0));
                if (hit.add(e.getUniqueId())) hurt(c, e, v(k + ".damage"));
            }
        }, () -> Fx.radial(ctr.clone().add(0, 1, 0), Particle.CLOUD, 60, 0.5));
        return true;
    }

    // ---------- VOID ----------
    static void rift(Location c, Vector right) {
        Consumer<Location> f = Fx.trans(VIOLET, BLACK, 1.5f);
        int k = Fx.n(28);
        for (int i = 0; i < k; i++) {
            double a = 2 * Math.PI * i / k;
            f.accept(c.clone().add(right.clone().multiply(Math.cos(a) * 0.9)).add(0, Math.sin(a) * 1.3, 0));
        }
        Fx.p(c, Particle.REVERSE_PORTAL, 30, 0.4, 0.8, 0.4, 0.1);
    }

    static boolean eventHorizon(Ctx c, String k) {
        Location ctr = aim(c.p, v(k + ".range")).add(0, 1, 0); World w = ctr.getWorld();
        double pr = v(k + ".pull-radius"), str = v(k + ".pull-strength"), er = v(k + ".explosion-radius");
        Consumer<Location> disc = Fx.trans(VIOLET, PURPLE, 1.4f), core = Fx.dust(BLACK, 1.8f);
        snd(ctr, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.4f);
        zone(ticks(k + ".pull-seconds"), t -> {
            Fx.sphere(ctr, 0.9 + 0.1 * Math.sin(t * 0.5), 26, core);
            for (int arm = 0; arm < 2; arm++) for (int i = 0; i < 30; i++) {
                double a = t * 0.35 + arm * Math.PI + i * 0.22, rr = 4.0 - i * 0.12;
                if (rr < 1 || !Fx.roll()) continue;
                disc.accept(new Location(w, ctr.getX() + Math.cos(a) * rr, ctr.getY() + Math.sin(a * 2) * 0.15, ctr.getZ() + Math.sin(a) * rr));
            }
            Fx.ring(ctr, pr, 40, Fx.sometimes(0.4, Fx.pt(Particle.REVERSE_PORTAL)));
            Fx.p(ctr, Particle.PORTAL, 30, pr / 2, pr / 2, pr / 2, 1.0);
            for (LivingEntity e : enemies(c.p, ctr, pr)) {
                Vector d = ctr.toVector().subtract(e.getBoundingBox().getCenter());
                if (d.lengthSquared() > 1) e.setVelocity(d.normalize().multiply(str));
            }
        }, () -> {
            Fx.radial(ctr, Particle.END_ROD, 100, 0.8);
            Fx.radial(ctr, Particle.PORTAL, 120, 1.2);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, ctr, 2);
            snd(ctr, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
            zone(8, t -> Fx.sphere(ctr, er * (t + 1) / 8, 60, Fx.trans(VIOLET, BLACK, 1.6f)), null);
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
        Fx.line(from, to.clone().add(0, 1, 0), 0.5, Fx.sometimes(0.6, Fx.pt(Particle.PORTAL, 2, 0.1, 0.2)));
        p.teleport(to);
        Location arrive = to.clone().add(0, 1, 0);
        rift(arrive, new Vector(-to.getDirection().getZ(), 0, to.getDirection().getX()).normalize());
        Fx.radial(arrive, Particle.END_ROD, 40, 0.4);
        Fx.radial(arrive, Particle.PORTAL, 60, 0.8);
        snd(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks(k + ".blindness-seconds"), 0));
        pl().rift.put(p.getUniqueId(), System.currentTimeMillis() + (long) (v(k + ".bonus-window-seconds") * 1000));
        return true;
    }

    // ---------- NATURE ----------
    static boolean worldroot(Ctx c, String k) {
        Location l = c.p.getLocation(), base = l.clone().add(0, 0.15, 0); double r = v(k + ".radius"); int dur = ticks(k + ".duration");
        Consumer<Location> leaf = Fx.both(Fx.trans(GREEN, LIME, 1.3f), Fx.sometimes(0.3, Fx.pt(Particle.HAPPY_VILLAGER, 1, 0.1, 0)));
        snd(l, Sound.BLOCK_GRASS_BREAK, 1.5f, 0.5f);
        Fx.ring(base, 1.2, 20, leaf);
        zone(10, t -> {
            double rr = r * (t + 1) / 10;
            Fx.ring(base, rr, (int) (rr * 12), leaf);
            for (int spoke = 0; spoke < 6; spoke++) {
                double a = spoke * Math.PI / 3 + t * 0.1;
                Fx.line(base, base.clone().add(Math.cos(a) * rr, 0, Math.sin(a) * rr), 0.5, Fx.sometimes(0.4, Fx.dust(Color.fromRGB(90, 60, 30), 1.4f)));
            }
        }, null);
        for (LivingEntity e : enemies(c.p, l, r)) {
            hurt(c, e, v(k + ".damage"));
            pl().root(e, dur);
            Fx.helix(e.getLocation(), 0.7, 2.5, 30, 3, 0, leaf);
            Fx.sphere(e.getBoundingBox().getCenter().toLocation(e.getWorld()), 0.8, 16, Fx.block(Material.MOSS_BLOCK));
            Fx.p(e.getLocation().add(0, 0.5, 0), Particle.SPORE_BLOSSOM_AIR, 15, 0.5, 0.8, 0.5, 0);
        }
        return true;
    }

    static boolean rebirth(Ctx c, String k) {
        Player p = c.p;
        double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.min(max, p.getHealth() + v(k + ".heal-hearts") * 2));
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) if (NEG.contains(e.getType())) p.removePotionEffect(e.getType());
        pl().leech.put(p.getUniqueId(), new double[]{System.currentTimeMillis() + v(k + ".leech-seconds") * 1000, 0});
        Consumer<Location> heal = Fx.both(Fx.trans(GOLD, GREEN, 1.3f), Fx.sometimes(0.25, Fx.pt(Particle.HAPPY_VILLAGER, 1, 0.05, 0)));
        snd(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        Fx.radial(p.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 40, 0.4);
        zone(24, t -> {
            Location feet = p.getLocation();
            Fx.helix(feet, 0.9, 2.2, 24, 2, t * 0.5, heal);
            Fx.helix(feet, 0.9, 2.2, 24, 2, t * 0.5 + Math.PI, heal);
            Fx.ring(feet.clone().add(0, 0.1, 0), 0.4 + t * 0.06, 16, Fx.dust(LIME, 1.1f));
            if (t % 5 == 0) Fx.p(feet.clone().add(0, 2, 0), Particle.HEART, 1, 0.4, 0.2, 0.4, 0);
        }, null);
        return true;
    }

    // ---------- LIGHT ----------
    static boolean smite(Ctx c, String k) {
        LivingEntity t = target(c.p, v(k + ".range"));
        if (t == null) return false;
        Location l = t.getLocation();
        Consumer<Location> holy = Fx.both(Fx.trans(WHITE, GOLD, 1.6f), Fx.sometimes(0.4, Fx.pt(Particle.END_ROD, 1, 0.05, 0.02)));
        snd(l, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.8f);
        zone(8, tick -> {
            double top = 22 - tick * 3, bot = Math.max(0, 22 - (tick + 1) * 3);
            for (double y = bot; y <= top; y += 0.5) { Fx.ring(l.clone().add(0, y, 0), 0.6, 8, holy); Fx.p(l.clone().add(0, y, 0), Particle.END_ROD, 1, 0.1, 0, 0.1, 0); }
        }, () -> {
            Location ic = l.clone().add(0, 0.8, 0);
            Fx.radial(ic, Particle.END_ROD, 80, 0.6);
            Fx.radial(ic, Particle.FIREWORK, 40, 0.3);
            Fx.sphere(ic, 1.2, 30, holy);
            Fx.ring(l.clone().add(0, 0.15, 0), 2.0, 30, Fx.dust(GOLD, 1.8f));
            zone(10, tk -> Fx.column(l, 22, 0.6, Fx.sometimes(0.5, holy)), null);
        });
        for (PotionEffect e : new ArrayList<>(t.getActivePotionEffects())) if (POS.contains(e.getType())) t.removePotionEffect(e.getType());
        hurt(c, t, v(k + ".damage"));
        return true;
    }

    static boolean flashbang(Ctx c, String k) {
        Location l = c.p.getLocation().add(0, 1, 0), base = c.p.getLocation().add(0, 0.15, 0); double r = v(k + ".radius");
        snd(l, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2f, 1.2f);
        Fx.radial(l, Particle.END_ROD, 200, 1.0);
        Fx.radial(l, Particle.FIREWORK, 60, 0.4);
        Fx.p(l, Particle.EXPLOSION, 3, 0.3, 0.3, 0.3, 0);
        Fx.sphere(l, 1.0, 30, Fx.dust(WHITE, 2.5f));
        zone(10, t -> {
            double rr = r * (t + 1) / 10;
            Fx.sphere(l, rr, (int) (rr * rr * 2), Fx.sometimes(0.5, Fx.dust(WHITE, 2f)));
            Fx.ring(base, rr, (int) (rr * 10), Fx.dust(YELLOW, 1.6f));
        }, null);
        long until = System.currentTimeMillis() + (long) (v(k + ".disable-seconds") * 1000);
        for (LivingEntity e : enemies(c.p, l, r)) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks(k + ".blind-seconds"), 0));
            if (e instanceof Player) pl().disabled.put(e.getUniqueId(), until);
            Fx.radial(e.getEyeLocation(), Particle.END_ROD, 20, 0.3);
        }
        return true;
    }

    // ---------- MAGMA ----------
    static boolean eruption(Ctx c, String k) {
        Location l = aim(c.p, v(k + ".range")); World w = l.getWorld(); double r = v(k + ".radius");
        Location base = l.clone().add(0, 0.15, 0);
        Consumer<Location> magma = Fx.both(Fx.trans(ORANGE, DARKRED, 1.7f), Fx.sometimes(0.4, Fx.pt(Particle.FLAME, 1, 0.1, 0.02)));
        w.spawnParticle(Particle.EXPLOSION_EMITTER, l, 1);
        Fx.radial(l, Particle.FLAME, 150, 0.7);
        Fx.radial(l.clone().add(0, 0.5, 0), Particle.LARGE_SMOKE, 50, 0.4);
        snd(l, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
        zone(14, t -> {
            double rr = r * Math.min(1, (t + 1) / 6.0);
            Fx.ring(base, rr, (int) (rr * 12), magma);
            if (t % 3 == 0) for (int spoke = 0; spoke < 6; spoke++) {
                double a = spoke * Math.PI / 3 + t * 0.05;
                Fx.line(base, base.clone().add(Math.cos(a) * rr, 0, Math.sin(a) * rr), 0.5, Fx.dust(DARKRED, 1.3f));
            }
            Fx.fountain(l, Particle.FLAME, 12, 0.5, 0.6);
            Fx.fountain(l, Particle.SMALL_FLAME, 8, 0.8, 0.5);
            Fx.p(l.clone().add(0, 1, 0), Particle.LAVA, 6, 0.5, 0.5, 0.5, 0);
            if (t % 2 == 0) Fx.fountain(l, Particle.LARGE_SMOKE, 3, 0.4, 0.3);
            Fx.p(base.clone().add((Math.random() - .5) * r * 2, 0.2, (Math.random() - .5) * r * 2), Particle.DRIPPING_LAVA, 3, 0.2, 0.1, 0.2, 0);
        }, null);
        for (LivingEntity e : enemies(c.p, l, r)) {
            hurt(c, e, v(k + ".damage"));
            e.setVelocity(new Vector(0, v(k + ".launch"), 0));
            Fx.helix(e.getLocation(), 0.6, 2, 24, 3, 0, magma);
        }
        return true;
    }

    static boolean moltenPrison(Ctx c, String k) {
        LivingEntity t = target(c.p, v(k + ".range"));
        if (t == null) return false;
        int dur = ticks(k + ".duration");
        if (!pl().root(t, dur)) return true;
        Consumer<Location> magma = Fx.both(Fx.trans(ORANGE, DARKRED, 1.6f), Fx.sometimes(0.4, Fx.pt(Particle.FLAME, 1, 0.05, 0.02)));
        snd(t.getLocation(), Sound.BLOCK_LAVA_POP, 1.5f, 0.6f);
        zone(dur, tick -> {
            Location feet = t.getLocation();
            double rad = 1.8 - 0.8 * tick / (double) dur;
            for (int i = 0; i < 8; i++) {
                double a = tick * 0.15 + i * Math.PI / 4;
                Fx.column(feet.clone().add(Math.cos(a) * rad, 0, Math.sin(a) * rad), 1.8, 0.3, magma);
            }
            Fx.ring(feet.clone().add(0, 0.1, 0), rad, 20, magma);
            Fx.ring(feet.clone().add(0, 1.8, 0), rad, 12, Fx.pt(Particle.LAVA));
        }, () -> {
            if (t.isDead()) return;
            Location ec = t.getBoundingBox().getCenter().toLocation(t.getWorld());
            Fx.radial(ec, Particle.FLAME, 80, 0.5);
            Fx.sphere(ec, 1.2, 30, magma);
            snd(ec, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.3f);
            hurt(c, t, v(k + ".damage"));
        });
        return true;
    }
}
