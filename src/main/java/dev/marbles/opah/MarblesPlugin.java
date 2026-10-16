package dev.marbles.opah;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.entity.Boss;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public final class MarblesPlugin extends JavaPlugin implements Listener {
    static MarblesPlugin I;
    static final Key FONT = Key.key("marbles", "hud");
    static final String BACK = "\uE0F0", BLANK = "\uE0F1", SEP = "\uE0F2", LOCK = "\uE020";
    static final char D0 = 0xE100, G_S = 0xE10A, G_CHECK = 0xE10B, G_E = 0xE10C;

    NamespacedKey kElement, kEnergy, kItem, kKb;
    final Map<UUID, long[]> cd = new HashMap<>();
    final Map<UUID, Long> disabled = new HashMap<>(), immobile = new HashMap<>(), immune = new HashMap<>(), rift = new HashMap<>();
    final Map<UUID, double[]> leech = new HashMap<>();
    final Map<String, Long> pcd = new HashMap<>();
    final Map<UUID, Integer> hits = new HashMap<>();
    final Set<Block> temp = new HashSet<>();

    @Override public void onEnable() {
        I = this;
        saveDefaultConfig();
        kElement = new NamespacedKey(this, "element");
        kEnergy = new NamespacedKey(this, "energy");
        kItem = new NamespacedKey(this, "item");
        kKb = new NamespacedKey(this, "mountain_heart");
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::hud, 20L, 2L);
        Bukkit.getScheduler().runTaskTimer(this, this::passives, 20L, 10L);
    }

    @Override public void onDisable() {
        for (Block b : temp) if (b.getType() != Material.AIR && b.getType().isSolid()) b.setType(Material.AIR);
        temp.clear();
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
    boolean root(LivingEntity t, int ticks) {
        long now = System.currentTimeMillis();
        if (immune.getOrDefault(t.getUniqueId(), 0L) > now) return false;
        if (t instanceof Boss) ticks = (int) (ticks * getConfig().getDouble("crowd-control.boss-duration-multiplier", 0.5));
        long ms = ticks * 50L;
        immobile.put(t.getUniqueId(), now + ms);
        immune.put(t.getUniqueId(), now + ms + (long) (getConfig().getDouble("crowd-control.immunity-seconds", 4) * 1000));
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 9, false, false, false));
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
        if (energy(p) < unlock(slot)) { p.sendMessage(msg("You need " + unlock(slot) + " energy to use " + el.ability(slot) + ".", NamedTextColor.RED)); return; }
        long now = System.currentTimeMillis();
        if (disabled.getOrDefault(p.getUniqueId(), 0L) > now) { p.sendMessage(msg("Your abilities are disabled!", NamedTextColor.RED)); return; }
        long[] c = cd.computeIfAbsent(p.getUniqueId(), k -> new long[2]);
        if (c[slot] > now) return;
        if (Abilities.cast(p, el, slot)) c[slot] = now + el.cooldown(slot) * 1000L;
        else p.sendMessage(msg("No target in range.", NamedTextColor.GRAY));
    }

    // ---------- HUD ----------
    void hud() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Element el = element(p);
            if (el == null) continue;
            int en = energy(p);
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
            long left = cd.getOrDefault(p.getUniqueId(), new long[2])[slot] - System.currentTimeMillis();
            icon = String.valueOf((char) (0xE000 + slot * 0x10 + el.ordinal()));
            if (left > 0) { ic = TextColor.color(0x555555); dc = NamedTextColor.YELLOW; digits = num((int) Math.ceil(left / 1000.0), G_S); }
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
                default -> { }
            }
        }
    }

    // ---------- combat ----------
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent e) {
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
            case MAGMA -> { if (pready(p, "core", ad(pp + ".internal-cooldown"))) eff(p, PotionEffectType.STRENGTH, Abilities.ticks(pp + ".strength-seconds"), (int) ad(pp + ".strength-level") - 1); }
            case NATURE -> {
                if (p.getHealth() - e.getFinalDamage() < ad(pp + ".threshold-hearts") * 2 && pready(p, "life", ad(pp + ".internal-cooldown")))
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Abilities.ticks(pp + ".regen-seconds"), (int) ad(pp + ".regen-level") - 1));
            }
            default -> { }
        }
    }

    @EventHandler public void onMove(PlayerMoveEvent e) {
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
        if (k != null && k != v) setEnergy(k, Math.min(ci("max-energy"), energy(k) + ci("kill-energy-gain")));
        setEnergy(v, energy(v) - ci("death-energy-loss"));
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (element(p) != null) return;
        p.getPersistentDataContainer().set(kEnergy, PersistentDataType.INTEGER, ci("start-energy"));
        Element el = reroll(p, false);
        p.sendMessage(msg("You are bound to " + el.display + ". F = " + el.a1 + ", Shift+F = " + el.a2 + ".", NamedTextColor.GOLD));
    }

    Element reroll(Player p, boolean avoidSame) {
        Element cur = element(p);
        List<Element> pool = new ArrayList<>(List.of(Element.values()));
        if (avoidSame && getConfig().getBoolean("reroll.avoid-same", true) && cur != null) pool.remove(cur);
        Element n = pool.get(new Random().nextInt(pool.size()));
        p.getPersistentDataContainer().set(kElement, PersistentDataType.STRING, n.name());
        if (getConfig().getBoolean("reroll.reset-cooldowns", true)) cd.remove(p.getUniqueId());
        return n;
    }

    // ---------- items: Energy Marble / Element Reroller / Marble Trader ----------
    ItemStack item(String type, int n) {
        Material mat = Material.matchMaterial(getConfig().getString("items.material", "PLAYER_HEAD"));
        ItemStack it = new ItemStack(mat == null ? Material.PLAYER_HEAD : mat, n);
        ItemMeta m = it.getItemMeta();
        String name, lore, model; NamedTextColor col;
        switch (type) {
            case "reroller" -> { name = "Element Reroller"; lore = "Right-click to reroll your element."; model = "element_reroller"; col = NamedTextColor.LIGHT_PURPLE; }
            case "trader" -> { name = "Marble Trader"; lore = "Right-click to trade."; model = "marble_trader"; col = NamedTextColor.GOLD; }
            default -> { name = "Energy Marble"; lore = "Right-click to gain 1 energy."; model = "energy_marble"; col = NamedTextColor.AQUA; }
        }
        m.displayName(Component.text(name, col).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(kItem, PersistentDataType.STRING, type);
        if (getConfig().getBoolean("items.use-custom-model", true)) m.setItemModel(new NamespacedKey("marbles", model));
        it.setItemMeta(m);
        return it;
    }

    String itemType(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(kItem, PersistentDataType.STRING);
    }

    @EventHandler public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        String type = itemType(it);
        if (type == null) return;
        e.setCancelled(true); // never place the head
        Player p = e.getPlayer();
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
                p.sendMessage(msg("Rerolled! You are now " + n.display + " (F = " + n.a1 + ", Shift+F = " + n.a2 + ").", NamedTextColor.LIGHT_PURPLE));
            }
            case "trader" -> {
                if (openTrader(p) && getConfig().getBoolean("trader.consume-on-use", false)) it.setAmount(it.getAmount() - 1);
            }
            default -> { }
        }
    }

    int toInt(Object o, int def) { return o instanceof Number n ? n.intValue() : def; }

    ItemStack parseResult(Map<?, ?> m) {
        String r = String.valueOf(m.get("result")); int n = toInt(m.get("amount"), 1);
        if (r.equalsIgnoreCase("energy") || r.equalsIgnoreCase("reroller") || r.equalsIgnoreCase("trader")) return item(r.toLowerCase(), n);
        Material mat = Material.matchMaterial(r);
        return mat == null ? null : new ItemStack(mat, n);
    }

    boolean openTrader(Player p) {
        List<MerchantRecipe> rs = new ArrayList<>();
        for (Map<?, ?> m : getConfig().getMapList("trader.trades")) {
            ItemStack res = parseResult(m);
            if (res == null) continue;
            MerchantRecipe r = new MerchantRecipe(res, 0, toInt(m.get("max-uses"), 5), false);
            r.setIgnoreDiscounts(true);
            if (m.get("cost") instanceof List<?> cl) for (Object o : cl) {
                if (r.getIngredients().size() >= 2) break;
                if (o instanceof Map<?, ?> cm) {
                    Material mat = Material.matchMaterial(String.valueOf(cm.get("item")));
                    if (mat != null) r.addIngredient(new ItemStack(mat, toInt(cm.get("amount"), 1)));
                }
            }
            if (!r.getIngredients().isEmpty()) rs.add(r);
        }
        if (rs.isEmpty()) { p.sendMessage(msg("The trader has nothing to offer.", NamedTextColor.RED)); return false; }
        Merchant mer = Bukkit.createMerchant(Component.text(getConfig().getString("trader.title", "Marble Trader")));
        mer.setRecipes(rs);
        p.openMerchant(mer, true);
        return true;
    }

    // ---------- commands ----------
    Player who(CommandSender s, String[] a, int idx) {
        if (a.length > idx) return Bukkit.getPlayer(a[idx]);
        return s instanceof Player sp ? sp : null;
    }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        String cmd = c.getName().toLowerCase();
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
            p.sendMessage(msg("Rerolled! You are now " + n.display + " (F = " + n.a1 + ", Shift+F = " + n.a2 + ").", NamedTextColor.LIGHT_PURPLE));
            return true;
        }
        if (a.length < 1) return false;
        switch (a[0].toLowerCase()) {
            case "reload" -> { reloadConfig(); s.sendMessage("Config reloaded."); }
            case "reroll" -> {
                Player t = who(s, a, 1);
                if (t == null) { s.sendMessage("Player not found."); return true; }
                Element n = reroll(t, true);
                s.sendMessage(t.getName() + " is now " + n.display + ".");
                t.sendMessage(msg("Your element was rerolled: " + n.display + ".", NamedTextColor.LIGHT_PURPLE));
            }
            case "element" -> {
                Element el = a.length > 1 ? Element.parse(a[1]) : null;
                Player t = who(s, a, 2);
                if (el == null || t == null) { s.sendMessage("Usage: /marbles element <element> [player]"); return true; }
                t.getPersistentDataContainer().set(kElement, PersistentDataType.STRING, el.name());
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
                if (a.length < 2 || !List.of("energy", "reroller", "trader").contains(a[1].toLowerCase())) { s.sendMessage("Usage: /marbles give <energy|reroller|trader> [amount] [player]"); return true; }
                int n = 1;
                if (a.length > 2) { try { n = Math.max(1, Math.min(64, Integer.parseInt(a[2]))); } catch (NumberFormatException x) { n = 1; } }
                Player t = who(s, a, 3);
                if (t == null) { s.sendMessage("Player not found."); return true; }
                t.getInventory().addItem(item(a[1].toLowerCase(), n)).values().forEach(i -> t.getWorld().dropItemNaturally(t.getLocation(), i));
                s.sendMessage("Gave " + n + " " + a[1] + " to " + t.getName() + ".");
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
        if (!c.getName().equalsIgnoreCase("marbles")) return List.of();
        if (a.length == 1) return List.of("element", "energy", "give", "reroll", "revive", "reload");
        if (a.length == 2 && a[0].equalsIgnoreCase("element")) return Arrays.stream(Element.values()).map(x -> x.name().toLowerCase()).toList();
        if (a.length == 2 && a[0].equalsIgnoreCase("energy")) return List.of("set", "add");
        if (a.length == 2 && a[0].equalsIgnoreCase("give")) return List.of("energy", "reroller", "trader");
        return null;
    }
}
