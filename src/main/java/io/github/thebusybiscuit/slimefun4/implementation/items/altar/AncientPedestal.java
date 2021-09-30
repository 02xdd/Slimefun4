package io.github.thebusybiscuit.slimefun4.implementation.items.altar;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.bakedlibs.dough.collections.OptionalPair;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.bakedlibs.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSpawnReason;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockDispenseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.AncientAltarListener;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.AncientAltarTask;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;

/**
 * The {@link AncientPedestal} is a part of the {@link AncientAltar}.
 * You can place any {@link ItemStack} onto the {@link AncientPedestal} to provide it to
 * the altar as a crafting ingredient.
 * 
 * @author Redemption198
 * @author TheBusyBiscuit
 * 
 * @see AncientAltar
 * @see AncientAltarListener
 * @see AncientAltarTask
 *
 */
public class AncientPedestal extends SimpleSlimefunItem<BlockDispenseHandler> {

    public static final String ITEM_PREFIX = ChatColors.color("&dALTAR &3Probe - &e");

    private static final Map<Location, OptionalPair<Item, Integer>> pedestalItemCache = new ConcurrentHashMap<>();

    @ParametersAreNonnullByDefault
    public AncientPedestal(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe, ItemStack recipeOutput) {
        super(itemGroup, item, recipeType, recipe, recipeOutput);

        addItemHandler(onBreak());
    }

    private @Nonnull BlockBreakHandler onBreak() {
        return new SimpleBlockBreakHandler() {

            @Override
            public void onBlockBreak(@Nonnull Block b) {
                Optional<Item> entity = getPlacedItem(b);

                if (entity.isPresent()) {
                    Item stack = entity.get();

                    if (stack.isValid()) {
                        stack.removeMetadata("no_pickup", Slimefun.instance());
                        b.getWorld().dropItem(b.getLocation(), getOriginalItemStack(stack));
                        stopDisplayItem(b, stack);
                    }
                }
            }
        };
    }

    @Override
    public @Nonnull BlockDispenseHandler getItemHandler() {
        return (e, d, block, machine) -> e.setCancelled(true);
    }

    public @Nonnull Optional<Item> getPlacedItem(@Nonnull Block pedestal) {
        OptionalPair<Item, Integer> cache = pedestalItemCache.get(pedestal.getLocation());

        if (cache != null && cache.getFirstValue().isPresent()) {
            return cache.getFirstValue();
        }

        // If cache was deleted, use old method to find nearby possible display item entity.
        Location l = pedestal.getLocation().add(0.5, 1.2, 0.5);

        for (Entity n : l.getWorld().getNearbyEntities(l, 0.5, 0.5, 0.5, this::testItem)) {
            if (n instanceof Item) {
                Optional<Item> item = Optional.of((Item) n);

                Location pedestalLocation = pedestal.getLocation();

                int watcherTaskID = startWatcher(pedestalLocation, pedestalLocation.add(0.5, 1.2, 0.5));
                pedestalItemCache.put(pedestalLocation, new OptionalPair<>(item.get(), watcherTaskID));

                return item;
            }
        }

        return Optional.empty();
    }

    private boolean testItem(@Nullable Entity n) {
        if (n instanceof Item && n.isValid()) {
            Item item = (Item) n;
            ItemMeta meta = item.getItemStack().getItemMeta();

            return meta.hasDisplayName() && meta.getDisplayName().startsWith(ITEM_PREFIX);
        } else {
            return false;
        }
    }

    public @Nonnull ItemStack getOriginalItemStack(@Nonnull Item item) {
        ItemStack stack = item.getItemStack().clone();
        String customName = item.getCustomName();

        if (customName.equals(ItemUtils.getItemName(new ItemStack(stack.getType())))) {
            ItemMeta im = stack.getItemMeta();
            im.setDisplayName(null);
            stack.setItemMeta(im);
        } else {
            ItemMeta im = stack.getItemMeta();
            im.setDisplayName(customName);
            stack.setItemMeta(im);
        }

        return stack;
    }

    public void placeItem(@Nonnull Player p, @Nonnull Block b) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemStack displayItem = new CustomItemStack(hand, ITEM_PREFIX + System.nanoTime());
        displayItem.setAmount(1);

        // Get the display name of the original Item in the Player's hand
        String nametag = ItemUtils.getItemName(hand);

        if (p.getGameMode() != GameMode.CREATIVE) {
            ItemUtils.consumeItem(hand, false);
        }

        Location pedestalLocation = b.getLocation();
        Location spawnLocation = pedestalLocation.add(0.5, 1.2, 0.5);
        Item entity = SlimefunUtils.spawnItem(spawnLocation, displayItem, ItemSpawnReason.ANCIENT_PEDESTAL_PLACE_ITEM);

        if (entity != null) {
            entity.setVelocity(new Vector(0, 0.1, 0));
            entity.setCustomNameVisible(true);
            entity.setCustomName(nametag);
            SlimefunUtils.markAsNoPickup(entity, "altar_item");
            p.playSound(pedestalLocation, Sound.ENTITY_ITEM_PICKUP, 0.3F, 0.3F);

            int watcherTaskID = startWatcher(pedestalLocation, spawnLocation);

            pedestalItemCache.put(pedestalLocation, new OptionalPair<>(entity, watcherTaskID));
        }
    }

    /**
     * Stop display item upon pedestal
     *
     * @param pedestal ancient pedestal location
     * @param item display item
     */
    public void stopDisplayItem(@Nonnull Block pedestal, @Nonnull Entity item) {
        item.remove();

        OptionalPair<Item, Integer> result = pedestalItemCache.get(pedestal.getLocation());

        if (result == null || result.getSecondValue().isPresent()) {
            return;
        }

        Bukkit.getScheduler().cancelTask(result.getSecondValue().orElse(-1));
        pedestalItemCache.remove(pedestal.getLocation());
    }

    /**
     * Start a watcher monitor the location of displayed item
     *
     * @param pedestalLocation the location of pedestal
     * @param spawnLocation the location displayed item entity first spawn
     *
     * @return Bukkit task id
     */
    private int startWatcher(@Nonnull Location pedestalLocation, @Nonnull Location spawnLocation) {
        return Bukkit.getScheduler().scheduleSyncRepeatingTask(Slimefun.instance(), () -> {
            Optional<Item> display = pedestalItemCache.get(pedestalLocation).getFirstValue();

            if (display.isPresent() && display.get().getLocation().distance(pedestalLocation) > 2) {
                display.get().teleport(spawnLocation);
            }

        },  5 * 20L, 5 * 20L);
    }
}
