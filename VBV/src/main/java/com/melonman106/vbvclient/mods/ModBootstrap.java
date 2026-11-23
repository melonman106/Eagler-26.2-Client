package com.melonman106.vbvclient.mods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;

/**
 * Registers the items of imported mods. It is called from {@code BuiltInRegistries.bootStrap()} after the game's own
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
    private static boolean guardTripped;

    private ModBootstrap() {}

    /** The mods registered in this realm, in registration order. */
    public static synchronized List<ModPackage> loaded() {
        return Collections.unmodifiableList(new ArrayList<>(LOADED));
    }

    public static synchronized Map<String, Report> reports() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(REPORTS));
    }

    /** Notes for the Mods screen (safe mode, items left out, storage trouble). */
    public static synchronized List<String> messages() {
        return Collections.unmodifiableList(new ArrayList<>(MESSAGES));
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
                guardTripped = store.beginRegistration();
                ModStore.Plan plan = store.plan();
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
            LOADED.add(pkg);
        }
        if (realm == ModPlatform.Realm.PAGE) store.endRegistration();
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
