package dev.marbles.opah;

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

    // ---------- entry ----------
    static boolean cast(Player p, Element el, int slot) {
        Ctx c = new Ctx(p);
        String k = el.path(slot);
        return switch (el) {
            case FIRE -> slot == 0 ? inferno(c, k) : dash(c, k, Particle.FLAME, false);
            case WATER -> slot == 0 ? tsunami(c, k) : whirlpool(c, k);
            case LIGHTNING -> slot == 0 ? thunderbolt(c, k) : chain(c, k);
            case EARTH -> slot == 0 ? earthquake(c, k) : stonePrison(c, k);
            case ICE -> slot == 0 ? glacialBurst(c, k) : icicle(c, k);
            case WIND -> slot == 0 ? cyclone(c, k) : dash(c, k, Particle.CLOUD, true);
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

    static void ring(Location l, double r, Particle part, int n) {
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            l.getWorld().spawnParticle(part, l.getX() + Math.cos(a) * r, l.getY(), l.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0.02);
        }
    }

    static void line(Location a, Location b, Particle part) {
        Vector d = b.toVector().subtract(a.toVector()); double len = d.length();
        if (len < 0.01) return;
        d.normalize();
        for (double i = 0; i < len; i += 0.5) a.getWorld().spawnParticle(part, a.clone().add(d.clone().multiply(i)), 1, 0, 0, 0, 0);
    }

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

    static boolean dash(Ctx c, String k, Particle trail, boolean knock) {
        Player p = c.p;
        Vector dir = p.getEyeLocation().getDirection();
        dir.setY(Math.max(-0.3, Math.min(0.5, dir.getY()))).normalize();
        int n = (int) Math.ceil(v(k + ".distance") / 1.5);
        double dmg = v(k + ".damage"), kb = knock ? v(k + ".knockback") : 0;
        Set<UUID> hit = new HashSet<>();
        snd(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
        zone(n, t -> {
            if (!p.isOnline() || p.isDead()) return;
            p.setVelocity(dir.clone().multiply(1.5)); p.setFallDistance(0);
            Location l = p.getLocation().add(0, 1, 0);
            l.getWorld().spawnParticle(trail, l, 12, 0.3, 0.4, 0.3, 0.02);
            for (LivingEntity e : enemies(p, l, 1.8)) if (hit.add(e.getUniqueId())) {
                hurt(c, e, dmg);
                if (kb > 0) e.setVelocity(dir.clone().multiply(kb).setY(0.4));
            }
        }, () -> p.setVelocity(dir.clone().multiply(0.2)));
        return true;
    }

    // ---------- FIRE ----------
    static boolean inferno(Ctx c, String k) {
        Location l = c.p.getLocation().add(0, 1, 0); double r = v(k + ".radius");
        for (int i = 1; i <= 4; i++) ring(l.clone().subtract(0, 0.5, 0), r * i / 4, Particle.FLAME, 30 + i * 10);
        l.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, l, 1);
        snd(l, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
        for (LivingEntity e : enemies(c.p, l, r)) { hurt(c, e, v(k + ".damage")); e.setFireTicks(ticks(k + ".fire-seconds")); }
        return true;
    }

    // ---------- WATER ----------
    static boolean tsunami(Ctx c, String k) {
        Player p = c.p;
        Location origin = p.getLocation().add(0, 0.5, 0);
        Vector dir = p.getLocation().getDirection().setY(0).normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        int steps = (int) Math.ceil(v(k + ".range") / 1.5); double width = v(k + ".width");
        Set<UUID> hit = new HashSet<>();
        snd(origin, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 0.8f);
        zone(steps, t -> {
            Location f = origin.clone().add(dir.clone().multiply(1.5 * (t + 1)));
            for (double w = -width; w <= width; w += 0.75)
                f.getWorld().spawnParticle(Particle.SPLASH, f.clone().add(side.clone().multiply(w)), 8, 0.2, 0.7, 0.2, 0.1);
            for (LivingEntity e : enemies(p, f.clone().add(0, 1, 0), width)) {
                Vector pv = origin.toVector().subtract(e.getLocation().toVector()).normalize().multiply(v(k + ".pull-strength")).setY(0.25);
                e.setVelocity(pv);
                if (hit.add(e.getUniqueId())) hurt(c, e, v(k + ".damage"));
            }
        }, null);
        return true;
    }

    static boolean whirlpool(Ctx c, String k) {
        Location ctr = aim(c.p, v(k + ".range")); World w = ctr.getWorld();
        double r = v(k + ".radius"), str = v(k + ".pull-strength");
        snd(ctr, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1f, 0.6f);
        zone(ticks(k + ".duration"), t -> {
            for (int i = 0; i < 24; i++) {
                double a = t * 0.4 + i * 0.5, rr = 1 + i * (r - 1) / 24;
                w.spawnParticle(Particle.SPLASH, ctr.getX() + Math.cos(a) * rr, ctr.getY() + 0.3, ctr.getZ() + Math.sin(a) * rr, 1, 0, 0, 0, 0);
            }
            for (LivingEntity e : enemies(c.p, ctr, r)) pull(e, ctr, str);
        }, null);
        return true;
    }

    // ---------- LIGHTNING ----------
    static boolean thunderbolt(Ctx c, String k) {
        LivingEntity t = target(c.p, v(k + ".range"));
        if (t == null) return false;
        t.getWorld().strikeLightningEffect(t.getLocation());
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
            Location prev = i == 0 ? c.p.getEyeLocation() : chain.get(i - 1).getEyeLocation();
            org.bukkit.Bukkit.getScheduler().runTaskLater(pl(), () -> {
                if (e.isDead()) return;
                line(prev, e.getEyeLocation(), Particle.ELECTRIC_SPARK);
                e.getWorld().strikeLightningEffect(e.getLocation());
                hurt(c, e, v(k + ".damage"));
            }, i * delay);
        }
        return true;
    }

    // ---------- EARTH ----------
    static boolean earthquake(Ctx c, String k) {
        Location l = c.p.getLocation(); double r = v(k + ".radius");
        snd(l, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.5f);
        zone(8, t -> ring(l.clone().add(0, 0.2, 0), r * (t + 1) / 8, Particle.SMOKE, 40), null);
        for (LivingEntity e : enemies(c.p, l, r)) {
            hurt(c, e, v(k + ".damage"));
            e.setVelocity(new Vector(0, v(k + ".launch"), 0));
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
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            for (int dy = 0; dy <= 2; dy++) {
                Block b = base.getRelative(dx, dy, dz);
                if (b.getType().isAir()) { b.setType(mat); placed.add(b); pl().temp.add(b); }
            }
        }
        snd(t.getLocation(), Sound.BLOCK_STONE_PLACE, 1f, 0.7f);
        final Material fm = mat;
        org.bukkit.Bukkit.getScheduler().runTaskLater(pl(), () -> {
            for (Block b : placed) { if (b.getType() == fm) b.setType(Material.AIR); pl().temp.remove(b); }
        }, dur);
        return true;
    }

    // ---------- ICE ----------
    static boolean glacialBurst(Ctx c, String k) {
        Location l = c.p.getLocation().add(0, 1, 0); double r = v(k + ".radius");
        for (int i = 1; i <= 4; i++) ring(l.clone().subtract(0, 0.8, 0), r * i / 4, Particle.SNOWFLAKE, 30);
        snd(l, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.6f);
        for (LivingEntity e : enemies(c.p, l, r)) {
            hurt(c, e, v(k + ".damage"));
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks(k + ".slowness-seconds"), (int) v(k + ".slowness-level") - 1));
        }
        return true;
    }

    static boolean icicle(Ctx c, String k) {
        Location eye = c.p.getEyeLocation(); Vector dir = eye.getDirection(); Set<UUID> hit = new HashSet<>();
        snd(eye, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.6f);
        for (double d = 1; d <= v(k + ".range"); d += 0.6) {
            Location l = eye.clone().add(dir.clone().multiply(d));
            if (l.getBlock().getType().isSolid()) break;
            l.getWorld().spawnParticle(Particle.SNOWFLAKE, l, 3, 0.05, 0.05, 0.05, 0);
            l.getWorld().spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0);
            for (LivingEntity e : enemies(c.p, l, 1.3)) if (hit.add(e.getUniqueId())) {
                hurt(c, e, v(k + ".damage"));
                e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks(k + ".weakness-seconds"), (int) v(k + ".weakness-level") - 1));
            }
        }
        return true;
    }

    // ---------- WIND ----------
    static boolean cyclone(Ctx c, String k) {
        Location ctr = aim(c.p, v(k + ".range")); World w = ctr.getWorld(); double r = v(k + ".radius");
        Set<UUID> hit = new HashSet<>();
        snd(ctr, Sound.ENTITY_PHANTOM_FLAP, 1.5f, 0.5f);
        zone(ticks(k + ".duration"), t -> {
            for (int i = 0; i < 20; i++) {
                double a = t * 0.5 + i * 0.7, h = i * 0.3, rr = 0.5 + h * 0.35;
                w.spawnParticle(Particle.CLOUD, ctr.getX() + Math.cos(a) * rr, ctr.getY() + h, ctr.getZ() + Math.sin(a) * rr, 1, 0, 0, 0, 0);
            }
            if (t % 4 == 0) for (LivingEntity e : enemies(c.p, ctr, r)) {
                e.setVelocity(new Vector(0, v(k + ".lift"), 0));
                if (hit.add(e.getUniqueId())) hurt(c, e, v(k + ".damage"));
            }
        }, null);
        return true;
    }

    // ---------- VOID ----------
    static boolean eventHorizon(Ctx c, String k) {
        Location ctr = aim(c.p, v(k + ".range")).add(0, 1, 0); World w = ctr.getWorld();
        double pr = v(k + ".pull-radius"), str = v(k + ".pull-strength"), er = v(k + ".explosion-radius");
        snd(ctr, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.4f);
        zone(ticks(k + ".pull-seconds"), t -> {
            w.spawnParticle(Particle.REVERSE_PORTAL, ctr, 40, 1.2, 1.2, 1.2, 0.1);
            w.spawnParticle(Particle.PORTAL, ctr, 20, 0.5, 0.5, 0.5, 0.5);
            for (LivingEntity e : enemies(c.p, ctr, pr)) {
                Vector d = ctr.toVector().subtract(e.getBoundingBox().getCenter());
                if (d.lengthSquared() > 1) e.setVelocity(d.normalize().multiply(str));
            }
        }, () -> {
            w.spawnParticle(Particle.EXPLOSION_EMITTER, ctr, 2);
            snd(ctr, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
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
        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.5);
        p.teleport(to);
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, to.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.2);
        snd(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks(k + ".blindness-seconds"), 0));
        pl().rift.put(p.getUniqueId(), System.currentTimeMillis() + (long) (v(k + ".bonus-window-seconds") * 1000));
        return true;
    }

    // ---------- NATURE ----------
    static boolean worldroot(Ctx c, String k) {
        Location l = c.p.getLocation(); double r = v(k + ".radius"); int dur = ticks(k + ".duration");
        ring(l.clone().add(0, 0.2, 0), r, Particle.HAPPY_VILLAGER, 60);
        snd(l, Sound.BLOCK_GRASS_BREAK, 1.5f, 0.5f);
        for (LivingEntity e : enemies(c.p, l, r)) {
            hurt(c, e, v(k + ".damage"));
            pl().root(e, dur);
            e.getWorld().spawnParticle(Particle.BLOCK, e.getLocation().add(0, 0.5, 0), 40, 0.4, 0.6, 0.4, Material.OAK_LOG.createBlockData());
        }
        return true;
    }

    static boolean rebirth(Ctx c, String k) {
        Player p = c.p;
        double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.min(max, p.getHealth() + v(k + ".heal-hearts") * 2));
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) if (NEG.contains(e.getType())) p.removePotionEffect(e.getType());
        pl().leech.put(p.getUniqueId(), new double[]{System.currentTimeMillis() + v(k + ".leech-seconds") * 1000, 0});
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0);
        snd(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        return true;
    }

    // ---------- LIGHT ----------
    static boolean smite(Ctx c, String k) {
        LivingEntity t = target(c.p, v(k + ".range"));
        if (t == null) return false;
        Location l = t.getLocation();
        for (double y = 0; y < 20; y += 0.5) l.getWorld().spawnParticle(Particle.END_ROD, l.clone().add(0, y, 0), 2, 0.15, 0, 0.15, 0);
        snd(l, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.8f);
        for (PotionEffect e : new ArrayList<>(t.getActivePotionEffects())) if (POS.contains(e.getType())) t.removePotionEffect(e.getType());
        hurt(c, t, v(k + ".damage"));
        return true;
    }

    static boolean flashbang(Ctx c, String k) {
        Location l = c.p.getLocation().add(0, 1, 0); double r = v(k + ".radius");
        l.getWorld().spawnParticle(Particle.END_ROD, l, 150, r / 3, r / 3, r / 3, 0.3);
        snd(l, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2f, 1.2f);
        long until = System.currentTimeMillis() + (long) (v(k + ".disable-seconds") * 1000);
        for (LivingEntity e : enemies(c.p, l, r)) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks(k + ".blind-seconds"), 0));
            if (e instanceof Player) pl().disabled.put(e.getUniqueId(), until);
        }
        return true;
    }

    // ---------- MAGMA ----------
    static boolean eruption(Ctx c, String k) {
        Location l = aim(c.p, v(k + ".range")); World w = l.getWorld(); double r = v(k + ".radius");
        w.spawnParticle(Particle.EXPLOSION_EMITTER, l, 1);
        w.spawnParticle(Particle.LAVA, l, 60, r / 3, 0.5, r / 3, 0);
        w.spawnParticle(Particle.FLAME, l, 80, r / 3, 1, r / 3, 0.1);
        snd(l, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
        for (LivingEntity e : enemies(c.p, l, r)) {
            hurt(c, e, v(k + ".damage"));
            e.setVelocity(new Vector(0, v(k + ".launch"), 0));
        }
        return true;
    }

    static boolean moltenPrison(Ctx c, String k) {
        LivingEntity t = target(c.p, v(k + ".range"));
        if (t == null) return false;
        int dur = ticks(k + ".duration");
        if (!pl().root(t, dur)) return true;
        snd(t.getLocation(), Sound.BLOCK_LAVA_POP, 1.5f, 0.6f);
        zone(dur, tick -> {
            Location l = t.getLocation().add(0, 0.2, 0);
            ring(l, 1.5, Particle.FLAME, 20); ring(l.clone().add(0, 1, 0), 1.5, Particle.LAVA, 8);
        }, () -> { if (!t.isDead()) hurt(c, t, v(k + ".damage")); });
        return true;
    }
}
