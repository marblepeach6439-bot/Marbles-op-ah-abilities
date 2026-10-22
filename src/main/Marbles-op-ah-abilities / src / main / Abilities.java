package dev.marbles.opah;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockState;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Boss;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import java.time.Duration;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
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
        final Player p; final Map<UUID, Double> dealt = new HashMap<>(); double capHearts = -1;
        Ctx(Player p) { this.p = p; }
    }

    static final Set<PotionEffectType> POS = Set.of(PotionEffectType.SPEED, PotionEffectType.HASTE, PotionEffectType.STRENGTH,
            PotionEffectType.JUMP_BOOST, PotionEffectType.REGENERATION, PotionEffectType.RESISTANCE, PotionEffectType.FIRE_RESISTANCE,
            PotionEffectType.WATER_BREATHING, PotionEffectType.INVISIBILITY, PotionEffectType.NIGHT_VISION, PotionEffectType.HEALTH_BOOST,
            PotionEffectType.ABSORPTION, PotionEffectType.SATURATION, PotionEffectType.SLOW_FALLING, PotionEffectType.CONDUIT_POWER,
            PotionEffectType.DOLPHINS_GRACE, PotionEffectType.LUCK, PotionEffectType.HERO_OF_THE_VILLAGE, PotionEffectType.INSTANT_HEALTH);
    /** Buffs Divine Smite may strip: everything beneficial EXCEPT regeneration and absorption. */
    static final Set<PotionEffectType> STRIP;
    static {
        Set<PotionEffectType> st = new HashSet<>(POS);
        st.remove(PotionEffectType.REGENERATION); st.remove(PotionEffectType.ABSORPTION);
        STRIP = st;
    }
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
            case FIRE -> slot == 0 ? dash(c, k, Fx2.both(Fx2.trans(ORANGE, RED, 1.4f), Fx2.sometimes(0.4, Fx2.pt(Particle.FLAME, 1, 0.05, 0.02))), Particle.FLAME, false) : inferno(c, k);
            case WATER -> slot == 0 ? tsunami(c, k) : whirlpool(c, k);
            case LIGHTNING -> slot == 0 ? thunderbolt(c, k) : chain(c, k);
            case EARTH -> slot == 0 ? earthquake(c, k) : stonePrison(c, k);
            case ICE -> slot == 0 ? glacialBurst(c, k) : icicle(c, k);
            case WIND -> slot == 0 ? dash(c, k, Fx2.both(Fx2.dust(GRAY, 1.3f), Fx2.sometimes(0.5, Fx2.pt(Particle.CLOUD, 1, 0.05, 0.01))), Particle.CLOUD, true) : cyclone(c, k);
            case VOID -> slot == 0 ? riftwalk(c, k) : eventHorizon(c, k);
            case NATURE -> slot == 0 ? worldroot(c, k) : rebirth(c, k);
            case LIGHT -> slot == 0 ? smite(c, k) : flashbang(c, k);
            case MAGMA -> slot == 0 ? eruption(c, k) : moltenPrison(c, k);
            case DRAGON -> slot == 0 ? false : pl().ex.crystalChaos(c, k);   // Dragon's Descent is two-phase, handled by the plugin
            case SCULK -> slot == 0 ? pl().ex.wrath(c, k) : pl().ex.domain(p, k);
            case DEATH -> slot == 0 ? pl().ex.reaper(c, k) : false;           // Inevitable Death opens a menu, handled by the plugin
            case DIVINE -> slot == 0 ? pl().ex.judgement(c, k) : pl().ex.authority(c, k);
        };
    }

    // ---------- helpers ----------
    static boolean valid(Player p, Entity e) {
        if (!(e instanceof LivingEntity le) || e == p || e instanceof ArmorStand || le.isDead() || !le.isValid()) return false;
        if (e instanceof Player o && (o.getGameMode() == GameMode.SPECTATOR || o.getGameMode() == GameMode.CREATIVE)) return false;
        if (pl().trust.isTrusted(p, e)) return false;     // trusted players are never targets
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
        if (pl().trust.isTrusted(c.p, t)) return;          // safety net for passives, bolts, clones...
        UUID id = t.getUniqueId();
        double done = c.dealt.getOrDefault(id, 0.0);
        double amt = Math.min(hearts * 2, (c.capHearts > 0 ? c.capHearts * 2 : cap()) - done);
        if (amt <= 0 || t.isDead()) return;
        c.dealt.put(id, done + amt);
        t.playHurtAnimation(0f);
        Location hc = t.getBoundingBox().getCenter().toLocation(t.getWorld());
        Fx2.p(hc, Particle.CRIT, 12, 0.3, 0.4, 0.3, 0.25);
        Fx2.p(hc, Particle.DAMAGE_INDICATOR, 3, 0.2, 0.3, 0.2, 0);
        Flair.hit(c.p, hc);
        if (t instanceof Mob m) m.setTarget(c.p);
        pl().leech(c.p, amt);
        double ab = t.getAbsorptionAmount();
        if (ab > 0) { double take = Math.min(ab, amt); t.setAbsorptionAmount(ab - take); amt -= take; }
        if (amt <= 0) return;
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
        pl().allowBuff = true;
        t.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1));
        t.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));
        t.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0));
        pl().allowBuff = false;
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

    // ---------- armor durability helpers ----------
    static void repairArmor(Player p, int amt) {
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        ItemStack[] a = inv.getArmorContents();
        for (ItemStack it : a) {
            if (it == null || it.getType().isAir()) continue;
            if (it.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dm && dm.hasDamage()) {
                dm.setDamage(Math.max(0, dm.getDamage() - amt)); it.setItemMeta(dm);
            }
        }
        inv.setArmorContents(a);
    }

    /** Wears down one random armor piece; the piece breaks if it runs out. */
    static void damageArmor(Player t, int amt) {
        org.bukkit.inventory.PlayerInventory inv = t.getInventory();
        ItemStack[] a = inv.getArmorContents();
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < a.length; i++)
            if (a[i] != null && a[i].getType().getMaxDurability() > 0 && a[i].getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dm && !dm.isUnbreakable()) idx.add(i);
        if (idx.isEmpty()) return;
        int i = idx.get(new Random().nextInt(idx.size()));
        ItemStack it = a[i];
        org.bukkit.inventory.meta.Damageable dm = (org.bukkit.inventory.meta.Damageable) it.getItemMeta();
        int nd = dm.getDamage() + amt;
        if (nd >= it.getType().getMaxDurability()) { a[i] = null; snd(t.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f); }
        else { dm.setDamage(nd); it.setItemMeta(dm); }
        inv.setArmorContents(a);
    }

    // ---------- shared: stun visuals, block at feet, afterimage clones ----------
    static boolean tryStun(Player by, LivingEntity t, int dur) {
        boolean ok = pl().stun(t, dur);
        String name = t instanceof Player pp ? pp.getName() : t.getName();
        by.sendMessage(net.kyori.adventure.text.Component.text(ok ? "Stunned " + name + "!" : name + " is stun-immune right now.",
                ok ? net.kyori.adventure.text.format.NamedTextColor.GOLD : net.kyori.adventure.text.format.NamedTextColor.GRAY));
        return ok;
    }

    static void stunFx(LivingEntity t, int dur) {
        zone(dur, tick -> {
            if (t.isDead() || tick % 3 != 0) return;
            Location h = t.getLocation().add(0, t.getHeight() + 0.3, 0);
            Fx2.ring(h, 0.45, 6, Fx2.dust(YELLOW, 1.0f));
            Fx2.p(h, Particle.CRIT, 2, 0.2, 0.05, 0.2, 0.05);
        }, null);
    }

    /** Puts a block on the target's feet so they look stuck; removed when the stun ends. */
    static void entomb(LivingEntity t, Material mat, int dur, Consumer<Location> fx) {
        Block b = t.getLocation().getBlock();
        if (!(b.getType().isAir() || b.isReplaceable())) return;
        b.setType(mat); pl().temp.add(b);
        Location mid = b.getLocation().add(0.5, 0.5, 0.5);
        Fx2.sphere(mid, 0.8, 16, fx);
        org.bukkit.Bukkit.getScheduler().runTaskLater(pl(), () -> {
            if (b.getType() == mat) { Fx2.sphere(mid, 0.9, 18, fx); b.setType(Material.AIR); }
            pl().temp.remove(b);
        }, dur);
    }

    /** A glowing armor-stand afterimage of the player (their head, armor and held item). */
    static ArmorStand spawnClone(Player p, Location loc) {
        ArmorStand as = loc.getWorld().spawn(loc, ArmorStand.class, s -> {
            s.setArms(true); s.setBasePlate(false); s.setGravity(false); s.setInvulnerable(true); s.setSilent(true);
            s.setMarker(true); s.setCollidable(false); s.setPersistent(false); s.setGlowing(true); s.setCanPickupItems(false);
            org.bukkit.inventory.EntityEquipment eq = s.getEquipment();
            org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
            org.bukkit.inventory.meta.SkullMeta sm = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(p); head.setItemMeta(sm);
            eq.setHelmet(head);
            org.bukkit.inventory.PlayerInventory inv = p.getInventory();
            eq.setChestplate(inv.getChestplate()); eq.setLeggings(inv.getLeggings()); eq.setBoots(inv.getBoots());
            eq.setItemInMainHand(inv.getItemInMainHand());
            s.setRightArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(-20), 0, 0));
        });
        pl().clones.add(as);
        return as;
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
        Player p = c.p;
        double range = v(k + ".range");
        LivingEntity found = target(p, range);
        if (found == null) {
            double bd = Double.MAX_VALUE;
            for (LivingEntity e : enemies(p, p.getLocation(), range)) {
                double d = e.getLocation().distanceSquared(p.getLocation());
                if (d < bd) { bd = d; found = e; }
            }
        }
        if (found == null) return false;
        final LivingEntity tg = found;
        int n = Math.max(1, (int) v(k + ".clones")); double rad = v(k + ".radius");
        long delay = Math.max(1, (long) v(k + ".delay-ticks")); double dmg = v(k + ".damage");
        Consumer<Location> ghost = Fx2.both(Fx2.trans(YELLOW, VIOLET, 1.4f), Fx2.sometimes(0.5, Fx2.pt(Particle.ELECTRIC_SPARK, 1, 0.05, 0.05)));
        double off = Math.random() * Math.PI * 2;
        snd(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.6f);
        Fx2.sphere(p.getLocation().add(0, 1, 0), 1.0, 30, ghost);
        for (int i = 0; i < n; i++) {
            final double a = off + 2 * Math.PI * i / n;
            org.bukkit.Bukkit.getScheduler().runTaskLater(pl(), () -> {
                if (tg.isDead() || !p.isOnline()) return;
                Location tc = tg.getLocation();
                Location far = tc.clone().add(Math.cos(a) * rad, 0, Math.sin(a) * rad);
                Location near = tc.clone().add(Math.cos(a) * 1.2, 0, Math.sin(a) * 1.2);
                far.setDirection(tc.toVector().subtract(far.toVector()));
                near.setDirection(tc.toVector().subtract(near.toVector()));
                ArmorStand clone = spawnClone(p, far);
                Fx2.sphere(far.clone().add(0, 1, 0), 0.9, 24, ghost);
                snd(far, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.8f);
                zone(12, tick -> {
                    if (clone.isDead()) return;
                    Location body = clone.getLocation().add(0, 1, 0);
                    Fx2.sphere(body, 0.55, 10, ghost);
                    Fx2.p(body, Particle.CLOUD, 2, 0.25, 0.5, 0.25, 0.01);
                    if (tick == 3) {
                        Fx2.line(far.clone().add(0, 1, 0), near.clone().add(0, 1, 0), 0.3, ghost);
                        clone.teleport(near);
                    }
                    if (tick == 5) {
                        clone.setRightArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(-100), 0, 0));
                        snd(near, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f);
                        if (!tg.isDead()) {
                            hurt(c, tg, dmg);
                            Location hc = tg.getBoundingBox().getCenter().toLocation(tg.getWorld());
                            Fx2.radial(hc, Particle.ELECTRIC_SPARK, 40, 0.4);
                            Fx2.p(hc, Particle.SWEEP_ATTACK, 2, 0.3, 0.3, 0.3, 0);
                        }
                    }
                }, () -> {
                    Location body = clone.getLocation().add(0, 1, 0);
                    Fx2.radial(body, Particle.CLOUD, 30, 0.2);
                    Fx2.sphere(body, 0.9, 20, ghost);
                    pl().clones.remove(clone);
                    clone.remove();
                });
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
        int dur = ticks(k + ".stun-seconds");
        if (tryStun(c.p, t, dur)) {
            entomb(t, mat, dur, Fx2.block(mat));
            stunFx(t, dur);
            Fx2.ring(t.getLocation().add(0, 0.2, 0), 1.6, 24, Fx2.dust(BROWN, 1.5f));
            snd(t.getLocation(), Sound.BLOCK_STONE_PLACE, 1f, 0.7f);
        }
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
        Material mat = Material.matchMaterial(str(k + ".block")); if (mat == null) mat = Material.ICE;
        final Material fm = mat; int dur = ticks(k + ".stun-seconds"); int wl = (int) v(k + ".weakness-level");
        Consumer<Location> ice = Fx2.both(Fx2.trans(ICE, AQUA, 1.2f), Fx2.sometimes(0.4, Fx2.pt(Particle.SNOWFLAKE, 1, 0.03, 0.01)));
        snd(eye, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.6f);
        Fx2.ring(eye.clone().add(dir.clone().multiply(1.2)), 0.5, 12, Fx2.pt(Particle.END_ROD));
        for (double d = 1; d <= v(k + ".range"); d += 0.6) {
            Location l = eye.clone().add(dir.clone().multiply(d));
            if (l.getBlock().getType().isSolid()) { Fx2.radial(l, Particle.SNOWFLAKE, 40, 0.4); break; }
            double a = d * 2.4;
            for (int s2 = 0; s2 < 2; s2++) {
                double aa = a + s2 * Math.PI;
                ice.accept(l.clone().add(p1.clone().multiply(Math.cos(aa) * 0.35)).add(p2.clone().multiply(Math.sin(aa) * 0.35)));
            }
            Fx2.p(l, Particle.END_ROD, 1, 0, 0, 0, 0);
            for (LivingEntity e : enemies(c.p, l, 1.3)) if (hit.add(e.getUniqueId())) {
                hurt(c, e, v(k + ".damage"));
                if (wl > 0) e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks(k + ".weakness-seconds"), wl - 1));
                Location ec = e.getBoundingBox().getCenter().toLocation(e.getWorld());
                Fx2.radial(ec, Particle.SNOWFLAKE, 40, 0.4);
                Fx2.sphere(ec, 0.9, 20, ice);
                if (tryStun(c.p, e, dur)) {
                    entomb(e, fm, dur, Fx2.both(Fx2.trans(ICE, AQUA, 1.4f), Fx2.block(fm)));
                    stunFx(e, dur);
                }
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
        Location l = c.p.getLocation(), base = l.clone().add(0, 0.15, 0); double r = v(k + ".radius"); int dur = ticks(k + ".stun-seconds");
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
            if (tryStun(c.p, e, dur)) stunFx(e, dur);
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
        int rl = (int) v(k + ".regen-level");
        if (rl > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks(k + ".regen-seconds"), rl - 1));
        int rep = (int) v(k + ".armor-repair");
        if (rep > 0) repairArmor(p, rep);
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
        int dur = ticks(k + ".buff-removal-seconds");
        List<PotionEffect> removed = new ArrayList<>();
        for (PotionEffect e : new ArrayList<>(t.getActivePotionEffects()))
            if (STRIP.contains(e.getType())) { removed.add(e); t.removePotionEffect(e.getType()); }
        UUID tid = t.getUniqueId();
        pl().buffBlock.put(tid, System.currentTimeMillis() + dur * 50L);
        org.bukkit.Bukkit.getScheduler().runTaskLater(pl(), () -> {
            pl().buffBlock.remove(tid);
            if (t.isDead() || !t.isValid()) return;
            pl().allowBuff = true;
            try {
                for (PotionEffect e : removed) {
                    int rem = e.getDuration() < 0 ? -1 : e.getDuration() - dur;
                    if (e.getDuration() >= 0 && rem <= 0) continue;
                    if (!t.hasPotionEffect(e.getType()))
                        t.addPotionEffect(new PotionEffect(e.getType(), rem, e.getAmplifier(), e.isAmbient(), e.hasParticles(), e.hasIcon()));
                }
            } finally { pl().allowBuff = false; }
            Fx2.sphere(t.getBoundingBox().getCenter().toLocation(t.getWorld()), 0.9, 20, Fx2.trans(GOLD, WHITE, 1.2f));
        }, dur);
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
        if (!pl().immobilize(t, dur)) return true;
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
            case DRAGON -> new Theme(Abilities.VIOLET, Abilities.PURPLE, Particle.PORTAL, Particle.END_ROD);
            case SCULK -> new Theme(Extras.TEAL, Extras.DARKTEAL, Particle.SCULK_SOUL, Particle.SCULK_CHARGE_POP);
            case DEATH -> new Theme(Abilities.RED, Abilities.BLACK, Particle.SOUL, Particle.SOUL_FIRE_FLAME);
            case DIVINE -> new Theme(Abilities.WHITE, Abilities.GOLD, Particle.END_ROD, Particle.TOTEM_OF_UNDYING);
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

/** Dragon, Sculk, Death and Divine elements (admin-assigned only, never rolled) + Nature gapple / Magma armor passives. */
final class Extras implements Listener {
    static final Color TEAL = Color.fromRGB(40, 200, 220), DARKTEAL = Color.fromRGB(10, 50, 60);

    final MarblesPlugin pl;
    final NamespacedKey kOwner, kKind, kCompass, kDoom;
    final Map<UUID, DragonSt> dragons = new HashMap<>();
    final Set<UUID> dragonIds = new HashSet<>();
    final Set<EnderCrystal> crystals = new HashSet<>();
    final Map<UUID, Long> marks = new HashMap<>();
    final Map<UUID, DomainSt> domains = new HashMap<>();
    final Map<Block, DB> domainBlocks = new HashMap<>();
    final Map<UUID, UUID> petOwner = new HashMap<>();
    final Map<UUID, String> petKind = new HashMap<>();
    final Set<Mob> pets = new HashSet<>();
    final Map<UUID, Doom> doom = new HashMap<>();
    final Set<UUID> doubleJump = new HashSet<>();
    final Map<UUID, Location> lastSafe = new HashMap<>();
    final Map<UUID, Integer> divineHits = new HashMap<>();

    record DB(UUID owner, BlockData orig) {}
    record Doom(UUID target, long until) {}
    static final class DragonSt { EnderDragon d; long expires; BukkitRunnable follow; }
    static final class DomainSt { UUID owner; Location ctr; double r; boolean ended; Material mat; BukkitRunnable effects; final List<Block> placed = new ArrayList<>(); }
    static final class DoomHolder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    Extras(MarblesPlugin pl) {
        this.pl = pl;
        kOwner = new NamespacedKey(pl, "pet_owner"); kKind = new NamespacedKey(pl, "pet_kind");
        kCompass = new NamespacedKey(pl, "doom_compass"); kDoom = new NamespacedKey(pl, "doom_target");
    }

    // ---------- config helpers ----------
    static double v(String k) { return Abilities.v(k); }
    boolean b(String k) { return pl.getConfig().getBoolean("abilities." + k); }
    static int tk(String k) { return Abilities.ticks(k); }

    void start() {
        Bukkit.getScheduler().runTaskTimer(pl, this::petTick, 20L, 10L);
        Bukkit.getScheduler().runTaskTimer(pl, this::slowTick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(pl, this::jumpTick, 20L, 2L);
        Bukkit.getScheduler().runTask(pl, this::rescanPets);
    }

    void shutdown() {
        for (Map.Entry<UUID, DragonSt> en : new ArrayList<>(dragons.entrySet())) removeDragon(en.getKey(), en.getValue(), false);
        for (EnderCrystal c : crystals) c.remove();
        crystals.clear();
        for (UUID id : new ArrayList<>(domains.keySet())) endDomainNow(id);
    }

    // ---------- standard passives (+ one themed potion) ----------
    void std(Player p, Element el) {
        String pp = el.passivePath();
        lvl(p, PotionEffectType.SPEED, pp + ".speed-level");
        lvl(p, PotionEffectType.STRENGTH, pp + ".strength-level");
        lvl(p, PotionEffectType.RESISTANCE, pp + ".resistance-level");
        String name = pl.getConfig().getString("abilities." + pp + ".extra-effect", "");
        int l = (int) v(pp + ".extra-level");
        if (!name.isEmpty() && l > 0) {
            PotionEffectType t = PotionEffectType.getByKey(NamespacedKey.minecraft(name.toLowerCase()));
            if (t != null) pl.eff(p, t, name.equalsIgnoreCase("night_vision") ? 260 : 40, l - 1);
        }
    }

    void lvl(Player p, PotionEffectType t, String path) {
        int l = (int) v(path);
        if (l > 0) pl.eff(p, t, 40, l - 1);
    }

    // ======================= DRAGON =======================
    Location hoverSpot(Player p, double yawOff) {
        Location l = p.getLocation().add(0, 8, 0);
        l.setYaw(p.getLocation().getYaw() + (float) yawOff);
        l.setPitch(0);
        return l;
    }

    boolean dragonReady(Player p) {
        DragonSt s = dragons.get(p.getUniqueId());
        return s != null && s.d.isValid() && s.expires > System.currentTimeMillis();
    }

    /** First press of Dragon's Descent: an ender dragon hovers above you. Press again to fire it. */
    void dragonSummon(Player p) {
        String k = "dragon.dragons-descent";
        UUID id = p.getUniqueId();
        double off = v(k + ".yaw-offset");
        DragonSt old = dragons.get(id);
        if (old != null) removeDragon(id, old, false);
        EnderDragon d = p.getWorld().spawn(hoverSpot(p, off), EnderDragon.class, e -> {
            e.setAI(false); e.setInvulnerable(true); e.setSilent(true); e.setPersistent(false); e.setGravity(false);
        });
        DragonSt st = new DragonSt();
        st.d = d; st.expires = System.currentTimeMillis() + (long) (v(k + ".ready-seconds") * 1000);
        dragons.put(id, st); dragonIds.add(d.getUniqueId());
        Abilities.snd(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 1f);
        p.sendMessage(Component.text("A dragon coils above you. Press F again to unleash it!", NamedTextColor.LIGHT_PURPLE));
        Consumer<Location> aura = Fx2.trans(Abilities.VIOLET, Abilities.PURPLE, 1.6f);
        st.follow = new BukkitRunnable() {
            public void run() {
                if (!p.isOnline() || p.isDead() || !d.isValid() || System.currentTimeMillis() > st.expires || dragons.get(id) != st) {
                    removeDragon(id, st, true); cancel(); return;
                }
                d.teleport(hoverSpot(p, off));
                Fx2.sphere(d.getLocation(), 3.0, 14, aura);
                Fx2.p(d.getLocation(), Particle.PORTAL, 10, 3, 1, 3, 0.4);
            }
        };
        st.follow.runTaskTimer(pl, 0L, 1L);
    }

    void removeDragon(UUID owner, DragonSt st, boolean poof) {
        if (dragons.get(owner) == st) dragons.remove(owner);
        dragonIds.remove(st.d.getUniqueId());
        if (st.follow != null) st.follow.cancel();
        if (poof && st.d.isValid()) {
            Fx2.radial(st.d.getLocation(), Particle.PORTAL, 80, 0.8);
            Fx2.radial(st.d.getLocation(), Particle.END_ROD, 40, 0.5);
        }
        st.d.remove();
    }

    /** Second press: the dragon rips forward, carving blocks and dealing true damage. */
    boolean dragonFire(Player p) {
        String k = "dragon.dragons-descent";
        UUID id = p.getUniqueId();
        DragonSt st = dragons.get(id);
        if (st == null || !st.d.isValid()) return false;
        dragons.remove(id);
        if (st.follow != null) st.follow.cancel();
        EnderDragon d = st.d;
        Vector dir = p.getEyeLocation().getDirection().normalize();
        double range = v(k + ".range"), speed = v(k + ".speed"), rad = v(k + ".radius"), dmg = v(k + ".damage"), off = v(k + ".yaw-offset");
        boolean destroy = b(k + ".destroy-blocks");
        int steps = (int) Math.ceil(range / speed);
        Location start = d.getLocation();
        Abilities.Ctx c = new Abilities.Ctx(p);
        Set<UUID> hit = new HashSet<>();
        Consumer<Location> breath = Fx2.both(Fx2.trans(Abilities.VIOLET, Abilities.PURPLE, 2f), Fx2.sometimes(0.5, Fx2.pt(Particle.PORTAL, 1, 0.2, 0.3)));
        Abilities.snd(start, Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 0.7f);
        Abilities.zone(steps, i -> {
            Location pos = start.clone().add(dir.clone().multiply(speed * (i + 1)));
            Location face = pos.clone(); face.setDirection(dir); face.setPitch(0); face.setYaw(face.getYaw() + (float) off);
            d.teleport(face);
            Fx2.sphere(pos, rad, 40, breath);
            Fx2.radial(pos, Particle.END_ROD, 20, 0.4);
            if (i % 3 == 0) pos.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, pos, 1);
            if (destroy) carve(pos, rad);
            for (LivingEntity e : Abilities.enemies(p, pos, rad + 1.5)) if (hit.add(e.getUniqueId())) {
                Abilities.hurt(c, e, dmg);
                e.setVelocity(dir.clone().multiply(1.2).setY(0.4));
            }
        }, () -> {
            Fx2.radial(d.getLocation(), Particle.PORTAL, 100, 1.0);
            Fx2.radial(d.getLocation(), Particle.END_ROD, 50, 0.6);
            dragonIds.remove(d.getUniqueId());
            d.remove();
        });
        return true;
    }

    void carve(Location pos, double rad) {
        int r = (int) Math.ceil(rad); World w = pos.getWorld();
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dy * dy + dz * dz > rad * rad) continue;
            Block bl = w.getBlockAt(pos.getBlockX() + dx, pos.getBlockY() + dy, pos.getBlockZ() + dz);
            Material m = bl.getType();
            if (m.isAir() || m.getHardness() < 0 || m == Material.REINFORCED_DEEPSLATE || domainBlocks.containsKey(bl) || pl.temp.contains(bl)) continue;
            bl.setType(Material.AIR, false);
        }
    }

    static Location ground(World w, double x, double y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int by = (int) y + 3; by >= (int) y - 8; by--) {
            Block bl = w.getBlockAt(bx, by, bz);
            if (bl.getType().isSolid() && w.getBlockAt(bx, by + 1, bz).isPassable()) return new Location(w, bx + 0.5, by + 1, bz + 0.5);
        }
        return new Location(w, bx + 0.5, y, bz + 0.5);
    }

    boolean crystalChaos(Abilities.Ctx c, String k) {
        Player p = c.p; World w = p.getWorld();
        int n = (int) v(k + ".crystals"); double r = v(k + ".radius"), range = v(k + ".effect-range");
        int life = tk(k + ".duration-seconds"), regen = (int) v(k + ".regen-level"), repair = (int) v(k + ".repair-per-second");
        List<EnderCrystal> mine = new ArrayList<>();
        Consumer<Location> glow = Fx2.trans(Abilities.VIOLET, Abilities.WHITE, 1.5f);
        for (int i = 0; i < n; i++) {
            double a = Math.random() * Math.PI * 2, dist = 1.5 + Math.random() * Math.max(0.1, r - 1.5);
            Location at = ground(w, p.getLocation().getX() + Math.cos(a) * dist, p.getLocation().getY(), p.getLocation().getZ() + Math.sin(a) * dist);
            EnderCrystal ec = w.spawn(at, EnderCrystal.class, e -> { e.setShowingBottom(false); e.setPersistent(false); });
            mine.add(ec); crystals.add(ec);
            Fx2.sphere(at.clone().add(0, 1, 0), 1.2, 30, glow);
        }
        Abilities.snd(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 1.6f);
        new BukkitRunnable() {
            int t = 0;
            public void run() {
                t += 10;
                boolean any = false, near = false;
                for (EnderCrystal ec : mine) {
                    if (!ec.isValid() || ec.isDead()) continue;
                    any = true;
                    boolean inRange = ec.getWorld().equals(p.getWorld()) && ec.getLocation().distanceSquared(p.getLocation()) <= range * range;
                    if (inRange) { near = true; ec.setBeamTarget(p.getLocation().getBlock().getLocation()); } else ec.setBeamTarget(null);
                    Fx2.helix(ec.getLocation(), 0.8, 2.5, 12, 1.5, t * 0.3, glow);
                }
                if (!any || t >= life || !p.isOnline()) {
                    for (EnderCrystal ec : mine) { crystals.remove(ec); if (ec.isValid()) { Fx2.radial(ec.getLocation().add(0, 1, 0), Particle.END_ROD, 20, 0.3); ec.remove(); } }
                    cancel(); return;
                }
                if (near) {
                    pl.eff(p, PotionEffectType.REGENERATION, 40, Math.max(0, regen - 1));
                    if (t % 20 == 0) Abilities.repairArmor(p, repair);
                }
            }
        }.runTaskTimer(pl, 10L, 10L);
        return true;
    }

    // ======================= SCULK =======================
    boolean wrath(Abilities.Ctx c, String k) {
        Player p = c.p;
        int beams = (int) v(k + ".beams"), dura = (int) v(k + ".armor-durability-damage");
        long delay = Math.max(1, (long) v(k + ".beam-delay-ticks"));
        double dmg = v(k + ".damage"), range = v(k + ".range");
        c.capHearts = Math.max(Abilities.cap() / 2.0, beams * dmg);
        Consumer<Location> sonic = Fx2.both(Fx2.trans(TEAL, DARKTEAL, 1.5f), Fx2.sometimes(0.4, Fx2.pt(Particle.SCULK_SOUL, 1, 0.1, 0.02)));
        for (int i = 0; i < beams; i++) {
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (!p.isOnline() || p.isDead()) return;
                Location eye = p.getEyeLocation(); Vector dir = eye.getDirection(); Set<UUID> hit = new HashSet<>();
                Abilities.snd(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1f);
                Location mouth = eye.clone().add(dir.clone().multiply(0.8)).add(0, -0.3, 0);
                for (double d = 1; d <= range; d += 0.5) {
                    Location l = mouth.clone().add(dir.clone().multiply(d));
                    if (l.getBlock().getType().isSolid()) break;
                    sonic.accept(l);
                    if (((int) (d * 2)) % 6 == 0) l.getWorld().spawnParticle(Particle.SONIC_BOOM, l, 1);
                    for (LivingEntity e : Abilities.enemies(p, l, 1.2)) if (hit.add(e.getUniqueId())) {
                        Abilities.hurt(c, e, dmg);
                        if (e instanceof Player tp) Abilities.damageArmor(tp, dura);
                    }
                }
            }, i * delay);
        }
        return true;
    }

    static boolean placeable(Block bl) { return bl.getType().isAir() || bl.isReplaceable() || bl.isLiquid(); }

    boolean domain(Player p, String k) {
        UUID id = p.getUniqueId();
        if (domains.containsKey(id)) return false;
        int R = (int) v(k + ".radius"); int dur = tk(k + ".duration-seconds");
        Material mat = Material.matchMaterial(Abilities.str(k + ".block")); if (mat == null) mat = Material.SCULK;
        Block cb = p.getLocation().getBlock(); int cx = cb.getX(), cy = cb.getY(), cz = cb.getZ(); World w = p.getWorld();
        DomainSt st = new DomainSt();
        st.owner = id; st.ctr = cb.getLocation().add(0.5, 0, 0.5); st.r = R; st.mat = mat;
        List<Block> todo = new ArrayList<>();
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
            if (x * x + z * z <= R * R) { Block fb = w.getBlockAt(cx + x, cy - 1, cz + z); if (placeable(fb)) todo.add(fb); }
            for (int y = 0; y <= R; y++) {
                double d = Math.sqrt(x * x + y * y + z * z);
                if (d <= R && d > R - 1.5) { Block bl = w.getBlockAt(cx + x, cy + y, cz + z); if (placeable(bl)) todo.add(bl); }
            }
        }
        todo.sort(Comparator.comparingInt(Block::getY));
        domains.put(id, st);
        final Material fm = mat;
        Abilities.snd(p.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 2f, 0.8f);
        new BukkitRunnable() {
            int idx = 0;
            public void run() {
                if (st.ended) { cancel(); return; }
                int end = Math.min(todo.size(), idx + 300);
                for (; idx < end; idx++) {
                    Block bl = todo.get(idx);
                    if (!placeable(bl)) continue;
                    domainBlocks.put(bl, new DB(id, bl.getBlockData()));
                    bl.setType(fm, false); st.placed.add(bl);
                }
                if (idx >= todo.size()) cancel();
            }
        }.runTaskTimer(pl, 0L, 1L);
        st.effects = new BukkitRunnable() {
            int t = 0;
            public void run() {
                if (!p.isOnline() || p.isDead() || t >= dur) { endDomain(id); cancel(); return; }
                t += 10;
                for (Player o : w.getPlayers()) {
                    if (o.getUniqueId().equals(id) || o.isDead() || o.getGameMode() == GameMode.SPECTATOR || pl.trust.isTrusted(id, o.getUniqueId())) continue;
                    if (o.getLocation().distanceSquared(st.ctr) > (double) R * R) continue;
                    pl.eff(o, PotionEffectType.WEAKNESS, 40, Math.max(0, (int) v(k + ".weakness-level") - 1));
                    pl.eff(o, PotionEffectType.SLOWNESS, 40, Math.max(0, (int) v(k + ".slowness-level") - 1));
                }
                pl.eff(p, PotionEffectType.RESISTANCE, 40, Math.max(0, (int) v(k + ".resistance-level") - 1));
                Location rnd = st.ctr.clone().add((Math.random() - .5) * R, Math.random() * R * 0.6, (Math.random() - .5) * R);
                Fx2.p(rnd, Particle.SCULK_SOUL, 6, 2, 2, 2, 0.02);
            }
        };
        st.effects.runTaskTimer(pl, 0L, 10L);
        return true;
    }

    void endDomain(UUID id) {
        DomainSt st = domains.remove(id);
        if (st == null) return;
        st.ended = true;
        if (st.effects != null) st.effects.cancel();
        List<Block> list = new ArrayList<>(st.placed);
        list.sort(Comparator.comparingInt(Block::getY).reversed());
        new BukkitRunnable() {
            int idx = 0;
            public void run() {
                int end = Math.min(list.size(), idx + 400);
                for (; idx < end; idx++) restore(list.get(idx));
                if (idx >= list.size()) cancel();
            }
        }.runTaskTimer(pl, 0L, 1L);
    }

    void endDomainNow(UUID id) {
        DomainSt st = domains.remove(id);
        if (st == null) return;
        st.ended = true;
        if (st.effects != null) st.effects.cancel();
        for (Block bl : st.placed) restore(bl);
    }

    void restore(Block bl) {
        DB db = domainBlocks.remove(bl);
        if (db != null) bl.setBlockData(db.orig(), false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDomainBreak(BlockBreakEvent e) {
        DB db = domainBlocks.get(e.getBlock());
        if (db == null) return;
        if (db.owner().equals(e.getPlayer().getUniqueId())) domainBlocks.remove(e.getBlock());
        else e.setCancelled(true);
    }

    @EventHandler public void onDomainBlockExplode(BlockExplodeEvent e) { e.blockList().removeIf(domainBlocks::containsKey); }
    @EventHandler public void onDomainPistonOut(BlockPistonExtendEvent e) { if (e.getBlocks().stream().anyMatch(domainBlocks::containsKey)) e.setCancelled(true); }
    @EventHandler public void onDomainPistonIn(BlockPistonRetractEvent e) { if (e.getBlocks().stream().anyMatch(domainBlocks::containsKey)) e.setCancelled(true); }

    // ---------- pets: tamed Wardens (Sculk) and Thralls (Death) ----------
    void sun(Mob m) {
        if (m instanceof Zombie z) z.setShouldBurnInDaylight(false);
        else if (m instanceof AbstractSkeleton s) s.setShouldBurnInDaylight(false);
    }

    void makePet(Mob m, Player owner, String kind) {
        m.getPersistentDataContainer().set(kOwner, PersistentDataType.STRING, owner.getUniqueId().toString());
        m.getPersistentDataContainer().set(kKind, PersistentDataType.STRING, kind);
        petOwner.put(m.getUniqueId(), owner.getUniqueId()); petKind.put(m.getUniqueId(), kind);
        pets.add(m); m.setPersistent(true); m.setRemoveWhenFarAway(false);
        sun(m);
    }

    void rescanPets() {
        for (World w : Bukkit.getWorlds()) for (Mob m : w.getEntitiesByClass(Mob.class)) {
            String o = m.getPersistentDataContainer().get(kOwner, PersistentDataType.STRING);
            if (o == null) continue;
            try {
                petOwner.put(m.getUniqueId(), UUID.fromString(o));
                petKind.put(m.getUniqueId(), m.getPersistentDataContainer().getOrDefault(kKind, PersistentDataType.STRING, "minion"));
                pets.add(m); sun(m);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    boolean isAlly(Entity e, UUID owner) {
        return owner.equals(e.getUniqueId()) || owner.equals(petOwner.get(e.getUniqueId())) || (e instanceof Player && pl.trust.isTrusted(owner, e.getUniqueId()));
    }

    List<Mob> ownedPets(UUID owner) {
        List<Mob> out = new ArrayList<>();
        for (Mob m : pets) if (m.isValid() && owner.equals(petOwner.get(m.getUniqueId()))) out.add(m);
        return out;
    }

    void aggro(Mob m, LivingEntity t) {
        if (m instanceof Warden w) w.setAnger(t, 150); else m.setTarget(t);
    }

    void petTick() {
        Iterator<Mob> it = pets.iterator();
        while (it.hasNext()) {
            Mob m = it.next();
            if (!m.isValid()) { it.remove(); petOwner.remove(m.getUniqueId()); petKind.remove(m.getUniqueId()); continue; }
            UUID oid = petOwner.get(m.getUniqueId());
            Player o = oid == null ? null : Bukkit.getPlayer(oid);
            if (o == null || !o.isOnline() || o.isDead()) continue;
            if (m instanceof Warden w) w.clearAnger(o);
            if (!o.getWorld().equals(m.getWorld()) || m.getLocation().distanceSquared(o.getLocation()) > 24 * 24) {
                m.teleport(o.getLocation().add(Math.random() * 2 - 1, 0, Math.random() * 2 - 1));
                continue;
            }
            LivingEntity tg = m.getTarget();
            if (tg != null && (tg.isDead() || isAlly(tg, oid))) m.setTarget(null);
            if (m.getTarget() == null && m.getLocation().distanceSquared(o.getLocation()) > 25) m.getPathfinder().moveTo(o, 1.2);
        }
    }

    @EventHandler public void onPetTarget(EntityTargetLivingEntityEvent e) {
        Entity en = e.getEntity(); LivingEntity t = e.getTarget();
        UUID ow = petOwner.get(en.getUniqueId());
        if (ow != null) {
            if (t == null) return;
            if (isAlly(t, ow) || e.getReason() != EntityTargetEvent.TargetReason.CUSTOM) e.setCancelled(true);
            return;
        }
        if (en instanceof Warden && t instanceof Player p && pl.element(p) == Element.SCULK && pl.hasPassive(p)) e.setCancelled(true);
    }

    @EventHandler public void onTame(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!(e.getRightClicked() instanceof Warden w) || !p.isSneaking()) return;
        if (pl.element(p) != Element.SCULK || !pl.hasPassive(p)) return;
        e.setCancelled(true);
        if (petOwner.containsKey(w.getUniqueId())) { p.sendMessage(Component.text("That warden is already tamed.", NamedTextColor.GRAY)); return; }
        int max = (int) v("sculk.passive.max-tamed");
        long mine = ownedPets(p.getUniqueId()).stream().filter(x -> x instanceof Warden).count();
        if (mine >= max) { p.sendMessage(Component.text("You can only tame " + max + " warden(s).", NamedTextColor.RED)); return; }
        makePet(w, p, "warden"); w.clearAnger(p);
        w.customName(Component.text(p.getName() + "'s Warden", NamedTextColor.AQUA));
        Fx2.radial(w.getLocation().add(0, 1.5, 0), Particle.SCULK_SOUL, 60, 0.4);
        Abilities.snd(w.getLocation(), Sound.ENTITY_WARDEN_NEARBY_CLOSER, 1.5f, 0.8f);
        p.sendMessage(Component.text("The warden bows to you.", NamedTextColor.AQUA));
    }

    @EventHandler public void onKill(EntityDeathEvent e) {
        LivingEntity victim = e.getEntity(); Player k = victim.getKiller();
        if (k == null || k.equals(victim) || pl.element(k) != Element.DEATH || !pl.hasPassive(k)) return;
        if (victim instanceof ArmorStand || victim instanceof Boss || petOwner.containsKey(victim.getUniqueId())) return;
        Location at = victim.getLocation(); String name = victim.getName();
        Bukkit.getScheduler().runTask(pl, () -> {
            Mob m = Math.random() < 0.5 ? at.getWorld().spawn(at, Skeleton.class) : at.getWorld().spawn(at, Zombie.class);
            m.customName(Component.text(name + "'s Thrall", NamedTextColor.DARK_RED));
            makePet(m, k, "minion");
            m.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
            Fx2.radial(at.clone().add(0, 1, 0), Particle.SOUL, 40, 0.3);
            Fx2.sphere(at.clone().add(0, 1, 0), 1.0, 20, Fx2.trans(Abilities.RED, Abilities.BLACK, 1.4f));
            List<Mob> mine = new ArrayList<>();
            for (Mob x : ownedPets(k.getUniqueId())) if ("minion".equals(petKind.get(x.getUniqueId()))) mine.add(x);
            int over = mine.size() - (int) v("death.passive.max-minions");
            for (int i = 0; i < over; i++) { Mob old = mine.get(i); pets.remove(old); petOwner.remove(old.getUniqueId()); old.remove(); }
        });
    }

    // ---------- combat hooks for the new elements' passives ----------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity raw = e.getDamager();
        Entity dmgr = raw;
        if (raw instanceof Projectile pr && pr.getShooter() instanceof Entity sh) dmgr = sh;
        // our dragon and crystals never hurt anyone
        if (dragonIds.contains(raw.getUniqueId()) || (raw instanceof EnderCrystal ec && crystals.contains(ec))) { e.setCancelled(true); return; }
        if (pl.isStunned(dmgr)) return;
        // pets never hurt their owner / each other
        UUID dow = petOwner.get(dmgr.getUniqueId());
        if (dow != null && isAlly(e.getEntity(), dow)) { e.setCancelled(true); return; }
        // pets back up their owner
        if (e.getEntity() instanceof LivingEntity victim) {
            if (dmgr instanceof Player o && !isAlly(victim, o.getUniqueId())) for (Mob m : ownedPets(o.getUniqueId())) aggro(m, victim);
            if (victim instanceof Player o2 && dmgr instanceof LivingEntity att && !isAlly(att, o2.getUniqueId()))
                for (Mob m : ownedPets(o2.getUniqueId())) aggro(m, att);
        }
        if (!(dmgr instanceof Player p) || !(e.getEntity() instanceof LivingEntity t)) return;
        Element el = pl.element(p);
        if (el == null || !pl.hasPassive(p)) return;
        String pp = el.passivePath();
        switch (el) {
            case SCULK -> { if (t instanceof Player) t.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, tk(pp + ".darkness-seconds"), 0)); }
            case DEATH -> {
                if (p.getAttackCooldown() >= v(pp + ".min-attack-strength")) {
                    double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
                    p.setHealth(Math.min(max, p.getHealth() + v(pp + ".lifesteal-hearts") * 2));
                }
            }
            case DIVINE -> {
                int n = divineHits.merge(p.getUniqueId(), 1, Integer::sum);
                if (n >= (int) v(pp + ".hits-required")) {
                    divineHits.put(p.getUniqueId(), 0);
                    t.getWorld().strikeLightningEffect(t.getLocation());
                    Abilities.bonusHit(p, t, v(pp + ".damage"));
                }
            }
            default -> { }
        }
    }

    @EventHandler public void onCrystalPrime(ExplosionPrimeEvent e) {
        if (e.getEntity() instanceof EnderCrystal ec && crystals.contains(ec)) { e.setRadius(0f); e.setFire(false); }
    }

    @EventHandler public void onExplode(EntityExplodeEvent e) {
        if (dragonIds.contains(e.getEntity().getUniqueId()) || (e.getEntity() instanceof EnderCrystal ec && crystals.contains(ec))) {
            e.blockList().clear(); e.setCancelled(true); return;
        }
        e.blockList().removeIf(domainBlocks::containsKey);
    }

    @EventHandler public void onChangeBlock(EntityChangeBlockEvent e) {
        if (dragonIds.contains(e.getEntity().getUniqueId()) || domainBlocks.containsKey(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler public void onDragonHurt(EntityDamageEvent e) {
        if (dragonIds.contains(e.getEntity().getUniqueId())) e.setCancelled(true);
    }

    // Nature: golden apples give extra absorption + saturation
    @EventHandler public void onEat(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (pl.element(p) != Element.NATURE || !pl.hasPassive(p)) return;
        Material m = e.getItem().getType();
        if (m != Material.GOLDEN_APPLE && m != Material.ENCHANTED_GOLDEN_APPLE) return;
        double mult = v("nature.passive.gapple-multiplier");
        if (mult <= 1) return;
        float sat0 = p.getSaturation(); double abs0 = p.getAbsorptionAmount();
        Bukkit.getScheduler().runTask(pl, () -> {
            double dAbs = p.getAbsorptionAmount() - abs0;
            if (dAbs > 0) p.setAbsorptionAmount(abs0 + dAbs * mult);
            float dSat = p.getSaturation() - sat0;
            if (dSat > 0) p.setSaturation((float) Math.min(p.getFoodLevel(), sat0 + dSat * mult));
        });
    }

    // Magma: armor lasts longer
    @EventHandler public void onArmorDamage(PlayerItemDamageEvent e) {
        Player p = e.getPlayer();
        if (pl.element(p) != Element.MAGMA || !pl.hasPassive(p)) return;
        String n = e.getItem().getType().name();
        if (!(n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS"))) return;
        double mult = v("magma.passive.armor-durability-multiplier");
        if (mult <= 1) return;
        double nd = e.getDamage() / mult; int base = (int) nd;
        if (Math.random() < nd - base) base++;
        if (base <= 0) e.setCancelled(true); else e.setDamage(base);
    }

    // ======================= DEATH =======================
    boolean reaper(Abilities.Ctx c, String k) {
        LivingEntity t = Abilities.target(c.p, v(k + ".range"));
        if (t == null) return false;
        marks.put(t.getUniqueId(), System.currentTimeMillis() + (long) (v(k + ".duration-seconds") * 1000));
        applyMark(t, k);
        Consumer<Location> soul = Fx2.trans(Abilities.RED, Abilities.BLACK, 1.6f);
        Abilities.snd(t.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.6f);
        Fx2.radial(t.getBoundingBox().getCenter().toLocation(t.getWorld()), Particle.SOUL, 60, 0.5);
        Fx2.sphere(t.getBoundingBox().getCenter().toLocation(t.getWorld()), 1.3, 40, soul);
        if (t instanceof Player tp) tp.sendMessage(Component.text("You have been marked for death.", NamedTextColor.DARK_RED));
        c.p.sendMessage(Component.text("Marked " + t.getName() + ".", NamedTextColor.RED));
        return true;
    }

    void applyMark(LivingEntity t, String k) {
        t.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false));
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, Math.max(0, (int) v(k + ".slowness-level") - 1), false, true));
        t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, Math.max(0, (int) v(k + ".weakness-level") - 1), false, true));
    }

    void openDoom(Player p) {
        List<Player> list = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p) && !pl.trust.isTrusted(p, o)) list.add(o);
        if (list.isEmpty()) { p.sendMessage(Component.text("No one else is online.", NamedTextColor.GRAY)); return; }
        int shown = Math.min(54, list.size());
        DoomHolder h = new DoomHolder();
        Inventory inv = Bukkit.createInventory(h, ((shown - 1) / 9 + 1) * 9, Component.text("Inevitable Death - choose a target", NamedTextColor.DARK_RED));
        h.inv = inv;
        for (int i = 0; i < shown; i++) {
            Player t = list.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta m = (SkullMeta) head.getItemMeta();
            m.setOwningPlayer(t);
            m.displayName(Component.text(t.getName(), NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            m.getPersistentDataContainer().set(kDoom, PersistentDataType.STRING, t.getUniqueId().toString());
            head.setItemMeta(m);
            inv.setItem(i, head);
        }
        p.openInventory(inv);
    }

    @EventHandler public void onDoomClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof DoomHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        String s = it.getItemMeta().getPersistentDataContainer().get(kDoom, PersistentDataType.STRING);
        if (s == null) return;
        Player t = Bukkit.getPlayer(UUID.fromString(s));
        p.closeInventory();
        if (t == null) { p.sendMessage(Component.text("That player is no longer online.", NamedTextColor.GRAY)); return; }
        startDoom(p, t);
    }

    @EventHandler public void onDoomDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof DoomHolder) e.setCancelled(true);
    }

    void startDoom(Player viewer, Player target) {
        String k = "death.inevitable-death";
        UUID id = viewer.getUniqueId();
        stripCompass(viewer);
        doom.put(id, new Doom(target.getUniqueId(), System.currentTimeMillis() + (long) (v(k + ".duration-seconds") * 1000)));
        ItemStack cp = new ItemStack(Material.COMPASS);
        CompassMeta m = (CompassMeta) cp.getItemMeta();
        m.displayName(Component.text("Compass of Doom: " + target.getName(), NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false));
        m.getPersistentDataContainer().set(kCompass, PersistentDataType.STRING, target.getUniqueId().toString());
        m.setLodestone(target.getLocation()); m.setLodestoneTracked(false);
        cp.setItemMeta(m);
        viewer.getInventory().addItem(cp).values().forEach(i -> viewer.getWorld().dropItemNaturally(viewer.getLocation(), i));
        target.showTitle(Title.title(Component.text("You sense a feeling of doom...", NamedTextColor.RED, TextDecoration.BOLD), Component.empty(),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(1000))));
        target.playSound(target.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.6f);
        Fx2.radial(target.getLocation().add(0, 1, 0), Particle.SOUL, 50, 0.4);
        viewer.sendMessage(Component.text("The compass will lead you to " + target.getName() + ".", NamedTextColor.DARK_RED));
        pl.cd.computeIfAbsent(id, x -> new long[2])[1] = System.currentTimeMillis() + Element.DEATH.cooldown(1) * 1000L;
    }

    void stripCompass(Player v) {
        for (int i = 0; i < v.getInventory().getSize(); i++) {
            ItemStack it = v.getInventory().getItem(i);
            if (it != null && it.getType() == Material.COMPASS && it.hasItemMeta()
                    && it.getItemMeta().getPersistentDataContainer().has(kCompass, PersistentDataType.STRING)) v.getInventory().setItem(i, null);
        }
    }

    void updateCompass(Player v, Player t) {
        for (int i = 0; i < v.getInventory().getSize(); i++) {
            ItemStack it = v.getInventory().getItem(i);
            if (it == null || it.getType() != Material.COMPASS || !it.hasItemMeta()) continue;
            if (!it.getItemMeta().getPersistentDataContainer().has(kCompass, PersistentDataType.STRING)) continue;
            CompassMeta cm = (CompassMeta) it.getItemMeta();
            cm.setLodestone(t.getLocation()); cm.setLodestoneTracked(false);
            it.setItemMeta(cm); v.getInventory().setItem(i, it);
        }
    }

    // ---------- once-a-second upkeep: sculk aura, marks, doom compasses ----------
    void slowTick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (pl.element(p) != Element.SCULK || !pl.hasPassive(p)) continue;
            double r = v("sculk.passive.glowing-radius");
            for (Player o : p.getWorld().getPlayers())
                if (!o.equals(p) && !pl.trust.isTrusted(p, o) && o.getLocation().distanceSquared(p.getLocation()) <= r * r) pl.eff(o, PotionEffectType.GLOWING, 60, 0);
            for (Entity en : p.getNearbyEntities(40, 40, 40)) if (en instanceof Warden w) w.clearAnger(p);
        }
        for (Iterator<Map.Entry<UUID, Long>> it = marks.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Long> en = it.next();
            Entity ent = Bukkit.getEntity(en.getKey());
            if (!(ent instanceof LivingEntity le) || le.isDead() || en.getValue() < now) { it.remove(); continue; }
            applyMark(le, "death.reapers-sentence");
            Fx2.ring(le.getLocation().add(0, le.getHeight() + 0.4, 0), 0.4, 8, Fx2.dust(Abilities.RED, 1.0f));
        }
        for (Iterator<Map.Entry<UUID, Doom>> it = doom.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Doom> en = it.next();
            Player vw = Bukkit.getPlayer(en.getKey()), tg = Bukkit.getPlayer(en.getValue().target());
            if (vw == null || tg == null || tg.isDead() || en.getValue().until() < now) {
                if (vw != null) { stripCompass(vw); vw.sendMessage(Component.text("The curse has run its course.", NamedTextColor.GRAY)); }
                it.remove(); continue;
            }
            updateCompass(vw, tg);
        }
    }

    @EventHandler public void onPlayerDeath(PlayerDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        marks.remove(id); endDomain(id);
        DragonSt st = dragons.get(id);
        if (st != null) removeDragon(id, st, true);
        for (Iterator<Map.Entry<UUID, Doom>> it = doom.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Doom> en = it.next();
            if (en.getValue().target().equals(id) || en.getKey().equals(id)) {
                Player vw = Bukkit.getPlayer(en.getKey());
                if (vw != null) { stripCompass(vw); vw.sendMessage(Component.text("The one you hunted is dead.", NamedTextColor.GRAY)); }
                it.remove();
            }
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer(); UUID id = p.getUniqueId();
        endDomain(id); lastSafe.remove(id); divineHits.remove(id); doom.remove(id);
        DragonSt st = dragons.get(id);
        if (st != null) removeDragon(id, st, false);
        if (doubleJump.remove(id) && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)) { p.setAllowFlight(false); p.setFlying(false); }
    }

    // ======================= DIVINE =======================
    boolean judgement(Abilities.Ctx c, String k) {
        LivingEntity t = Abilities.target(c.p, v(k + ".range"));
        if (t == null) return false;
        Location l = t.getLocation();
        Consumer<Location> holy = Fx2.both(Fx2.trans(Abilities.WHITE, Abilities.GOLD, 1.7f), Fx2.sometimes(0.4, Fx2.pt(Particle.END_ROD, 1, 0.05, 0.02)));
        Abilities.snd(l, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.9f);
        Abilities.zone(8, tick -> {
            double top = 24 - tick * 3, bot = Math.max(0, 24 - (tick + 1) * 3);
            for (double y = bot; y <= top; y += 0.5) { Fx2.ring(l.clone().add(0, y, 0), 0.7, 8, holy); Fx2.p(l.clone().add(0, y, 0), Particle.END_ROD, 1, 0.1, 0, 0.1, 0); }
        }, () -> {
            Location ic = l.clone().add(0, 0.8, 0);
            Fx2.radial(ic, Particle.END_ROD, 90, 0.7); Fx2.radial(ic, Particle.FIREWORK, 40, 0.3); Fx2.sphere(ic, 1.3, 30, holy);
            Fx2.ring(l.clone().add(0, 0.15, 0), 2.2, 30, Fx2.dust(Abilities.GOLD, 1.8f));
        });
        Abilities.hurt(c, t, v(k + ".damage"));
        t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, tk(k + ".blind-seconds"), 0));
        t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, tk(k + ".weakness-seconds"), Math.max(0, (int) v(k + ".weakness-level") - 1)));
        return true;
    }

    boolean authority(Abilities.Ctx c, String k) {
        LivingEntity t = Abilities.target(c.p, v(k + ".range"));
        if (t == null) return false;
        int dur = tk(k + ".duration-seconds");
        Consumer<Location> gold = Fx2.trans(Abilities.GOLD, Abilities.WHITE, 1.5f);
        Abilities.snd(t.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.4f);
        Abilities.zone(16, tick -> {
            Location h = t.getLocation().add(0, t.getHeight() + 0.8 - tick * 0.05, 0);
            Fx2.ring(h, 0.6, 14, gold);
            Fx2.ring(t.getLocation().add(0, 0.15, 0), 1.6 - tick * 0.04, 20, gold);
            Fx2.p(h, Particle.END_ROD, 3, 0.3, 0.1, 0.3, 0.02);
        }, () -> Fx2.radial(t.getBoundingBox().getCenter().toLocation(t.getWorld()), Particle.END_ROD, 40, 0.4));
        Abilities.hurt(c, t, v(k + ".damage"));
        t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, dur, Math.max(0, (int) v(k + ".weakness-level") - 1)));
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, Math.max(0, (int) v(k + ".slowness-level") - 1)));
        return true;
    }

    // ======================= double jump + void immunity =======================
    void jumpTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Element el = pl.element(p); UUID id = p.getUniqueId();
            GameMode g = p.getGameMode();
            boolean want = el != null && (el == Element.DRAGON || el == Element.DIVINE) && pl.hasPassive(p) && b(el.passivePath() + ".double-jump")
                    && (g == GameMode.SURVIVAL || g == GameMode.ADVENTURE) && !p.isDead();
            if (want) {
                doubleJump.add(id);
                if (p.isOnGround() && !p.getAllowFlight()) p.setAllowFlight(true);
                if (el == Element.DRAGON && p.isOnGround() && p.getLocation().getY() > p.getWorld().getMinHeight() + 8) lastSafe.put(id, p.getLocation());
            } else if (doubleJump.remove(id) && (g == GameMode.SURVIVAL || g == GameMode.ADVENTURE)) {
                p.setAllowFlight(false); p.setFlying(false);
            }
        }
    }

    @EventHandler public void onFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (!doubleJump.contains(p.getUniqueId()) || !e.isFlying()) return;
        GameMode g = p.getGameMode();
        if (g != GameMode.SURVIVAL && g != GameMode.ADVENTURE) return;
        e.setCancelled(true);
        if (pl.isStunned(p)) return;
        p.setAllowFlight(false); p.setFlying(false);
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() > 1e-4) dir.normalize().multiply(0.35);
        dir.setY(0.85);
        p.setVelocity(dir); p.setFallDistance(0);
        Location f = p.getLocation();
        Element el = pl.element(p);
        Consumer<Location> puff = el == Element.DRAGON ? Fx2.trans(Abilities.VIOLET, Abilities.PURPLE, 1.4f) : Fx2.trans(Abilities.WHITE, Abilities.GOLD, 1.4f);
        Fx2.ring(f.clone().add(0, 0.1, 0), 0.8, 14, puff);
        Fx2.p(f, el == Element.DRAGON ? Particle.PORTAL : Particle.END_ROD, 12, 0.3, 0.1, 0.3, 0.1);
        Abilities.snd(f, Sound.ENTITY_BAT_TAKEOFF, 0.8f, 1.4f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVoid(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.VOID || !(e.getEntity() instanceof Player p)) return;
        if (pl.element(p) != Element.DRAGON || !pl.hasPassive(p) || !b("dragon.passive.void-immunity")) return;
        e.setCancelled(true);
        Location to = lastSafe.get(p.getUniqueId());
        if (to == null || !to.getWorld().equals(p.getWorld())) to = p.getWorld().getHighestBlockAt(p.getLocation()).getLocation().add(0.5, 1, 0.5);
        final Location dest = to;
        Bukkit.getScheduler().runTask(pl, () -> { p.teleport(dest); p.setFallDistance(0); p.setVelocity(new Vector(0, 0, 0)); });
    }
}

/** Mutual trust between players: trusted players are never hit, stunned, pulled or buffed/debuffed by each other's abilities. */
final class Trust {
    final MarblesPlugin pl;
    final File file;
    final Map<UUID, Set<UUID>> trusted = new HashMap<>();
    final Map<UUID, Map<UUID, Long>> pending = new HashMap<>();   // target -> (requester -> expires)

    Trust(MarblesPlugin pl) {
        this.pl = pl;
        file = new File(pl.getDataFolder(), "trust.yml");
        load();
    }

    boolean enabled() { return pl.getConfig().getBoolean("trust.enabled", true); }

    boolean isTrusted(UUID a, UUID b) {
        Set<UUID> s = trusted.get(a);
        return s != null && s.contains(b);
    }

    boolean isTrusted(Entity a, Entity b) {
        return enabled() && a instanceof Player && b instanceof Player && !a.equals(b) && isTrusted(a.getUniqueId(), b.getUniqueId());
    }

    boolean blocksMelee(Entity damager, Entity victim) {
        return pl.getConfig().getBoolean("trust.block-melee", false) && isTrusted(damager, victim);
    }

    int count(UUID id) { return trusted.getOrDefault(id, Set.of()).size(); }
    int max() { return pl.getConfig().getInt("trust.max-trusted", 5); }

    String nameOf(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? id.toString().substring(0, 8) : n;
    }

    // ---------- storage ----------
    void load() {
        try { load0(); } catch (Throwable t) { pl.getLogger().warning("Could not read trust.yml: " + t.getMessage()); }
    }

    void load0() {
        if (!file.exists()) return;
        YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
        org.bukkit.configuration.ConfigurationSection sec = yc.getConfigurationSection("trust");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            try {
                Set<UUID> set = new HashSet<>();
                for (String o : sec.getStringList(k)) set.add(UUID.fromString(o));
                trusted.put(UUID.fromString(k), set);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    void save() {
        YamlConfiguration yc = new YamlConfiguration();
        for (Map.Entry<UUID, Set<UUID>> en : trusted.entrySet()) {
            List<String> l = new ArrayList<>();
            for (UUID u : en.getValue()) l.add(u.toString());
            if (!l.isEmpty()) yc.set("trust." + en.getKey(), l);
        }
        try { pl.getDataFolder().mkdirs(); yc.save(file); } catch (IOException ex) { pl.getLogger().warning("Could not save trust.yml: " + ex.getMessage()); }
    }

    // ---------- commands: /trust, /untrust, /trustlist ----------
    boolean command(CommandSender s, String cmd, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        if (!enabled()) { say(p, "The trust system is disabled.", NamedTextColor.RED); return true; }
        switch (cmd) {
            case "trustlist" -> list(p);
            case "untrust" -> { if (a.length < 1) say(p, "Usage: /untrust <player>", NamedTextColor.RED); else untrust(p, a[0]); }
            default -> {
                if (a.length == 0 || a[0].equalsIgnoreCase("list")) { list(p); return true; }
                switch (a[0].toLowerCase()) {
                    case "accept" -> { if (a.length < 2) say(p, "Usage: /trust accept <player>", NamedTextColor.RED); else accept(p, a[1]); }
                    case "deny" -> { if (a.length < 2) say(p, "Usage: /trust deny <player>", NamedTextColor.RED); else deny(p, a[1]); }
                    case "remove" -> { if (a.length < 2) say(p, "Usage: /trust remove <player>", NamedTextColor.RED); else untrust(p, a[1]); }
                    default -> request(p, a[0]);
                }
            }
        }
        return true;
    }

    void say(Player p, String msg, NamedTextColor c) { p.sendMessage(Component.text(msg, c)); }

    void request(Player p, String name) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null) { say(p, name + " isn't online.", NamedTextColor.RED); return; }
        if (t.equals(p)) { say(p, "You already trust yourself.", NamedTextColor.GRAY); return; }
        UUID me = p.getUniqueId(), them = t.getUniqueId();
        if (isTrusted(me, them)) { say(p, "You already trust " + t.getName() + ".", NamedTextColor.GRAY); return; }
        if (count(me) >= max()) { say(p, "You can only trust " + max() + " players. Use /untrust first.", NamedTextColor.RED); return; }
        long now = System.currentTimeMillis();
        Map<UUID, Long> mine = pending.get(me);
        if (mine != null && mine.getOrDefault(them, 0L) > now) { accept(p, t.getName()); return; }   // they asked first
        if (!pl.getConfig().getBoolean("trust.require-accept", true)) { link(p, t); return; }
        long exp = now + (long) (pl.getConfig().getDouble("trust.request-expire-seconds", 60) * 1000);
        pending.computeIfAbsent(them, k -> new HashMap<>()).put(me, exp);
        say(p, "Trust request sent to " + t.getName() + ".", NamedTextColor.GREEN);
        t.sendMessage(Component.text(p.getName() + " wants to trust you. ", NamedTextColor.GOLD)
                .append(Component.text("[Accept]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/trust accept " + p.getName())))
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(Component.text("[Deny]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/trust deny " + p.getName()))));
    }

    UUID findRequester(Player p, String name) {
        Map<UUID, Long> m = pending.get(p.getUniqueId());
        if (m == null) return null;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> en : m.entrySet())
            if (en.getValue() > now && nameOf(en.getKey()).equalsIgnoreCase(name)) return en.getKey();
        return null;
    }

    void accept(Player p, String name) {
        UUID req = findRequester(p, name);
        if (req == null) { say(p, "No pending trust request from " + name + ".", NamedTextColor.RED); return; }
        Player r = Bukkit.getPlayer(req);
        if (r == null) { say(p, name + " is no longer online.", NamedTextColor.RED); return; }
        if (count(p.getUniqueId()) >= max() || count(req) >= max()) { say(p, "One of you has reached the trust limit (" + max() + ").", NamedTextColor.RED); return; }
        pending.get(p.getUniqueId()).remove(req);
        link(p, r);
    }

    void deny(Player p, String name) {
        UUID req = findRequester(p, name);
        if (req == null) { say(p, "No pending trust request from " + name + ".", NamedTextColor.RED); return; }
        pending.get(p.getUniqueId()).remove(req);
        say(p, "Denied " + name + "'s request.", NamedTextColor.GRAY);
        Player r = Bukkit.getPlayer(req);
        if (r != null) say(r, p.getName() + " denied your trust request.", NamedTextColor.GRAY);
    }

    void link(Player a, Player b) {
        trusted.computeIfAbsent(a.getUniqueId(), k -> new HashSet<>()).add(b.getUniqueId());
        trusted.computeIfAbsent(b.getUniqueId(), k -> new HashSet<>()).add(a.getUniqueId());
        save();
        say(a, "You now trust " + b.getName() + ". Your abilities won't hurt each other.", NamedTextColor.GREEN);
        say(b, "You now trust " + a.getName() + ". Your abilities won't hurt each other.", NamedTextColor.GREEN);
    }

    void untrust(Player p, String name) {
        UUID other = null;
        for (UUID u : trusted.getOrDefault(p.getUniqueId(), Set.of())) if (nameOf(u).equalsIgnoreCase(name)) other = u;
        if (other == null) { say(p, "You don't trust " + name + ".", NamedTextColor.RED); return; }
        Set<UUID> mine = trusted.get(p.getUniqueId()); if (mine != null) mine.remove(other);
        Set<UUID> theirs = trusted.get(other); if (theirs != null) theirs.remove(p.getUniqueId());
        save();
        say(p, "You no longer trust " + nameOf(other) + ".", NamedTextColor.YELLOW);
        Player o = Bukkit.getPlayer(other);
        if (o != null) say(o, p.getName() + " no longer trusts you.", NamedTextColor.YELLOW);
    }

    void list(Player p) {
        Set<UUID> s = trusted.getOrDefault(p.getUniqueId(), Set.of());
        if (s.isEmpty()) { say(p, "You don't trust anyone. Use /trust <player>.", NamedTextColor.GRAY); return; }
        List<String> names = new ArrayList<>();
        for (UUID u : s) names.add(nameOf(u));
        say(p, "Trusted (" + s.size() + "/" + max() + "): " + String.join(", ", names), NamedTextColor.GOLD);
    }

    List<String> tab(CommandSender s, String cmd, String[] a) {
        if (!(s instanceof Player p)) return List.of();
        List<String> out = new ArrayList<>();
        if (cmd.equals("untrust") || (cmd.equals("trust") && a.length == 2 && a[0].equalsIgnoreCase("remove"))) {
            for (UUID u : trusted.getOrDefault(p.getUniqueId(), Set.of())) out.add(nameOf(u));
            return out;
        }
        if (cmd.equals("trust") && a.length == 2 && (a[0].equalsIgnoreCase("accept") || a[0].equalsIgnoreCase("deny"))) {
            Map<UUID, Long> m = pending.getOrDefault(p.getUniqueId(), Map.of());
            for (UUID u : m.keySet()) out.add(nameOf(u));
            return out;
        }
        if (cmd.equals("trust") && a.length == 1) {
            out.addAll(List.of("accept", "deny", "list", "remove"));
            for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p)) out.add(o.getName());
        }
        return out;
    }
}

/** Handlers that use newer/optional Paper events, kept apart so a missing class can never disable the whole plugin. */
final class Opt implements Listener {
    final MarblesPlugin pl;
    Opt(MarblesPlugin pl) { this.pl = pl; }

    @EventHandler public void onStunJump(com.destroystokyo.paper.event.player.PlayerJumpEvent e) {
        if (pl.isStunned(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler public void onPackStatus(PlayerResourcePackStatusEvent e) {
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> pl.packLoaded.add(e.getPlayer().getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> pl.packLoaded.remove(e.getPlayer().getUniqueId());
            default -> { }
        }
    }
}
