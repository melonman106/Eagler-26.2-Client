package com.melonman106.vbvclient.mods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;

/**
 * Registers the content of imported mods. It is called from {@code BuiltInRegistries.bootStrap()} after the game's own
 * registries are filled and before they freeze, which is the same moment Fabric lets a mod register content.
 *
 * <p>The page decides which mods load (and publishes that), the integrated-server worker loads exactly the same
 * list, so both register the same items in the same order. The mesh worker has no storage and registers nothing.
 * Nothing here ever throws into the game's own boot, except when the worker cannot reproduce the page's list.</p>
 */
public final class ModBootstrap {
    /** What happened to one mod while registering. */
    public static final class Report {
        public final String id;
        public int registered;
        public final List<String> failures = new ArrayList<>();

        Report(String id) {
            this.id = id;
        }
    }

    private static boolean done;
    private static final List<ModPackage> LOADED = new ArrayList<>();
    private static final Map<String, Report> REPORTS = new LinkedHashMap<>();
    private static final List<String> MESSAGES = new ArrayList<>();
    private static final Set<String> COMMANDS_ALLOWED = new HashSet<>();
    private static boolean guardTripped;
    private static String sessionSafeReason;

    private ModBootstrap() {}

    /** The mods registered in this realm, in registration order. */
    public static synchronized List<ModPackage> loaded() {
        return Collections.unmodifiableList(new ArrayList<>(LOADED));
    }

    /** Width and height of a loaded mod's icon, or null when it has none (or has not been loaded). */
    public static synchronized int[] iconSize(String modId) {
        for (ModPackage pkg : LOADED) {
            if (pkg.manifest.id.equals(modId)) return pkg.icon == null ? null : ModPackage.pngSize(pkg.icon);
        }
        return null;
    }

    public static synchronized Map<String, Report> reports() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(REPORTS));
    }

    /** Notes for the Mods screen (safe mode, items left out, storage trouble). */
    public static synchronized List<String> messages() {
        return Collections.unmodifiableList(new ArrayList<>(MESSAGES));
    }

    /** True when the player had allowed this loaded mod's commands when the game started (the switch is read once, at start-up). */
    public static synchronized boolean commandsAllowed(String modId) {
        return COMMANDS_ALLOWED.contains(modId);
    }

    /** What the game started with for a mod's commands, or null when the mod is not loaded in this session. */
    public static synchronized Boolean commandsApplied(String modId) {
        for (ModPackage pkg : LOADED) {
            if (pkg.manifest.id.equals(modId)) return COMMANDS_ALLOWED.contains(modId);
        }
        return null;
    }

    /** Why this session started without mods because of the page address ({@code ?safe}, {@code ?nomods=1}), or null. */
    public static synchronized String sessionSafeReason() {
        return sessionSafeReason;
    }

    public static synchronized boolean guardTripped() {
        return guardTripped;
    }

    /** Called once per realm from {@code BuiltInRegistries.bootStrap()}. */
    public static synchronized void registerAll() {
        if (done) return;
        done = true;
        ModPlatform.Realm realm = ModPlatform.realm();
        if (realm == ModPlatform.Realm.OTHER) return;
        ModStore store = ModPlatform.store();
        if (store == null) {
            MESSAGES.add("Mod storage is not available, so no mods were loaded.");
            return;
        }

        ModStore.Applied applied;
        if (realm == ModPlatform.Realm.PAGE) {
            try {
                // The way out when a mod keeps the game from starting: one session without mods, nothing stored.
                if (ModPlatform.urlFlag("safe")) {
                    sessionSafeReason = "No mods were loaded this session because the page was opened with ?safe.";
                } else if (ModPlatform.urlFlag("nomods")) {
                    sessionSafeReason = "No mods were loaded this session because the page was opened with ?nomods=1.";
                }
                // A game that never imported a mod has nothing to read, write or register: leave storage untouched.
                if (store.nothingStored()) return;
                if (sessionSafeReason == null) guardTripped = store.beginRegistration();
                ModStore.Plan plan = store.plan(sessionSafeReason);
                applied = store.apply(plan);
                if (plan.safeMode()) MESSAGES.add(plan.safeModeReason());
                for (Map.Entry<String, List<String>> blocked : plan.blocked().entrySet()) {
                    MESSAGES.add(blocked.getKey() + " was not loaded: " + String.join(" ", blocked.getValue()));
                }
                MESSAGES.addAll(applied.problems());
            } catch (Throwable t) {
                System.err.println("[VBV mods] could not prepare the mod list: " + t);
                MESSAGES.add("Mods could not be prepared and were not loaded.");
                return;
            }
        } else {
            applied = store.readApplied();
            if (!applied.problems().isEmpty()) {
                // The worker must register what the page registered. Starting with a different set would make the two
                // sides disagree about item ids, so refuse with a message the player can act on.
                throw new IllegalStateException("The mods this game started with cannot be loaded by the integrated server ("
                    + String.join(" ", applied.problems()) + "). Reload the page.");
            }
        }

        COMMANDS_ALLOWED.addAll(applied.commandsAllowed());
        // Effects, potions, blocks and crops first: an item's potion component and a crop's drops need them.
        ModContentBootstrap.registerAll(applied.mods());
        for (ModPackage pkg : applied.mods()) {
            Report report = new Report(pkg.manifest.id);
            REPORTS.put(pkg.manifest.id, report);
            for (ModItems.ItemDef def : pkg.items) {
                try {
                    registerItem(pkg.manifest.id, def);
                    report.registered++;
                } catch (Throwable t) {
                    report.failures.add(def.id() + ": " + t);
                    System.err.println("[VBV mods] " + pkg.manifest.id + ":" + def.id() + " could not be registered: " + t);
                }
            }
            // Block items and crop seeds are generated from content.json, not items.json.
            ModContentBootstrap.registerBlockItems(pkg, report);
            LOADED.add(pkg);
        }
        if (realm == ModPlatform.Realm.PAGE && sessionSafeReason == null) store.endRegistration();
    }

    private static void registerItem(String modId, ModItems.ItemDef def) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(modId, def.id()));
        Item.Properties properties = new Item.Properties();
        if (def.durability() > 0) properties.durability(def.durability());
        else properties.stacksTo(def.stackSize());
        properties.rarity(Rarity.valueOf(def.rarity().toUpperCase(Locale.ROOT)));
        if (def.fireResistant()) properties.fireResistant();
        if (def.enchantability() > 0) properties.enchantable(def.enchantability());
        if (def.glint()) properties.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        if (def.potion() != null) {
            Holder<Potion> potion = ModContentBootstrap.potion(def.potion());
            if (potion == null) throw new IllegalArgumentException("potion \"" + def.potion() + "\" is not registered by this mod");
            properties.stacksTo(1)
                .component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)
                .usingConvertsTo(Items.GLASS_BOTTLE);
            Item item = new ModPotionItem(properties.setId(key), potion);
            Registry.register(BuiltInRegistries.ITEM, key, item);
            ModItemGroups.add(def.creativeTabs(), item);
            return;
        }
        if (def.food() != null) {
            properties.food(new FoodProperties(def.food().nutrition(), def.food().saturation(), def.food().alwaysEdible()));
        }
        if (def.repairWith() != null) {
            Item repair = BuiltInRegistries.ITEM.getValue(Identifier.parse(def.repairWith()));
            if (repair != null && repair != net.minecraft.world.item.Items.AIR) {
                properties.repairable(repair);
            } else {
                System.err.println("[VBV mods] " + modId + ":" + def.id() + " repair_with " + def.repairWith() + " is not an item (yet); it is ignored");
            }
        }
        if (def.attackDamage() != null) {
            // The player starts with 1 damage and 4 swings per second, and the tooltip adds those to the item's
            // modifiers, so the numbers a mod author writes are the numbers the player sees.
            ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder().add(
                Attributes.ATTACK_DAMAGE,
                new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, def.attackDamage() - 1.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);
            if (def.attackSpeed() != null) {
                attributes.add(
                    Attributes.ATTACK_SPEED,
                    new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, def.attackSpeed() - 4.0, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
            }
            properties.attributes(attributes.build());
            properties.component(DataComponents.WEAPON, new Weapon(1));
        }

        Item item = new Item(properties.setId(key));
        Registry.register(BuiltInRegistries.ITEM, key, item);
        ModItemGroups.add(def.creativeTabs(), item);
    }
}
