package com.wafflegodxd.enchantment;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class EnchantmentPlugin extends JavaPlugin implements Listener, TabExecutor {

    private static final int ANVIL_RESULT_SLOT = 2;

    // The 18 special enchants. They never go above level II.
    private enum Special {
        LIFESTEAL("Lifesteal", "Critical hits give you Regeneration (3s / 5s)", "_SWORD", "_AXE"),
        FROSTBITE("Frostbite", "Hits can chill and slow the target (25% / 40%)", "_SWORD", "_AXE"),
        EXECUTIONER("Executioner", "Bonus damage to targets under 35% health (+1.5 / +3)", "_AXE"),
        BLOODRUSH("Bloodrush", "Kills give you Speed (4s / 6s)", "_SWORD"),
        STATIC_SHOCK("Static Shock", "Attackers may get zapped (15% / 25%)", "_CHESTPLATE"),
        SECOND_WIND("Second Wind", "Low health gives Regeneration II (60s / 40s cooldown)", "_LEGGINGS"),
        NIGHTSIGHT("Nightsight", "Night Vision in dark places", "_HELMET"),
        SPRINGHEEL("Springheel", "Jump Boost I / II", "_BOOTS"),
        PROSPECTOR("Prospector", "Ores can drop extra (12% / 24%)", "_PICKAXE"),
        SNARE("Snare", "Arrows slow the target (Slowness I / II)", "BOW"),
        CLEAVE("Cleave", "Your hits wear down the target's armor faster (2x / 3x durability loss)", "_AXE"),
        ORBITAL("Orbital", "Sneak + right-click: 6 TNT fall from the sky where you look (45s / 30s cooldown)", "FISHING_ROD"),
        POGO("Pogo", "Double jump in mid-air (I / II = higher)", "_BOOTS"),
        ASCENSION("Ascension", "Hitting a player weakens and slows them, and gives you Fire Resistance and Speed (3s / 5s)", "MACE"),
        MUTILATION("Mutilation", "Much faster attacks (2x / 2.5x speed), but the sword wears out 4x faster", "_SWORD"),
        STORMCALLER("Stormcaller", "Trident hits can call lightning (15% / 25%, +3 / +5 damage)", "TRIDENT"),
        GROUNDBREAKER("Groundbreaker", "Haste while holding a shovel (I / II)", "_SHOVEL"),
        OVERDRIVE("Overdrive", "Sneak + right-click: Strength IV for 3s (40s / 25s cooldown)", "_SWORD", "_AXE");

        final String display;
        final String description;
        final String[] suffixes;

        Special(String display, String description, String... suffixes) {
            this.display = display;
            this.description = description;
            this.suffixes = suffixes;
        }

        boolean fits(Material material) {
            String name = material.name();
            for (String suffix : suffixes) {
                if (name.endsWith(suffix)) return true;
            }
            return false;
        }

        String id() {
            return name().toLowerCase();
        }
    }

    private NamespacedKey startedKey;
    private NamespacedKey bottleKey;
    private NamespacedKey potionKey;
    private NamespacedKey recipeKey;
    private final Map<Special, NamespacedKey> specialKeys = new EnumMap<>(Special.class);
    private final Set<UUID> pendingLoss = new HashSet<>();
    private final Map<UUID, Long> secondWindReady = new HashMap<>();
    private final Map<UUID, Long> orbitalReady = new HashMap<>();
    private final Set<UUID> pogoFlyers = new HashSet<>();
    private final Map<UUID, Long> lastDropAt = new HashMap<>();
    private final Map<UUID, Long> lastDrinkAt = new HashMap<>();
    private final Map<UUID, Integer> lastDrinkFrom = new HashMap<>();
    private final Map<UUID, Long> deathAt = new HashMap<>();
    private final Map<UUID, Long> orbitalFired = new HashMap<>();
    private final Map<UUID, Long> groundSeen = new HashMap<>();
    private final Map<UUID, Long> overdriveReady = new HashMap<>();
    private NamespacedKey orbitalKey;
    private NamespacedKey mutilationKey;
    private boolean orbitalBreakBlocks;

    private int startLevel;
    private int minLevel;
    private int maxLevel;
    private boolean killBonus;
    private boolean scaleEnchants;
    private int levelsPerTier;
    private boolean gearGating;
    private boolean blockPickup;
    private boolean blockXpGain;
    private boolean freeEnchanting;
    private boolean potionRecipe;
    private int potionLevels;
    private boolean specialEnchants;
    private double specialChance;
    private int specialTwoMinLevel;
    private boolean anyEnchant;
    private int anyEnchantLevel;
    private double anyEnchantChance;

    // ------------------------------------------------------------------ setup

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        startedKey = new NamespacedKey(this, "started");
        bottleKey = new NamespacedKey(this, "level_bottle");
        potionKey = new NamespacedKey(this, "level_potion");
        recipeKey = new NamespacedKey(this, "potion_of_levels");
        orbitalKey = new NamespacedKey(this, "orbital_tnt");
        mutilationKey = new NamespacedKey(this, "mutilation_speed");
        for (Special special : Special.values()) {
            specialKeys.put(special, new NamespacedKey(this, "se_" + special.id()));
        }
        registerPotionRecipe();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("enchantment").setExecutor(this);

        // Armor that is too powerful gets taken off (checked 5 times a second).
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) checkArmor(player);
        }, 20L, 4L);

        // Pogo (double jump) and Mutilation (attack speed) are checked 10 times a second.
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (!specialEnchants) return;
            for (Player player : getServer().getOnlinePlayers()) {
                updatePogo(player);
                updateMutilation(player);
            }
        }, 40L, 2L);

        // Passive special enchants (Nightsight, Springheel, Groundbreaker) refresh once a second.
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (!specialEnchants) return;
            for (Player player : getServer().getOnlinePlayers()) passiveEffects(player);
        }, 40L, 20L);
    }

    @Override
    public void onDisable() {
        if (recipeKey != null) getServer().removeRecipe(recipeKey);
    }

    // Hidden glitches can each be switched off in config.yml under "glitches:" (default: on).
    private boolean glitch(String name) {
        return getConfig().getBoolean("glitches." + name, true);
    }

    private double chance(String path, double fallback) {
        return Math.max(0.0, Math.min(1.0, getConfig().getDouble(path, fallback)));
    }

    private void loadSettings() {
        reloadConfig();
        startLevel = getConfig().getInt("start-level", 15);
        minLevel = getConfig().getInt("min-level", 0);
        maxLevel = getConfig().getInt("max-level", 30);
        killBonus = getConfig().getBoolean("kill-bonus", false);
        scaleEnchants = getConfig().getBoolean("scale-enchants", true);
        levelsPerTier = Math.max(1, getConfig().getInt("levels-per-tier", 5));
        gearGating = getConfig().getBoolean("gear-gating", true);
        blockPickup = getConfig().getBoolean("block-pickup", false);
        blockXpGain = getConfig().getBoolean("block-xp-gain", true);
        freeEnchanting = getConfig().getBoolean("free-enchanting", true);
        potionRecipe = getConfig().getBoolean("potion-recipe", true);
        potionLevels = Math.max(1, getConfig().getInt("potion-levels", 2));
        specialEnchants = getConfig().getBoolean("special-enchants", true);
        specialChance = chance("special-chance", 0.30);
        specialTwoMinLevel = getConfig().getInt("special-level-2-min-level", 25);
        anyEnchant = getConfig().getBoolean("any-enchant", true);
        anyEnchantLevel = getConfig().getInt("any-enchant-level", 30);
        orbitalBreakBlocks = getConfig().getBoolean("orbital-break-blocks", false);
        anyEnchantChance = chance("any-enchant-chance", 0.50);
    }

    // 2 gold ingots + 4 diamonds + 2 netherite ingots + 1 glass bottle, in any arrangement.
    private void registerPotionRecipe() {
        if (!potionRecipe) return;
        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, levelPotion());
        recipe.addIngredient(2, Material.GOLD_INGOT);
        recipe.addIngredient(4, Material.DIAMOND);
        recipe.addIngredient(2, Material.NETHERITE_INGOT);
        recipe.addIngredient(1, Material.GLASS_BOTTLE);
        getServer().addRecipe(recipe);
    }

    // ---------------------------------------------------------------- helpers

    private void setLevel(Player player, int level) {
        player.setExp(0f);
        player.setLevel(Math.max(minLevel, Math.min(maxLevel, level)));
    }

    private void tell(Player player, String text, NamedTextColor color) {
        player.sendActionBar(Component.text(text, color));
    }

    private Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    // The Bottle of Levels dropped on death: a bottle o' enchanting worth 1 level.
    private ItemStack levelBottle() {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain("Bottle of Levels", NamedTextColor.GREEN));
        meta.lore(List.of(plain("Right-click to gain 1 level", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(bottleKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    // The crafted Potion of Levels.
    private ItemStack levelPotion() {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.displayName(plain("Potion of Levels", NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(plain("Right-click to gain levels", NamedTextColor.GRAY)));
        meta.setColor(Color.fromRGB(0x55FF55));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(potionKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    // How many levels one of our items is worth (0 if it isn't ours).
    private int levelsIn(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        var data = item.getItemMeta().getPersistentDataContainer();
        if (data.has(bottleKey, PersistentDataType.BYTE)) return 1;
        if (data.has(potionKey, PersistentDataType.BYTE)) return potionLevels;
        return 0;
    }

    // ---------------------------------------------------------- special enchants

    private String roman(int level) {
        return level >= 2 ? "II" : "I";
    }

    // 0 if the item doesn't have that special enchant.
    private int specialLevel(ItemStack item, Special special) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return 0;
        Integer level = item.getItemMeta().getPersistentDataContainer()
                .get(specialKeys.get(special), PersistentDataType.INTEGER);
        return level == null ? 0 : level;
    }

    // Put the special enchant on the item (max level II) and show it in the lore.
    // An item only ever has ONE special enchant: adding one replaces any other.
    private void addSpecial(ItemStack item, Special special, int level) {
        ItemMeta meta = item.getItemMeta();
        for (Special other : Special.values()) {
            meta.getPersistentDataContainer().remove(specialKeys.get(other));
        }
        int clamped = Math.max(1, Math.min(2, level));
        meta.getPersistentDataContainer().set(specialKeys.get(special), PersistentDataType.INTEGER, clamped);
        refreshLore(meta);
        item.setItemMeta(meta);
    }

    private boolean hasAnySpecial(ItemStack item) {
        for (Special special : Special.values()) {
            if (specialLevel(item, special) > 0) return true;
        }
        return false;
    }

    // Take every special enchant off the item (used by the grindstone).
    private void stripSpecials(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        for (Special special : Special.values()) {
            meta.getPersistentDataContainer().remove(specialKeys.get(special));
        }
        refreshLore(meta);
        item.setItemMeta(meta);
    }

    // The one real item in the grindstone's two slots, or null if there are zero or two.
    private ItemStack soleItem(ItemStack upper, ItemStack lower) {
        boolean hasUpper = upper != null && !upper.getType().isAir();
        boolean hasLower = lower != null && !lower.getType().isAir();
        if (hasUpper == hasLower) return null;
        return hasUpper ? upper : lower;
    }

    // Special enchant lines go at the top of the lore; other lore is left alone.
    private void refreshLore(ItemMeta meta) {
        var serializer = PlainTextComponentSerializer.plainText();
        List<Component> lore = new ArrayList<>();
        if (meta.lore() != null) lore.addAll(meta.lore());
        lore.removeIf(line -> {
            String text = serializer.serialize(line);
            for (Special special : Special.values()) {
                if (text.equals(special.display + " I") || text.equals(special.display + " II")) return true;
            }
            return false;
        });
        List<Component> mine = new ArrayList<>();
        for (Special special : Special.values()) {
            int have = meta.getPersistentDataContainer()
                    .getOrDefault(specialKeys.get(special), PersistentDataType.INTEGER, 0);
            if (have > 0) mine.add(plain(special.display + " " + roman(have), NamedTextColor.LIGHT_PURPLE));
        }
        lore.addAll(0, mine);
        meta.lore(lore);
    }

    private Special randomSpecialFor(Material material) {
        List<Special> options = new ArrayList<>();
        for (Special special : Special.values()) {
            if (special.fits(material)) options.add(special);
        }
        if (options.isEmpty()) return null;
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }

    private Special findSpecial(String name) {
        for (Special special : Special.values()) {
            if (special.id().equalsIgnoreCase(name) || special.display.equalsIgnoreCase(name)) return special;
        }
        return null;
    }

    // Crit = hitting while falling (the vanilla rule, roughly).
    private boolean isCrit(Player player) {
        return player.getFallDistance() > 0.0F && !player.isOnGround() && !player.isInWater()
                && !player.isSprinting() && !player.isInsideVehicle();
    }

    // Lifesteal, Frostbite, Executioner, Static Shock and Snare.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpecialHit(EntityDamageByEntityEvent event) {
        if (!specialEnchants) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Snare: arrows that were shot from a Snare bow.
        if (event.getDamager() instanceof Projectile projectile) {
            Integer snare = projectile.getPersistentDataContainer()
                    .get(specialKeys.get(Special.SNARE), PersistentDataType.INTEGER);
            if (snare != null) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, snare - 1));
            }
            return;
        }

        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

        // Static Shock: whoever hits the wearer may get zapped.
        if (target instanceof Player wearer && event.getDamager() instanceof LivingEntity attacker) {
            int shock = specialLevel(wearer.getInventory().getChestplate(), Special.STATIC_SHOCK);
            if (shock > 0 && rng.nextDouble() < (shock == 1 ? 0.15 : 0.25)) {
                attacker.damage(shock == 1 ? 2.0 : 3.0);
                attacker.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                        attacker.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.02);
            }
        }

        // Weapon enchants.
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack weapon = player.getInventory().getItemInMainHand();

        int lifesteal = specialLevel(weapon, Special.LIFESTEAL);
        if (lifesteal > 0 && isCrit(player)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, lifesteal == 1 ? 60 : 100, 0));
        }

        int frostbite = specialLevel(weapon, Special.FROSTBITE);
        if (frostbite > 0 && rng.nextDouble() < (frostbite == 1 ? 0.25 : 0.40)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0));
            target.setFreezeTicks(Math.max(target.getFreezeTicks(), 100));
        }

        int cleave = specialLevel(weapon, Special.CLEAVE);
        if (cleave > 0) shredArmor(target, cleave, event.getFinalDamage());

        int ascension = specialLevel(weapon, Special.ASCENSION);
        if (ascension > 0 && target instanceof Player) {
            int ticks = ascension == 1 ? 60 : 100;
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, ticks, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 0));
        }

        int storm = specialLevel(weapon, Special.STORMCALLER);
        if (storm > 0 && rng.nextDouble() < (storm == 1 ? 0.15 : 0.25)) {
            target.getWorld().strikeLightningEffect(target.getLocation());
            event.setDamage(event.getDamage() + (storm == 1 ? 3.0 : 5.0));
        }

        int executioner = specialLevel(weapon, Special.EXECUTIONER);
        if (executioner > 0 && target.getHealth() <= target.getMaxHealth() * 0.35) {
            event.setDamage(event.getDamage() + (executioner == 1 ? 1.5 : 3.0));
        }
    }

    // Bloodrush: kills give Speed.
    @EventHandler
    public void onKill(EntityDeathEvent event) {
        if (!specialEnchants) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.equals(event.getEntity())) return;
        int bloodrush = specialLevel(killer.getInventory().getItemInMainHand(), Special.BLOODRUSH);
        if (bloodrush > 0) {
            killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, bloodrush == 1 ? 80 : 120, 0));
        }
    }

    // Second Wind: dropping to 3 hearts or less gives Regeneration II, with a cooldown.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!specialEnchants || !(event.getEntity() instanceof Player player)) return;
        int wind = specialLevel(player.getInventory().getLeggings(), Special.SECOND_WIND);
        if (wind == 0) return;
        double left = player.getHealth() - event.getFinalDamage();
        if (left <= 0.0 || left > 6.0) return;
        long now = System.currentTimeMillis();
        if (secondWindReady.getOrDefault(player.getUniqueId(), 0L) > now) return;
        secondWindReady.put(player.getUniqueId(), now + (wind == 1 ? 60_000L : 40_000L));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, wind == 1 ? 60 : 100, 1));
        tell(player, "Second Wind!", NamedTextColor.LIGHT_PURPLE);
    }

    // Prospector: ores can drop extra.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProspect(BlockBreakEvent event) {
        if (!specialEnchants) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        ItemStack tool = player.getInventory().getItemInMainHand();
        int prospector = specialLevel(tool, Special.PROSPECTOR);
        Block block = event.getBlock();
        if (prospector == 0 || !block.getType().name().endsWith("_ORE")) return;
        if (ThreadLocalRandom.current().nextDouble() >= (prospector == 1 ? 0.12 : 0.24)) return;
        for (ItemStack drop : block.getDrops(tool)) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
        }
    }

    // Snare: remember which arrows came from a Snare bow.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSnareShoot(EntityShootBowEvent event) {
        if (!specialEnchants) return;
        int snare = specialLevel(event.getBow(), Special.SNARE);
        if (snare == 0) return;
        event.getProjectile().getPersistentDataContainer()
                .set(specialKeys.get(Special.SNARE), PersistentDataType.INTEGER, snare);
    }

    // Nightsight and Springheel are always on while worn.
    private void passiveEffects(Player player) {
        PlayerInventory inv = player.getInventory();

        int sight = specialLevel(inv.getHelmet(), Special.NIGHTSIGHT);
        if (sight > 0 && player.getLocation().getBlock().getLightLevel() < (sight == 1 ? 5 : 8)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0, false, false, true));
        }

        int dig = specialLevel(inv.getItemInMainHand(), Special.GROUNDBREAKER);
        if (dig > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, dig - 1, false, false, true));
        }

        int spring = specialLevel(inv.getBoots(), Special.SPRINGHEEL);
        if (spring > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, spring - 1, false, false, true));
        }
    }

    // Cleave: extra durability loss on the target's armor (vanilla loss x2 at I, x3 at II).
    private void shredArmor(LivingEntity target, int level, double damage) {
        EntityEquipment equipment = target.getEquipment();
        if (equipment == null) return;
        int extra = Math.max(1, (int) (damage / 4.0)) * level;
        ItemStack[] armor = equipment.getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (piece == null || piece.getType().isAir()) continue;
            ItemMeta meta = piece.getItemMeta();
            if (!(meta instanceof Damageable damageable) || meta.isUnbreakable()) continue;
            int worn = damageable.getDamage() + extra;
            if (worn >= piece.getType().getMaxDurability()) {
                armor[i] = null;
            } else {
                damageable.setDamage(worn);
                piece.setItemMeta(meta);
            }
            changed = true;
        }
        if (changed) equipment.setArmorContents(armor);
    }

    // Mutilation: swords wear out 4x faster.
    @EventHandler(ignoreCancelled = true)
    public void onMutilationWear(PlayerItemDamageEvent event) {
        if (!specialEnchants) return;
        if (specialLevel(event.getItem(), Special.MUTILATION) == 0) return;
        event.setDamage(event.getDamage() * 4);
    }

    // Mutilation: faster attacks while the sword is in your main hand (+100% / +150% attack speed).
    private void updateMutilation(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (speed == null) return;
        AttributeModifier current = null;
        for (AttributeModifier modifier : speed.getModifiers()) {
            if (mutilationKey.equals(modifier.getKey())) {
                current = modifier;
                break;
            }
        }
        int level = specialLevel(player.getInventory().getItemInMainHand(), Special.MUTILATION);
        double wanted = level == 0 ? 0.0 : (level == 1 ? 1.0 : 1.5);
        if (level != 0 && current != null && current.getAmount() == wanted) return;
        if (current != null) speed.removeModifier(current);
        if (level != 0) {
            speed.addTransientModifier(new AttributeModifier(mutilationKey, wanted, AttributeModifier.Operation.ADD_SCALAR));
        }
    }

    // Pogo: flight is only switched on while standing on the ground, so the "flight toggle"
    // (double-tap jump) in mid-air becomes a double jump.
    private void updatePogo(Player player) {
        GameMode mode = player.getGameMode();
        boolean normal = mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
        UUID id = player.getUniqueId();
        int pogo = specialLevel(player.getInventory().getBoots(), Special.POGO);
        if (pogo > 0 && normal) {
            pogoFlyers.add(id);
            if (player.isOnGround()) groundSeen.put(id, System.currentTimeMillis());
            if (player.isOnGround() && !player.getAllowFlight()) player.setAllowFlight(true);
        } else if (pogoFlyers.remove(id) && normal) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPogoJump(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!specialEnchants || !event.isFlying() || !pogoFlyers.contains(player.getUniqueId())) return;
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) return;
        int pogo = specialLevel(player.getInventory().getBoots(), Special.POGO);
        if (pogo == 0) return;
        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        double power = pogo == 1 ? 0.75 : 0.95;
        // Glitch: a very fast double tap while sprinting lands before the ground check catches up,
        // so the jump boost stacks.
        if (glitch("stacked-jump") && player.isSprinting()
                && System.currentTimeMillis() - groundSeen.getOrDefault(player.getUniqueId(), 0L) < 170L) {
            power *= 1.6;
        }
        player.setVelocity(player.getVelocity().setY(power));
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 8, 0.3, 0.05, 0.3, 0.02);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (pogoFlyers.remove(player.getUniqueId())) {
            player.setAllowFlight(false);
        }
        // Quitting while dead still costs the level. Glitch: quit within a second of dying
        // and the loss never gets applied (the bottle still dropped).
        if (player.isDead() && pendingLoss.remove(player.getUniqueId())) {
            long diedAgo = System.currentTimeMillis() - deathAt.getOrDefault(player.getUniqueId(), 0L);
            if (!(glitch("early-quit") && diedAgo < 1000L)) {
                setLevel(player, player.getLevel() - 1);
            }
        }
    }

    // Remember when a bottle was dropped (used by the drink timing in onUseBottle).
    @EventHandler(ignoreCancelled = true)
    public void onDropTrack(PlayerDropItemEvent event) {
        if (levelsIn(event.getItemDrop().getItemStack()) == 0) return;
        lastDropAt.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    // Glitch: change world within 3 seconds of firing Orbital and the cooldown gets lost.
    @EventHandler
    public void onWorldHop(PlayerChangedWorldEvent event) {
        if (!glitch("portal-cooldown")) return;
        UUID id = event.getPlayer().getUniqueId();
        if (System.currentTimeMillis() - orbitalFired.getOrDefault(id, 0L) < 3000L) {
            orbitalReady.remove(id);
        }
    }

    // Overdrive: sneak + right-click with an Overdrive weapon for Strength IV for 3 seconds.
    @EventHandler(priority = EventPriority.HIGH)
    public void onOverdrive(PlayerInteractEvent event) {
        if (!specialEnchants) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        ItemStack weapon = event.getItem();
        int overdrive = specialLevel(weapon, Special.OVERDRIVE);
        if (overdrive == 0 || !canUse(player, weapon)) return;

        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        long ready = overdriveReady.getOrDefault(id, 0L);
        if (ready > now) {
            tell(player, "Overdrive recharging: " + ((ready - now + 999) / 1000) + "s", NamedTextColor.RED);
            return;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 3));
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.1);
        tell(player, "Overdrive!", NamedTextColor.LIGHT_PURPLE);

        // The cooldown is saved 2 ticks later, and only if the weapon is still in your hand.
        // Glitch: switch hotbar slots in that tiny gap and the cooldown never starts.
        long cooldown = overdrive == 1 ? 40_000L : 25_000L;
        getServer().getScheduler().runTaskLater(this, () -> {
            boolean stillHolding = specialLevel(player.getInventory().getItemInMainHand(), Special.OVERDRIVE) > 0;
            if (stillHolding || !glitch("swap-cooldown")) {
                overdriveReady.put(id, System.currentTimeMillis() + cooldown);
            }
        }, 2L);
    }

    // Orbital: sneak + right-click with an Orbital fishing rod to call 6 TNT down from the sky.
    @EventHandler(priority = EventPriority.HIGH)
    public void onOrbital(PlayerInteractEvent event) {
        if (!specialEnchants) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        ItemStack rod = event.getItem();
        int orbital = specialLevel(rod, Special.ORBITAL);
        if (orbital == 0 || !canUse(player, rod)) return;
        event.setUseItemInHand(Event.Result.DENY);

        long now = System.currentTimeMillis();
        long ready = orbitalReady.getOrDefault(player.getUniqueId(), 0L);
        if (ready > now) {
            tell(player, "Orbital recharging: " + ((ready - now + 999) / 1000) + "s", NamedTextColor.RED);
            return;
        }
        orbitalReady.put(player.getUniqueId(), now + (orbital == 1 ? 45_000L : 30_000L));
        orbitalFired.put(player.getUniqueId(), now);

        RayTraceResult hit = player.rayTraceBlocks(40.0);
        Location aim = hit != null
                ? hit.getHitPosition().toLocation(player.getWorld())
                : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(40));
        int height = 0;
        while (height < 14 && aim.clone().add(0, height + 1, 0).getBlock().isPassable()) height++;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            Location spot = aim.clone().add(rng.nextDouble(-2.5, 2.5), height, rng.nextDouble(-2.5, 2.5));
            player.getWorld().spawn(spot, TNTPrimed.class, tnt -> {
                tnt.setFuseTicks(70);
                tnt.setSource(player);
                tnt.getPersistentDataContainer().set(orbitalKey, PersistentDataType.BYTE, (byte) 1);
            });
        }
        tell(player, "Orbital strike incoming!", NamedTextColor.LIGHT_PURPLE);
    }

    // Orbital TNT doesn't break blocks unless orbital-break-blocks is on (it still hurts).
    @EventHandler(ignoreCancelled = true)
    public void onOrbitalBlast(EntityExplodeEvent event) {
        if (orbitalBreakBlocks) return;
        if (event.getEntity().getPersistentDataContainer().has(orbitalKey, PersistentDataType.BYTE)) {
            event.blockList().clear();
        }
    }

    // ------------------------------------------------------------ gear gating

    // An item's tier is its highest enchantment level (Sharpness VI = tier 6).
    private int itemTier(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;
        int best = 0;
        for (int enchantLevel : item.getEnchantments().values()) best = Math.max(best, enchantLevel);
        return best;
    }

    // Every levels-per-tier levels is one tier (level 15 = tier 3, level 30 = tier 6).
    private int playerTier(Player player) {
        return Math.max(1, player.getLevel() / levelsPerTier);
    }

    private boolean canUse(Player player, ItemStack item) {
        if (!gearGating) return true;
        if (item == null || item.getType().isAir()) return true;
        if (player.hasPermission("enchantment.bypass")) return true;
        return itemTier(item) <= playerTier(player);
    }

    private void warn(Player player, ItemStack item) {
        int need = itemTier(item) * levelsPerTier;
        tell(player, "Too powerful! Needs level " + need + " (you're level " + player.getLevel() + ")", NamedTextColor.RED);
    }

    // Take off any armor that is too powerful and put it in the inventory.
    private void checkArmor(Player player) {
        if (!gearGating) return;
        PlayerInventory inv = player.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (piece == null || canUse(player, piece)) continue;
            armor[i] = null;
            changed = true;
            warn(player, piece);
            inv.addItem(piece).values().forEach(left ->
                    player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        if (changed) inv.setArmorContents(armor);
    }

    // Too-powerful weapons do no damage.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (canUse(player, weapon)) return;
        event.setCancelled(true);
        warn(player, weapon);
    }

    // Too-powerful tools can't break blocks.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (canUse(player, tool)) return;
        event.setCancelled(true);
        warn(player, tool);
    }

    // Too-powerful bows and crossbows can't shoot.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();
        if (canUse(player, bow)) return;
        event.setCancelled(true);
        warn(player, bow);
    }

    // Too-powerful items can't be used with right-click (tridents, shields, tools on blocks).
    @EventHandler(priority = EventPriority.HIGH)
    public void onGearUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        if (canUse(player, item)) return;
        event.setUseItemInHand(Event.Result.DENY);
        warn(player, item);
    }

    // Optional: too-powerful items can't even be picked up.
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!blockPickup || !(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        if (canUse(player, item)) return;
        event.setCancelled(true);
        warn(player, item);
    }

    // ------------------------------------------------------------ level rules

    // Everyone starts at the start level, once.
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (potionRecipe) player.discoverRecipe(recipeKey);
        var data = player.getPersistentDataContainer();
        if (data.has(startedKey, PersistentDataType.BYTE)) return;
        data.set(startedKey, PersistentDataType.BYTE, (byte) 1);
        setLevel(player, startLevel);
    }

    // Levels only come from bottles and potions (and the optional kill bonus).
    @EventHandler
    public void onXp(PlayerExpChangeEvent event) {
        if (blockXpGain) event.setAmount(0);
    }

    // Any death costs one level and drops a bottle worth one level.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        event.setKeepLevel(true);
        event.setDroppedExp(0);

        if (victim.getLevel() > minLevel) {
            pendingLoss.add(victim.getUniqueId());
            deathAt.put(victim.getUniqueId(), System.currentTimeMillis());
            Item bottle = victim.getWorld().dropItemNaturally(victim.getLocation(), levelBottle());
            bottle.setInvulnerable(true);
        }

        Player killer = victim.getKiller();
        if (killBonus && killer != null && !killer.equals(victim) && killer.getLevel() < maxLevel) {
            setLevel(killer, killer.getLevel() + 1);
            tell(killer, "+1 level (" + killer.getLevel() + ")", NamedTextColor.GREEN);
        }
    }

    // The level is taken once the player respawns.
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!pendingLoss.remove(player.getUniqueId())) return;
        getServer().getScheduler().runTask(this, () -> {
            setLevel(player, player.getLevel() - 1);
            tell(player, "-1 level (" + player.getLevel() + ")", NamedTextColor.RED);
        });
    }

    // Right-click a bottle or potion of levels to drink it.
    @EventHandler(priority = EventPriority.HIGH)
    public void onUseBottle(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        EquipmentSlot hand = event.getHand();
        if (hand == null) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(hand);
        int gained = levelsIn(held);
        if (gained == 0) return;

        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);

        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();

        // Glitch: drink twice within ~90ms and the max-level check reads the old level,
        // so you can end up one level over the cap.
        boolean stale = glitch("stale-cap") && now - lastDrinkAt.getOrDefault(id, 0L) < 90L;
        int checkLevel = stale ? lastDrinkFrom.getOrDefault(id, player.getLevel()) : player.getLevel();
        if (checkLevel >= maxLevel) {
            tell(player, "You're already at the max level (" + maxLevel + ")", NamedTextColor.YELLOW);
            return;
        }

        // Glitch: drink right after dropping one from the same stack and (sometimes) the
        // bottle isn't used up.
        boolean raced = glitch("drop-race") && now - lastDropAt.getOrDefault(id, 0L) < 120L
                && ThreadLocalRandom.current().nextDouble() < 0.4;
        if (!raced) {
            ItemStack rest = held.clone();
            rest.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(hand, rest.getAmount() > 0 ? rest : null);
        }

        int before = player.getLevel();
        lastDrinkAt.put(id, now);
        lastDrinkFrom.put(id, before);
        if (stale) {
            player.setExp(0f);
            player.setLevel(Math.min(maxLevel + 1, before + gained));
        } else {
            setLevel(player, before + gained);
        }
        tell(player, "+" + gained + (gained == 1 ? " level (" : " levels (") + player.getLevel() + ")", NamedTextColor.GREEN);
    }

    // Bottles and potions of levels don't burn in lava or fire, and can't be blown up.
    @EventHandler
    public void onLevelItemDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item dropped)) return;
        if (levelsIn(dropped.getItemStack()) == 0) return;
        switch (event.getCause()) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CONTACT, BLOCK_EXPLOSION, ENTITY_EXPLOSION -> event.setCancelled(true);
            default -> { }
        }
    }

    // Grindstone: special enchants come off like normal ones.
    @EventHandler(priority = EventPriority.HIGH)
    public void onGrindstone(PrepareGrindstoneEvent event) {
        ItemStack source = event.getResult();
        if (source == null || source.getType().isAir()) {
            source = soleItem(event.getInventory().getUpperItem(), event.getInventory().getLowerItem());
        }
        if (source == null || !hasAnySpecial(source)) return;
        ItemStack cleaned = source.clone();
        stripSpecials(cleaned);
        event.setResult(cleaned);
    }

    // Dispensers can't throw or use these items.
    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (levelsIn(event.getItem()) > 0) event.setCancelled(true);
    }

    // ------------------------------------------------------------- enchanting

    // Level 30 only: the table can also roll an enchant that normally doesn't fit the item
    // (Wind Burst on a sword). It is capped at that enchant's normal max level.
    private void addOffTypeEnchant(ItemStack item, Map<Enchantment, Integer> chosen, int tier) {
        List<Enchantment> options = new ArrayList<>();
        for (Enchantment candidate : Registry.ENCHANTMENT) {
            if (candidate.canEnchantItem(item)) continue;
            if (candidate.getKey().getKey().contains("curse")) continue;
            if (chosen.containsKey(candidate)) continue;
            boolean clash = false;
            for (Enchantment existing : chosen.keySet()) {
                if (existing.conflictsWith(candidate)) clash = true;
            }
            if (!clash) options.add(candidate);
        }
        if (options.isEmpty()) return;
        Enchantment pick = options.get(ThreadLocalRandom.current().nextInt(options.size()));
        chosen.put(pick, Math.max(1, Math.min(pick.getMaxLevel(), tier)));
    }

    // Runs one tick after the table finishes, so the item already has its normal enchants.
    private void applySpecialAtTable(Player player, Special special, int specialLevel) {
        Inventory top = player.getOpenInventory().getTopInventory();
        ItemStack enchanted = top.getItem(0);
        if (enchanted == null || !special.fits(enchanted.getType())) return;
        if (hasAnySpecial(enchanted)) return;
        addSpecial(enchanted, special, specialLevel);
        top.setItem(0, enchanted);
        tell(player, "Special enchant: " + special.display + " " + roman(specialLevel), NamedTextColor.LIGHT_PURPLE);
    }

    // Enchanting table: enchants get stronger as your level goes up (level 30 = tier 6, Sharpness VI).
    // Enchants that only have one level (like Mending) stay at 1.
    // At level 30 it can also roll an off-type enchant, and sometimes a special enchant.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchantScale(EnchantItemEvent event) {
        Player enchanter = event.getEnchanter();
        ItemStack target = event.getItem();
        int tier = playerTier(enchanter);
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        if (scaleEnchants) {
            event.getEnchantsToAdd().replaceAll((enchant, current) ->
                    enchant.getMaxLevel() == 1 ? 1 : Math.max(current, tier));
        }

        if (anyEnchant && enchanter.getLevel() >= anyEnchantLevel && rng.nextDouble() < anyEnchantChance) {
            addOffTypeEnchant(target, event.getEnchantsToAdd(), tier);
        }

        if (specialEnchants && rng.nextDouble() < specialChance) {
            Special pick = randomSpecialFor(target.getType());
            if (pick != null) {
                int rolled = (enchanter.getLevel() >= specialTwoMinLevel && rng.nextDouble() < 0.40) ? 2 : 1;
                getServer().getScheduler().runTask(this, () -> applySpecialAtTable(enchanter, pick, rolled));
            }
        }
    }

    // Enchanting table: give the cost back.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (!freeEnchanting) return;
        Player player = event.getEnchanter();
        int before = player.getLevel();
        getServer().getScheduler().runTask(this, () -> player.setLevel(before));
    }

    // Anvil: give the cost back when the result is taken.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvil(InventoryClickEvent event) {
        if (!freeEnchanting) return;
        if (!(event.getInventory() instanceof AnvilInventory)) return;
        if (event.getRawSlot() != ANVIL_RESULT_SLOT) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int before = player.getLevel();
        getServer().getScheduler().runTask(this, () -> {
            if (player.getLevel() < before) player.setLevel(before);
        });
    }

    // ---------------------------------------------------------------- command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /" + label + " <check [player]|list|set <player> <level>|enchant <name> [1-2]|reload>", NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "check" -> {
                Player target = args.length > 1 ? getServer().getPlayerExact(args[1])
                        : (sender instanceof Player self ? self : null);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text(target.getName() + " is level " + target.getLevel()
                        + " (gear tier " + playerTier(target) + ").", NamedTextColor.AQUA));
            }
            case "list" -> {
                sender.sendMessage(Component.text("Special enchants (max level II):", NamedTextColor.LIGHT_PURPLE));
                for (Special special : Special.values()) {
                    sender.sendMessage(Component.text(special.display + " - " + special.description, NamedTextColor.GRAY));
                }
            }
            case "enchant" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this.", NamedTextColor.RED));
                    return true;
                }
                if (!sender.hasPermission("enchantment.admin")) {
                    sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /" + label + " enchant <name> [1-2]. See /" + label + " list", NamedTextColor.YELLOW));
                    return true;
                }
                Special special = findSpecial(args[1]);
                if (special == null) {
                    sender.sendMessage(Component.text("Unknown special enchant. See /" + label + " list", NamedTextColor.RED));
                    return true;
                }
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType().isAir() || !special.fits(held.getType())) {
                    sender.sendMessage(Component.text("Hold an item that fits " + special.display + ".", NamedTextColor.RED));
                    return true;
                }
                int wanted = 1;
                if (args.length > 2) {
                    try {
                        wanted = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Component.text("Level must be 1 or 2.", NamedTextColor.RED));
                        return true;
                    }
                }
                addSpecial(held, special, wanted);
                player.getInventory().setItemInMainHand(held);
                sender.sendMessage(Component.text("Added " + special.display + " " + roman(wanted) + ".", NamedTextColor.GREEN));
            }
            case "set" -> {
                if (!sender.hasPermission("enchantment.admin")) {
                    sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /" + label + " set <player> <level>", NamedTextColor.YELLOW));
                    return true;
                }
                Player target = getServer().getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                try {
                    setLevel(target, Integer.parseInt(args[2]));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Level must be a number.", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text(target.getName() + " is now level " + target.getLevel() + ".", NamedTextColor.GREEN));
            }
            case "glitches" -> {
                if (!sender.hasPermission("enchantment.admin")) {
                    sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text("Hidden glitches (turn off in config.yml under glitches:)", NamedTextColor.LIGHT_PURPLE));
                sender.sendMessage(Component.text("drop-race: drop one bottle (Q) from a stack and right-click within ~0.1s: 40% chance the bottle isn't used up", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("stale-cap: right-click a stack of bottles at " + (maxLevel - 1) + " faster than ~11 clicks/sec to reach " + (maxLevel + 1), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("stacked-jump: sprint + extremely fast double tap jump with Pogo boots = a much higher jump", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("portal-cooldown: change world within 3s of firing Orbital and its cooldown is lost", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("early-quit: quit within 1s of dying and you keep the level (bottle still drops)", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("swap-cooldown: right-click with an Overdrive weapon, then switch hotbar slots within ~0.1s: the cooldown never starts (repeat = infinite Strength IV)", NamedTextColor.GRAY));
            }
            case "reload" -> {
                if (!sender.hasPermission("enchantment.admin")) {
                    sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                    return true;
                }
                loadSettings();
                sender.sendMessage(Component.text("Config reloaded. (The recipe only changes after a full restart.)", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("check", "list", "set", "enchant", "reload");
        if (args.length == 2 && (args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("set"))) {
            return getServer().getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("enchant")) {
            List<String> ids = new ArrayList<>();
            for (Special special : Special.values()) ids.add(special.id());
            return ids;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("enchant")) return List.of("1", "2");
        return List.of();
    }
}
