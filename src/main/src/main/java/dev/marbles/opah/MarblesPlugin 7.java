package dev.marbles.opah;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.*;

public final class MarblesPlugin extends JavaPlugin implements Listener {
    static MarblesPlugin I;
    static final Key FONT = Key.key("marbles", "hud");
    static final String BACK = "\uE0F0", BLANK = "\uE0F1", SEP = "\uE0F2", LOCK = "\uE020";
    static final char D0 = 0xE100, G_S = 0xE10A, G_CHECK = 0xE10B, G_E = 0xE10C, G_M = 0xE10D, G_H = 0xE10E;

    NamespacedKey kElement, kEnergy, kItem, kKb, kHud, kEquipped, kBase;
    final Set<UUID> packLoaded = new HashSet<>();
    final Map<UUID, Map<Element, long[]>> cd = new HashMap<>();
    long[] cds(Player p, Element el) { return cd.computeIfAbsent(p.getUniqueId(), k -> new EnumMap<>(Element.class)).computeIfAbsent(el, k -> new long[2]); }
    final Map<UUID, Long> disabled = new HashMap<>(), immobile = new HashMap<>(), immune = new HashMap<>(), rift = new HashMap<>();
    final Map<UUID, double[]> leech = new HashMap<>();
    final Map<String, Long> pcd = new HashMap<>();
    final Map<UUID, Integer> hits = new HashMap<>();
    final Set<Block> temp = new HashSet<>();
    final Map<UUID, Long> stunned = new HashMap<>(), buffBlock = new HashMap<>();
    final Set<ArmorStand> clones = new HashSet<>();
    boolean allowBuff = false;
    Extras ex;
    Trust trust;

    @Override public void onEnable() {
        I = this;
        try {
            saveDefaultConfig();
            kElement = new NamespacedKey(this, "element");
            kEnergy = new NamespacedKey(this, "energy");
            kItem = new NamespacedKey(this, "item");
            kKb = new NamespacedKey(this, "mountain_heart");
            kHud = new NamespacedKey(this, "hud_mode");
            kEquipped = new NamespacedKey(this, "equipped_element");
            kBase = new NamespacedKey(this, "base_element");
            trust = new Trust(this);
            Bukkit.getPluginManager().registerEvents(this, this);
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.SEVERE, "MarblesOPAH core failed to start", t);
        }
        try { Bukkit.getPluginManager().registerEvents(new Opt(this), this); }
        catch (Throwable t) { getLogger().log(java.util.logging.Level.WARNING, "Optional handlers (resource pack status, stun jump lock) disabled", t); }
        try {
            ex = new Extras(this);
            Bukkit.getPluginManager().registerEvents(ex, this);
            ex.start();
        } catch (Throwable t) {
            ex = null;
            getLogger().log(java.util.logging.Level.SEVERE, "Dragon/Sculk/Death/Divine failed to start and are disabled; the rest of the plugin still works.", t);
        }
        try {
            Bukkit.getScheduler().runTaskTimer(this, this::hud, 20L, 2L);
            Bukkit.getScheduler().runTaskTimer(this, this::passives, 20L, 10L);
            getLogger().info("Enabled: " + Element.values().length + " elements, extras " + (ex != null ? "OK" : "FAILED")
                    + ", resource pack url " + (getConfig().getString("resource-pack.url", "").isBlank() ? "not set" : "set") + ".");
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.SEVERE, "MarblesOPAH tasks failed to start", t);
        }
    }

    @Override public void onDisable() {
        if (ex != null) ex.shutdown();
        for (Block b : temp) if (b.getType() != Material.AIR && b.getType().isSolid()) b.setType(Material.AIR);
        temp.clear();
        for (ArmorStand a : clones) a.remove();
        clones.clear();
    }

    // ---------- config shortcuts ----------
    int ci(String k) { return getConfig().getInt(k); }
    double ad(String k) { return Abilities.v(k); }
    boolean ab(String k) { return getConfig().getBoolean("abilities." + k); }
    int unlock(int slot) { return ci(slot == 0 ? "unlock-ability-1" : "unlock-ability-2"); }

    // ---------- data ----------
    int energy(Player p) { return p.getPersistentDataContainer().getOrDefault(kEnergy, PersistentDataType.INTEGER, ci("start-energy")); }

    void setEnergy(Player p, int v) {
        p.getPersistentDataContainer().set(kEnergy, PersistentDataType.INTEGER, v);
        if (v <= ci("deathban-energy")) deathban(p);
    }

    Element element(Player p) {
        String s = p.getPersistentDataContainer().get(kElement, PersistentDataType.STRING);
        return s == null ? null : Element.parse(s);
    }

    boolean hasPassive(Player p) { return element(p) != null && energy(p) >= ci("passive-min-energy"); }

    void deathban(Player p) {
        int min = ci("deathban-minutes");
        Date exp = min > 0 ? new Date(System.currentTimeMillis() + min * 60_000L) : null;
        p.getPersistentDataContainer().set(kEnergy, PersistentDataType.INTEGER, ci("start-energy"));
        Bukkit.getScheduler().runTask(this, () -> p.ban("\u00a7cYou ran out of energy and got deathbanned.", exp, "MarblesOPAH", true));
    }

    boolean pready(Player p, String key, double sec) {
        String k = p.getUniqueId() + key; long now = System.currentTimeMillis();
        if (pcd.getOrDefault(k, 0L) > now) return false;
        pcd.put(k, now + (long) (sec * 1000));
        return true;
    }

    // ---------- helpers used by abilities ----------
    boolean isStunned(Entity e) { Long u = stunned.get(e.getUniqueId()); return u != null && u > System.currentTimeMillis(); }

    /** Movement lock only (Molten Prison). */
    boolean immobilize(LivingEntity t, int ticks) { return lock(t, ticks, false); }

    /** Full stun: can't move, attack, cast or use items - except eating and potions. */
    boolean stun(LivingEntity t, int ticks) { return lock(t, ticks, true); }

    private boolean lock(LivingEntity t, int ticks, boolean full) {
        long now = System.currentTimeMillis();
        if (immune.getOrDefault(t.getUniqueId(), 0L) > now) return false;
        if (t instanceof Boss) ticks = (int) (ticks * getConfig().getDouble("crowd-control.boss-duration-multiplier", 0.5));
        long ms = ticks * 50L;
        immobile.put(t.getUniqueId(), now + ms);
        if (full) stunned.put(t.getUniqueId(), now + ms);
        immune.put(t.getUniqueId(), now + ms + (long) (getConfig().getDouble("crowd-control.immunity-seconds", 4) * 1000));
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 9, false, false, false));
        if (full && t instanceof Player tp)
            tp.showTitle(Title.title(Component.text("STUNNED", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text(String.format("%.1f seconds", ms / 1000.0), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(Math.max(300, ms)), Duration.ofMillis(300))));
        if (full && t instanceof Mob mob) {
            mob.setAware(false);
            Bukkit.getScheduler().runTaskLater(this, () -> { if (mob.isValid()) mob.setAware(true); }, ticks);
        }
        return true;
    }

    void leech(Player p, double dealt) {
        double[] ls = leech.get(p.getUniqueId());
        if (ls == null) return;
        if (ls[0] < System.currentTimeMillis()) { leech.remove(p.getUniqueId()); return; }
        double heal = Math.min(dealt * ad("nature.rebirth.leech-percent"), ad("nature.rebirth.leech-cap-hearts") * 2 - ls[1]);
        if (heal <= 0) return;
        ls[1] += heal;
        p.setHealth(Math.min(p.getAttribute(Attribute.MAX_HEALTH).getValue(), p.getHealth() + heal));
    }

    void eff(Player p, PotionEffectType t, int dur, int amp) {
        PotionEffect cur = p.getPotionEffect(t);
        if (cur != null && (cur.getAmplifier() > amp || (cur.getAmplifier() == amp && cur.getDuration() > dur))) return;
        p.addPotionEffect(new PotionEffect(t, dur, amp, true, false, true));
    }

    Component msg(String s, NamedTextColor c) { return Component.text(s, c); }

    // ---------- F / Shift+F ----------
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (element(p) == null) return;
        e.setCancelled(true);
        cast(p, p.isSneaking() ? 1 : 0);
    }

    void cast(Player p, int slot) {
        Element el = element(p);
        if (isStunned(p)) { p.sendMessage(msg("You are stunned!", NamedTextColor.RED)); return; }
        if (energy(p) < unlock(slot)) { p.sendMessage(msg("You need " + unlock(slot) + " energy to use " + el.ability(slot) + ".", NamedTextColor.RED)); return; }
        long now = System.currentTimeMillis();
        if (disabled.getOrDefault(p.getUniqueId(), 0L) > now) { p.sendMessage(msg("Your abilities are disabled!", NamedTextColor.RED)); return; }
        long[] c = cds(p, el);
        if (c[slot] > now) return;
        if (ex == null && el.ordinal() >= Element.DRAGON.ordinal()) { p.sendMessage(msg("That element is disabled (see console).", NamedTextColor.RED)); return; }
        if (el == Element.DRAGON && slot == 0) {            // F once = summon the dragon, F again = fire it
            if (ex.dragonReady(p)) {
                if (ex.dragonFire(p)) { c[slot] = now + el.cooldown(slot) * 1000L; Flair.cast(p, el, slot); }
            } else ex.dragonSummon(p);
            return;
        }
        if (el == Element.DEATH && slot == 1) { ex.openDoom(p); return; }   // cooldown starts when a target is picked
        if (Abilities.cast(p, el, slot)) { c[slot] = now + el.cooldown(slot) * 1000L; Flair.cast(p, el, slot); }
        else p.sendMessage(msg("No target in range.", NamedTextColor.GRAY));
    }

    // ---------- resource pack ----------
    void sendPack(Player p) {
        String url = getConfig().getString("resource-pack.url", "");
        if (url.isBlank() || !p.isOnline()) return;
        String sha = getConfig().getString("resource-pack.sha1", "").trim();
        byte[] hash = null;
        if (sha.length() == 40) {
            try { hash = new byte[20]; for (int i = 0; i < 20; i++) hash[i] = (byte) Integer.parseInt(sha.substring(i * 2, i * 2 + 2), 16); }
            catch (NumberFormatException x) { hash = null; }
        }
        Component prompt = Component.text(getConfig().getString("resource-pack.prompt", "Marbles OP AH icons and items"));
        try { p.setResourcePack(url, hash, prompt, getConfig().getBoolean("resource-pack.force", false)); }
        catch (Throwable t) { getLogger().warning("Could not send the resource pack: " + t.getMessage()); }
    }

    @EventHandler public void onQuitPack(PlayerQuitEvent e) { packLoaded.remove(e.getPlayer().getUniqueId()); }

    /** Icons need the pack; players without it get a plain-text HUD. /hud icons|text|auto overrides per player. */
    boolean icons(Player p) {
        String m = p.getPersistentDataContainer().getOrDefault(kHud, PersistentDataType.STRING, "auto");
        if (m.equals("icons")) return true;
        if (m.equals("text")) return false;
        String g = getConfig().getString("hud.mode", "auto");
        if (g.equals("icons")) return true;
        if (g.equals("text")) return false;
        return packLoaded.contains(p.getUniqueId());
    }

    boolean customModel() {
        String v = getConfig().getString("items.use-custom-model", "auto");
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
        return !getConfig().getString("resource-pack.url", "").isBlank();
    }

    String fmtTime(int sec) { return sec <= 99 ? sec + "s" : sec <= 99 * 60 ? (int) Math.ceil(sec / 60.0) + "m" : (int) Math.ceil(sec / 3600.0) + "h"; }

    Component textCell(Player p, Element el, int slot) {
        String key = slot == 0 ? "F" : "Shift+F", name = el.ability(slot);
        if (energy(p) < unlock(slot)) return Component.text("[" + key + "] " + name + " (needs " + unlock(slot) + " energy)", NamedTextColor.DARK_GRAY);
        long left = cds(p, el)[slot] - System.currentTimeMillis();
        if (left > 0) return Component.text("[" + key + "] " + name + " " + fmtTime((int) Math.ceil(left / 1000.0)), NamedTextColor.YELLOW);
        return Component.text("[" + key + "] " + name + " READY", NamedTextColor.GREEN);
    }

    // ---------- HUD ----------
    void hud() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Element el = element(p);
            if (el == null) continue;
            int en = energy(p);
            if (!icons(p)) {
                p.sendActionBar(Component.text().append(textCell(p, el, 0)).append(Component.text("  |  ", NamedTextColor.DARK_GRAY)).append(textCell(p, el, 1))
                        .append(Component.text("   " + en + " energy", en < 0 ? NamedTextColor.RED : NamedTextColor.GOLD)).build());
                continue;
            }
            p.sendActionBar(Component.text().append(cell(p, el, 0)).append(cell(p, el, 1))
                    .append(Component.text(" " + en + " energy", en < 0 ? NamedTextColor.RED : NamedTextColor.GOLD)).build());
        }
    }

    String num(int n, char suffix) {
        n = Math.max(0, Math.min(99, n));
        return (n >= 10 ? String.valueOf((char) (D0 + n / 10)) : BLANK) + (char) (D0 + n % 10) + suffix;
    }

    Component cell(Player p, Element el, int slot) {
        String icon, digits; TextColor ic, dc;
        if (energy(p) < unlock(slot)) {
            icon = LOCK; ic = TextColor.color(0x808080); dc = NamedTextColor.RED; digits = num(unlock(slot), G_E);
        } else {
            long left = cds(p, el)[slot] - System.currentTimeMillis();
            icon = String.valueOf((char) (0xE000 + slot * 0x10 + el.ordinal()));
            if (left > 0) { ic = TextColor.color(0x555555); dc = NamedTextColor.YELLOW; int sec = (int) Math.ceil(left / 1000.0); digits = sec <= 99 ? num(sec, G_S) : sec <= 99 * 60 ? num((int) Math.ceil(sec / 60.0), G_M) : num((int) Math.ceil(sec / 3600.0), G_H); }
            else { ic = NamedTextColor.WHITE; dc = NamedTextColor.GREEN; digits = BLANK + G_CHECK + BLANK; }
        }
        return Component.text().font(FONT).append(Component.text(icon, ic)).append(Component.text(BACK))
                .append(Component.text(digits, dc)).append(Component.text(SEP)).build();
    }

    // ---------- passives ----------
    void passives() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Element el = element(p);
            boolean on = el != null && energy(p) >= ci("passive-min-energy") && !p.isDead();
            AttributeInstance kb = p.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                boolean want = on && el == Element.EARTH, has = kb.getModifier(kKb) != null;
                if (want && !has) kb.addModifier(new AttributeModifier(kKb, ad("earth.passive.knockback-reduction"), AttributeModifier.Operation.ADD_NUMBER));
                else if (!want && has) kb.removeModifier(kKb);
            }
            if (!on) continue;
            String pp = el.passivePath();
            switch (el) {
                case FIRE, MAGMA -> { if (ab(pp + ".fire-immunity")) eff(p, PotionEffectType.FIRE_RESISTANCE, 40, 0); }
                case WATER -> {
                    if (ab(pp + ".water-breathing")) eff(p, PotionEffectType.WATER_BREATHING, 40, 0);
                    int g = (int) ad(pp + ".dolphins-grace-level");
                    if (g > 0 && (p.isInWater() || p.isSwimming())) eff(p, PotionEffectType.DOLPHINS_GRACE, 40, g - 1);
                }
                case LIGHTNING, WIND -> { int l = (int) ad(pp + ".speed-level"); if (l > 0) eff(p, PotionEffectType.SPEED, 40, l - 1); }
                case EARTH -> { int l = (int) ad(pp + ".resistance-level"); if (l > 0) eff(p, PotionEffectType.RESISTANCE, 40, l - 1); }
                case VOID -> {
                    if (ab(pp + ".night-vision")) eff(p, PotionEffectType.NIGHT_VISION, 260, 0);
                    if (ab(pp + ".blindness-immunity")) p.removePotionEffect(PotionEffectType.BLINDNESS);
                }
                case LIGHT -> { if (ab(pp + ".night-vision")) eff(p, PotionEffectType.NIGHT_VISION, 260, 0); }
                case NATURE -> { int l = (int) ad(pp + ".hero-of-the-village-level"); if (l > 0) eff(p, PotionEffectType.HERO_OF_THE_VILLAGE, 40, l - 1); }
                case DRAGON, SCULK, DEATH, DIVINE -> { if (ex != null) ex.std(p, el); }
                default -> { }
            }
        }
    }

    // ---------- combat ----------
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity dmgr = e.getDamager();
        if (dmgr instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) dmgr = shooter;
        if (isStunned(dmgr)) { e.setCancelled(true); return; }
        if (trust.blocksMelee(dmgr, e.getEntity())) { e.setCancelled(true); return; }
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity t)) return;
        Element el = element(p);
        if (el == null) return;
        UUID id = p.getUniqueId();
        String pp = el.passivePath();
        if (hasPassive(p)) {
            switch (el) {
                case FIRE -> { if (Math.random() < ad(pp + ".ignite-chance")) t.setFireTicks(Abilities.ticks(pp + ".ignite-seconds")); }
                case LIGHTNING -> {
                    int n = hits.merge(id, 1, Integer::sum);
                    if (n >= (int) ad(pp + ".hits-required") && pready(p, "bolt", ad(pp + ".internal-cooldown"))) { hits.put(id, 0); Abilities.passiveBolt(p, t); }
                }
                case LIGHT -> { if (t.hasPotionEffect(PotionEffectType.BLINDNESS)) e.setDamage(e.getDamage() * ad(pp + ".damage-multiplier")); }
                default -> { }
            }
        }
        Long rb = rift.remove(id);
        if (rb != null && rb > System.currentTimeMillis()) Abilities.bonusHit(p, t, ad("void.riftwalk.bonus-damage"));
        leech(p, e.getFinalDamage());
    }

    @EventHandler(ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !hasPassive(p)) return;
        Element el = element(p); String pp = el.passivePath();
        switch (el) {
            case WIND -> { if (ab(pp + ".fall-immunity") && e.getCause() == EntityDamageEvent.DamageCause.FALL) e.setCancelled(true); }
            case ICE -> { if (pready(p, "frozen", ad(pp + ".internal-cooldown"))) eff(p, PotionEffectType.RESISTANCE, Abilities.ticks(pp + ".resistance-seconds"), (int) ad(pp + ".resistance-level") - 1); }
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void onMove(PlayerMoveEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Long u = immobile.get(id);
        if (u == null) return;
        if (u < System.currentTimeMillis()) { immobile.remove(id); return; }
        Location f = e.getFrom(), t = e.getTo();
        if (t != null && (f.getX() != t.getX() || f.getZ() != t.getZ() || t.getY() > f.getY())) {
            Location n = f.clone(); n.setYaw(t.getYaw()); n.setPitch(t.getPitch()); e.setTo(n);
        }
    }

    @EventHandler public void onBreak(BlockBreakEvent e) { if (temp.contains(e.getBlock())) e.setCancelled(true); }

    // ---------- energy: kills / deaths / join ----------
    @EventHandler public void onDeath(PlayerDeathEvent e) {
        Player v = e.getEntity(), k = v.getKiller();
        String eq = v.getPersistentDataContainer().get(kEquipped, PersistentDataType.STRING);
        if (eq != null) {
            Element oe = Element.parse(eq);
            restoreBase(v);
            if (oe != null) v.getWorld().dropItemNaturally(v.getLocation(), elementItem(oe));
            v.sendMessage(msg("Your " + (oe == null ? "" : oe.display + " ") + "orb dropped. You're back to " + element(v).display + ".", NamedTextColor.GOLD));
        }
        if (k != null && k != v) setEnergy(k, Math.min(ci("max-energy"), energy(k) + ci("kill-energy-gain")));
        setEnergy(v, energy(v) - ci("death-energy-loss"));
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boolean first = element(p) == null;
        if (first) {
            p.getPersistentDataContainer().set(kEnergy, PersistentDataType.INTEGER, ci("start-energy"));
            Element el = reroll(p, false);
            p.sendMessage(msg("You are bound to " + el.display + ". F = " + el.a1 + ", Shift+F = " + el.a2 + ".", NamedTextColor.GOLD));
        }
        Bukkit.getScheduler().runTaskLater(this, () -> sendPack(p), 40L);
        Element mine = element(p);
        if (getConfig().getBoolean("join-animation.enabled", true) && (first || getConfig().getBoolean("join-animation.every-join", true)))
            Bukkit.getScheduler().runTaskLater(this, () -> playRoll(p, mine), ci("join-animation.start-delay-ticks"));
    }

    static final NamedTextColor[] COLORS = {NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.LIGHT_PURPLE, NamedTextColor.GOLD,
            NamedTextColor.AQUA, NamedTextColor.DARK_AQUA, NamedTextColor.DARK_PURPLE, NamedTextColor.GREEN, NamedTextColor.YELLOW, NamedTextColor.DARK_RED,
            NamedTextColor.DARK_PURPLE, NamedTextColor.DARK_AQUA, NamedTextColor.DARK_RED, NamedTextColor.GOLD};

    /** Slot-machine style roll: the element icons flip by on screen, slow down, and land on the result. */
    void playRoll(Player p, Element result) {
        if (!p.isOnline() || result == null) return;
        int[] gaps = {2, 2, 2, 2, 3, 3, 3, 4, 4, 5, 6, 7, 9};
        Element[] all = Element.rollables().toArray(new Element[0]); Random rnd = new Random(); Element[] last = {null};
        long t = 0;
        for (int i = 0; i < gaps.length; i++) {
            t += gaps[i];
            final int idx = i; final boolean fin = i == gaps.length - 1;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!p.isOnline()) return;
                Element show = result;
                if (!fin) do { show = all[rnd.nextInt(all.length)]; } while (show == last[0] || (idx == gaps.length - 2 && show == result));
                last[0] = show;
                Component icon = icons(p) ? Component.text(String.valueOf((char) (0xE000 + show.ordinal())), NamedTextColor.WHITE).font(FONT)
                        : Component.text(show.display, COLORS[show.ordinal()], TextDecoration.BOLD);
                Component sub = fin
                        ? Component.text("You are bound to ", NamedTextColor.GRAY).append(Component.text(show.display, COLORS[show.ordinal()], TextDecoration.BOLD))
                        : Component.text("Rolling...", NamedTextColor.GRAY);
                p.showTitle(Title.title(icon, sub, Title.Times.times(Duration.ZERO, Duration.ofMillis(fin ? 2500 : 600), Duration.ofMillis(fin ? 600 : 0))));
                if (fin) {
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f);
                    p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.3);
                } else p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 0.8f + 0.05f * idx);
            }, t);
        }
    }

    Element reroll(Player p, boolean avoidSame) {
        var pdc = p.getPersistentDataContainer();
        boolean wearing = pdc.has(kEquipped, PersistentDataType.STRING);
        Element cur = wearing ? Element.parse(pdc.getOrDefault(kBase, PersistentDataType.STRING, "")) : element(p);
        List<Element> pool = new ArrayList<>(Element.rollables());
        if (avoidSame && getConfig().getBoolean("reroll.avoid-same", true) && cur != null) pool.remove(cur);
        Element n = pool.get(new Random().nextInt(pool.size()));
        pdc.set(wearing ? kBase : kElement, PersistentDataType.STRING, n.name());
        if (wearing) p.sendMessage(msg("Your normal element is now " + n.display + ". You keep your equipped element until you take it off or die.", NamedTextColor.GRAY));
        if (getConfig().getBoolean("reroll.reset-cooldowns", true)) cd.remove(p.getUniqueId());
        return n;
    }

    // ---------- equippable element orbs (Dragon / Sculk / Death / Divine) ----------
    void giveOrDrop(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
    }

    ItemStack elementItem(Element el) { return item("elem_" + el.key(), 1); }

    String giveType(String s) {
        s = s.toLowerCase();
        return List.of("dragon", "sculk", "death", "divine").contains(s) ? "elem_" + s : s;
    }

    /** Right-click an orb: it replaces your current element until you die (or /unequip). */
    void equip(Player p, ItemStack it, Element el) {
        if (el == null || el.rollable) return;
        var pdc = p.getPersistentDataContainer();
        Element cur = element(p);
        if (cur == el) { p.sendMessage(msg("You already have the " + el.display + " element.", NamedTextColor.GRAY)); return; }
        String old = pdc.get(kEquipped, PersistentDataType.STRING);
        if (old == null) pdc.set(kBase, PersistentDataType.STRING, (cur == null ? Element.FIRE : cur).name());   // remember your normal element
        else { Element oe = Element.parse(old); if (oe != null) giveOrDrop(p, elementItem(oe)); }               // swapping: hand the old orb back
        pdc.set(kEquipped, PersistentDataType.STRING, el.name());
        pdc.set(kElement, PersistentDataType.STRING, el.name());
        it.setAmount(it.getAmount() - 1);
        if (getConfig().getBoolean("join-animation.play-on-reroll", true)) playRoll(p, el);
        p.sendMessage(msg("You equipped the " + el.display + " element (F = " + el.a1 + ", Shift+F = " + el.a2 + "). It drops if you die.", COLORS[el.ordinal()]));
    }

    void restoreBase(Player p) {
        var pdc = p.getPersistentDataContainer();
        String base = pdc.get(kBase, PersistentDataType.STRING);
        pdc.remove(kEquipped); pdc.remove(kBase);
        Element b = base == null ? null : Element.parse(base);
        if (b == null) b = Element.rollables().get(new Random().nextInt(Element.rollables().size()));
        pdc.set(kElement, PersistentDataType.STRING, b.name());
    }

    /** Takes the equipped orb off and hands it back. */
    boolean unequip(Player p) {
        String eq = p.getPersistentDataContainer().get(kEquipped, PersistentDataType.STRING);
        if (eq == null) return false;
        Element oe = Element.parse(eq);
        restoreBase(p);
        if (oe != null) giveOrDrop(p, elementItem(oe));
        return true;
    }

    // ---------- items: Energy Marble / Element Reroller ----------
    ItemStack item(String type, int n) {
        Material mat = Material.matchMaterial(getConfig().getString("items.material", "PLAYER_HEAD"));
        ItemStack it = new ItemStack(mat == null ? Material.PLAYER_HEAD : mat, n);
        ItemMeta m = it.getItemMeta();
        String name, lore, model; NamedTextColor col;
        switch (type) {
            case "reroller" -> { name = "Element Reroller"; lore = "Right-click to reroll your element."; model = "element_reroller"; col = NamedTextColor.LIGHT_PURPLE; }
            default -> {
                Element oe = type.startsWith("elem_") ? Element.parse(type.substring(5)) : null;
                if (oe != null) { name = oe.display + " Orb"; lore = "Right-click to equip the " + oe.display + " element."; model = "element_" + oe.key(); col = COLORS[oe.ordinal()]; }
                else { name = "Energy Marble"; lore = "Right-click to gain 1 energy."; model = "energy_marble"; col = NamedTextColor.AQUA; }
            }
        }
        m.displayName(Component.text(name, col).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (type.startsWith("elem_")) {
            lines.add(Component.text("Replaces your current element.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lines.add(Component.text("Drops when you die.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            m.setEnchantmentGlintOverride(true);
        }
        m.lore(lines);
        m.getPersistentDataContainer().set(kItem, PersistentDataType.STRING, type);
        if (customModel()) m.setItemModel(new NamespacedKey("marbles", model));
        it.setItemMeta(m);
        return it;
    }

    String itemType(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(kItem, PersistentDataType.STRING);
    }

    @EventHandler public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || isStunned(e.getPlayer())) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        String type = itemType(it);
        if (type == null) return;
        e.setCancelled(true); // never place the head
        Player p = e.getPlayer();
        if (type.startsWith("elem_")) { equip(p, it, Element.parse(type.substring(5))); return; }
        switch (type) {
            case "energy" -> {
                if (energy(p) >= ci("max-energy")) { p.sendMessage(msg("You're at max energy.", NamedTextColor.RED)); return; }
                it.setAmount(it.getAmount() - 1);
                setEnergy(p, energy(p) + 1);
                p.sendMessage(msg("Energy: " + energy(p), NamedTextColor.GREEN));
            }
            case "reroller" -> {
                it.setAmount(it.getAmount() - 1);
                Element n = reroll(p, true);
                if (getConfig().getBoolean("join-animation.play-on-reroll", true)) playRoll(p, n);
                p.sendMessage(msg("Rerolled! You are now " + n.display + " (F = " + n.a1 + ", Shift+F = " + n.a2 + ").", NamedTextColor.LIGHT_PURPLE));
            }
            default -> { }
        }
    }

    // ---------- stun rules: can't move/attack/cast; CAN eat and use potions ----------
    @EventHandler public void onStunInteract(PlayerInteractEvent e) {
        if (!isStunned(e.getPlayer())) return;
        e.setUseInteractedBlock(Event.Result.DENY);
        ItemStack it = e.getItem();
        Material m = it == null ? Material.AIR : it.getType();
        boolean ok = m == Material.POTION || m == Material.SPLASH_POTION || m == Material.LINGERING_POTION
                || (m.isEdible() && m != Material.CHORUS_FRUIT);
        if (!ok) e.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler public void onStunLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Entity s && isStunned(s) && !(e.getEntity() instanceof ThrownPotion)) e.setCancelled(true);
    }

    @EventHandler public void onStunVelocity(PlayerVelocityEvent e) { if (isStunned(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onStunShoot(EntityShootBowEvent e) { if (isStunned(e.getEntity())) e.setCancelled(true); }
    @EventHandler public void onStunBreak(BlockBreakEvent e) { if (isStunned(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onStunPlace(BlockPlaceEvent e) { if (isStunned(e.getPlayer())) e.setCancelled(true); }

    @EventHandler public void onStunTeleport(PlayerTeleportEvent e) {
        if (!isStunned(e.getPlayer())) return;
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || e.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) e.setCancelled(true);
    }

    /** Divine Smite: while buffs are "removed", new beneficial effects can't be applied. */
    @EventHandler(ignoreCancelled = true) public void onBuffBlock(EntityPotionEffectEvent e) {
        if (allowBuff || !(e.getEntity() instanceof LivingEntity le)) return;
        Long u = buffBlock.get(le.getUniqueId());
        if (u == null || u < System.currentTimeMillis()) return;
        EntityPotionEffectEvent.Action act = e.getAction();
        if (act != EntityPotionEffectEvent.Action.ADDED && act != EntityPotionEffectEvent.Action.CHANGED) return;
        PotionEffect ne = e.getNewEffect();
        if (ne == null || !Abilities.STRIP.contains(ne.getType()) || e.getCause() == EntityPotionEffectEvent.Cause.TOTEM) return;
        e.setCancelled(true);
    }

    // ---------- commands ----------
    Player who(CommandSender s, String[] a, int idx) {
        if (a.length > idx) return Bukkit.getPlayer(a[idx]);
        return s instanceof Player sp ? sp : null;
    }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        String cmd = c.getName().toLowerCase();
        if (cmd.equals("unequip")) {
            if (!(s instanceof Player p)) return true;
            if (!getConfig().getBoolean("equip.allow-unequip", true)) { p.sendMessage(msg("Unequipping is disabled.", NamedTextColor.RED)); return true; }
            if (unequip(p)) p.sendMessage(msg("You took off the orb. You're back to " + element(p).display + ".", NamedTextColor.GOLD));
            else p.sendMessage(msg("You don't have an element orb equipped.", NamedTextColor.GRAY));
            return true;
        }
        if (cmd.equals("hud")) {
            if (!(s instanceof Player p)) return true;
            String mode = a.length > 0 ? a[0].toLowerCase() : "";
            if (!List.of("icons", "text", "auto").contains(mode)) { p.sendMessage(msg("Usage: /hud <icons|text|auto>   (now: " + (icons(p) ? "icons" : "text") + ")", NamedTextColor.GOLD)); return true; }
            p.getPersistentDataContainer().set(kHud, PersistentDataType.STRING, mode);
            p.sendMessage(msg("HUD mode: " + mode, NamedTextColor.GREEN));
            return true;
        }
        if (cmd.equals("trust") || cmd.equals("untrust") || cmd.equals("trustlist")) return trust.command(s, cmd, a);
        if (cmd.equals("withdraw")) {
            if (!(s instanceof Player p)) return true;
            int n = 1;
            if (a.length > 0) { try { n = Integer.parseInt(a[0]); } catch (NumberFormatException x) { n = 0; } }
            if (n < 1 || n > 64) { p.sendMessage(msg("Usage: /withdraw [1-64]", NamedTextColor.RED)); return true; }
            if (energy(p) - n < ci("withdraw-min-energy")) { p.sendMessage(msg("You can't withdraw below " + ci("withdraw-min-energy") + " energy.", NamedTextColor.RED)); return true; }
            setEnergy(p, energy(p) - n);
            p.getInventory().addItem(item("energy", n)).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
            p.sendMessage(msg("Withdrew " + n + " energy. Now at " + energy(p) + ".", NamedTextColor.GOLD));
            return true;
        }
        if (cmd.equals("reroll")) {
            if (!(s instanceof Player p)) return true;
            Element n = reroll(p, true);
                if (getConfig().getBoolean("join-animation.play-on-reroll", true)) playRoll(p, n);
            p.sendMessage(msg("Rerolled! You are now " + n.display + " (F = " + n.a1 + ", Shift+F = " + n.a2 + ").", NamedTextColor.LIGHT_PURPLE));
            return true;
        }
        if (a.length < 1) return false;
        switch (a[0].toLowerCase()) {
            case "reload" -> { reloadConfig(); s.sendMessage("Config reloaded."); }
            case "status" -> {
                Player t = who(s, a, 1);
                if (t == null) { s.sendMessage("Usage: /marbles status [player]"); return true; }
                Element el = element(t);
                long now = System.currentTimeMillis(); long[] c2 = el == null ? new long[2] : cds(t, el);
                s.sendMessage("Marbles v" + getDescription().getVersion() + " | extras " + (ex != null ? "OK" : "FAILED") + " | " + Element.values().length + " elements");
                s.sendMessage(t.getName() + ": element " + (el == null ? "none" : el.display) + ", energy " + energy(t) + ", passive " + (hasPassive(t) ? "on" : "off")
                        + ", F cd " + Math.max(0, (c2[0] - now) / 1000) + "s, Shift+F cd " + Math.max(0, (c2[1] - now) / 1000) + "s");
                s.sendMessage("Pack loaded: " + packLoaded.contains(t.getUniqueId()) + ", hud: " + (icons(t) ? "icons" : "text")
                        + ", pack url " + (getConfig().getString("resource-pack.url", "").isBlank() ? "not set" : "set") + ", stunned: " + isStunned(t));
            }
            case "reroll" -> {
                Player t = who(s, a, 1);
                if (t == null) { s.sendMessage("Player not found."); return true; }
                Element n = reroll(t, true);
                if (getConfig().getBoolean("join-animation.play-on-reroll", true)) playRoll(t, n);
                s.sendMessage(t.getName() + " is now " + n.display + ".");
                t.sendMessage(msg("Your element was rerolled: " + n.display + ".", NamedTextColor.LIGHT_PURPLE));
            }
            case "element" -> {
                Element el = a.length > 1 ? Element.parse(a[1]) : null;
                Player t = who(s, a, 2);
                if (el == null || t == null) { s.sendMessage("Usage: /marbles element <element> [player]"); return true; }
                t.getPersistentDataContainer().set(kElement, PersistentDataType.STRING, el.name());
                t.getPersistentDataContainer().remove(kEquipped); t.getPersistentDataContainer().remove(kBase);
                s.sendMessage(t.getName() + " is now " + el.display + ".");
            }
            case "energy" -> {
                if (a.length < 3) return false;
                Player t = who(s, a, 3); int v;
                try { v = Integer.parseInt(a[2]); } catch (NumberFormatException x) { return false; }
                if (t == null) { s.sendMessage("Player not found."); return true; }
                setEnergy(t, a[1].equalsIgnoreCase("add") ? energy(t) + v : v);
                s.sendMessage(t.getName() + " energy: " + energy(t));
            }
            case "give" -> {
                if (a.length < 2 || !List.of("energy", "reroller", "dragon", "sculk", "death", "divine").contains(a[1].toLowerCase())) { s.sendMessage("Usage: /marbles give <energy|reroller|dragon|sculk|death|divine> [amount] [player]"); return true; }
                int n = 1;
                if (a.length > 2) { try { n = Math.max(1, Math.min(64, Integer.parseInt(a[2]))); } catch (NumberFormatException x) { n = 1; } }
                Player t = who(s, a, 3);
                if (t == null) { s.sendMessage("Player not found."); return true; }
                t.getInventory().addItem(item(giveType(a[1]), n)).values().forEach(i -> t.getWorld().dropItemNaturally(t.getLocation(), i));
                s.sendMessage("Gave " + n + " " + a[1] + " to " + t.getName() + ".");
            }
            case "stun" -> {
                Player t = a.length > 1 ? Bukkit.getPlayer(a[1]) : null;
                if (t == null) { s.sendMessage("Usage: /marbles stun <player> [seconds]"); return true; }
                double sec = 3; if (a.length > 2) { try { sec = Double.parseDouble(a[2]); } catch (NumberFormatException x) { sec = 3; } }
                boolean ok = stun(t, (int) Math.round(sec * 20));
                s.sendMessage(ok ? "Stunned " + t.getName() + " for " + sec + "s." : t.getName() + " is stun-immune right now.");
            }
            case "revive" -> {
                if (a.length < 2) return false;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pardon " + a[1]);
                s.sendMessage("Pardoned " + a[1] + ".");
            }
            default -> { return false; }
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        String cn = c.getName().toLowerCase();
        if (cn.equals("trust") || cn.equals("untrust")) return trust.tab(s, cn, a);
        if (cn.equals("hud")) return a.length == 1 ? List.of("icons", "text", "auto") : List.of();
        if (!c.getName().equalsIgnoreCase("marbles")) return List.of();
        if (a.length == 1) return List.of("element", "energy", "give", "reroll", "stun", "status", "revive", "reload");
        if (a.length == 2 && a[0].equalsIgnoreCase("element")) return Arrays.stream(Element.values()).map(x -> x.name().toLowerCase()).toList();
        if (a.length == 2 && a[0].equalsIgnoreCase("energy")) return List.of("set", "add");
        if (a.length == 2 && a[0].equalsIgnoreCase("give")) return List.of("energy", "reroller", "dragon", "sculk", "death", "divine");
        return null;
    }
}
