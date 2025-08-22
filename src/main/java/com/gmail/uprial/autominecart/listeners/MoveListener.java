package com.gmail.uprial.autominecart.listeners;

import com.gmail.uprial.autominecart.AutoMinecart;
import com.gmail.uprial.autominecart.common.CustomLogger;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Rail;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.Function;

import static com.gmail.uprial.autominecart.common.Formatter.format;

public class MoveListener implements Listener {
    private static final Random RANDOM = new Random();

    private static class InventoryItem {
        final Inventory inventory;
        final Integer id;

        ItemStack item;

        InventoryItem(final Inventory inventory, final Integer id) {
            this.inventory = inventory;
            this.id = id;
            this.item = inventory.getItem(id);
        }

        ItemStack getItem() {
            return item;
        }

        Material getType() {
            return getItem().getType();
        }

        void decrement() {
            getItem().setAmount(getItem().getAmount() - 1);
        }

        void damage() {
            /*
                According to https://minecraft.wiki/w/Unbreaking,
                For tools and weapons, there is a 100%÷(level+1) chance
                that using the item reduces durability.
             */
            final int unbreaking = getItem().getEnchantmentLevel(Enchantment.UNBREAKING);
            if(unbreaking > 0) {
                if(RANDOM.nextInt(unbreaking + 1) != 0) {
                    return;
                }
            }

            final Damageable damageable = (Damageable)getItem().getItemMeta();
            damageable.setDamage(damageable.getDamage() + 1);
            if(damageable.getDamage() >= getType().getMaxDurability()) {
                inventory.setItem(id, null);
                item = null;
            } else {
                getItem().setItemMeta(damageable);
            }
        }
    }

    private final AutoMinecart plugin;
    private final CustomLogger customLogger;

    public MoveListener(final AutoMinecart plugin,
                        final CustomLogger customLogger) {
        this.plugin = plugin;
        this.customLogger = customLogger;
    }

    private static final Set<Material> RAIL_TYPES = ImmutableSet.<Material>builder()
            .add(Material.RAIL)
            .add(Material.ACTIVATOR_RAIL)
            .add(Material.DETECTOR_RAIL)
            .add(Material.POWERED_RAIL)
            .build();

    // Our plugin has the last word on the world population.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleMove(VehicleMoveEvent event) {
        if((plugin.getAutoMinecartConfig().isEnabled())
                && (event.getVehicle() instanceof StorageMinecart)) {

            final StorageMinecart minecart = (StorageMinecart)event.getVehicle();

            final Vector minecartDirection = event.getTo().toVector();
            minecartDirection.subtract(event.getFrom().toVector());
            minecartDirection.multiply(minecartDirection.length());

            Block nextBlock;

            {
                final Block currentBlock = minecart.getLocation().getBlock();

                if (RAIL_TYPES.contains(currentBlock.getType())) {
                    final Rail railDB = (Rail) currentBlock.getBlockData();
                    if (!getAllowedRailShapes(minecartDirection).contains(railDB.getShape())) {
                        customLogger.debug(String.format("Wrong rail shape: %s-%s",
                                format(currentBlock), railDB.getShape()));
                        return;
                    }
                }

                nextBlock = getDirectedBlock(currentBlock, minecartDirection);
                if (RAIL_TYPES.contains(nextBlock.getType())) {
                    // Already deployed
                    return;
                }
            }

            final Block aboveNextBlock = getBlockWithChangedY(nextBlock, +1);
            if (RAIL_TYPES.contains(aboveNextBlock.getType())) {
                // Already deployed ABOVE
                return;
            }

            {
                final Block underNextBlock = getBlockWithChangedY(nextBlock, -1);
                if (!underNextBlock.getType().isSolid()) {
                    if (RAIL_TYPES.contains(underNextBlock.getType())) {
                        // Already deployed UNDER
                        return;
                    }

                    final Block underUnderNextBlock = getBlockWithChangedY(nextBlock, -2);
                    if(!underUnderNextBlock.getType().isSolid()) {
                        customLogger.debug(String.format("No solid surface for rails: %s and %s",
                                format(underNextBlock), format(underUnderNextBlock)));
                        return;
                    } else {
                        nextBlock = underNextBlock;
                    }
                }
            }

            final Inventory inventory = minecart.getInventory();

            InventoryItem rail = fetchInventory(inventory, Material.RAIL);
            if(rail == null) {
                /*
                    WARNING: here's a trade-off:
                    though we may end up deploying a powered rail in this specific block,
                    there is no reason to continue deployment without rails in general.
                 */
                // Inventory has no rails
                return;
            }

            for(final Block blockIterator : Arrays.asList(nextBlock, aboveNextBlock)) {
                if(!breakBlock(blockIterator, inventory)) {
                    customLogger.debug(String.format("Can't break %s via %s for rails",
                            format(blockIterator), format(inventory)));
                    return;
                }
            }

            if((nextBlock.getX() % 16 == 0) || (nextBlock.getZ() % 16 == 0)) {
                final InventoryItem poweredRail = fetchInventory(inventory, Material.POWERED_RAIL);

                // Inventory has powered rails
                if(poweredRail != null) {
                    final InventoryItem redstoneTorch = fetchInventory(inventory, Material.REDSTONE_TORCH);

                    // Inventory has redstone torches
                    if(redstoneTorch != null) {
                        for (final Double angle : Arrays.asList(-Math.PI / 2.0D, Math.PI / 2.0D)) {
                            final Vector poweredDirection = minecartDirection.clone();
                            poweredDirection.rotateAroundY(angle);

                            final Block redstoneBlock = getDirectedBlock(nextBlock, poweredDirection);
                            if (RAIL_TYPES.contains(redstoneBlock.getType())) {
                                customLogger.debug(String.format("Rails took the place for redstone torch: %s",
                                        format(redstoneBlock)));
                                continue;
                            }

                            final Block underRedstoneBlock = getBlockWithChangedY(redstoneBlock, -1);

                            if (!underRedstoneBlock.getType().isSolid()) {
                                customLogger.debug(String.format("No solid surface for redstone torch: %s",
                                        format(underRedstoneBlock)));
                                continue;
                            }

                            if (!breakBlock(redstoneBlock, inventory)) {
                                customLogger.debug(String.format("Can't break %s via %s for redstone torch",
                                        format(redstoneBlock), format(inventory)));
                                continue;
                            }

                            redstoneBlock.setType(Material.REDSTONE_TORCH);
                            redstoneTorch.decrement();

                            rail = poweredRail;

                            break;
                        }
                    }
                }
            }

            nextBlock.setType(rail.getType());
            rail.decrement();

            customLogger.debug(String.format("Deployed %s with %s",
                    format(nextBlock), format(inventory)));
        }
    }

    private static final Map<Material,Integer> CHEST_TOOLS = ImmutableMap.<Material,Integer>builder()
            .put(Material.WOODEN_PICKAXE, 1)
            .put(Material.STONE_PICKAXE, 2)
            .put(Material.IRON_PICKAXE, 3)
            .put(Material.GOLDEN_PICKAXE, 4)
            .put(Material.DIAMOND_PICKAXE, 5)
            .put(Material.NETHERITE_PICKAXE, 6)
            .build();

    private static InventoryItem getTool(final Inventory inventory) {
        /*
            This function is called up to 3 times per one minecart move,
            fetching the whole 27 slots of its chest inventory,
            and so could be improved via caching.

            But also, the cached tool can break between these 3 blocks,
            which makes the cache invalidation hard, so I passed.
         */
        return fetchInventory(inventory,
                (final ItemStack itemStack) ->
                        CHEST_TOOLS.get(itemStack.getType()));
    }

    private static Block getDirectedBlock(final Block block, final Vector direction) {
        final double dx = Math.abs(direction.getX());
        final double dz = Math.abs(direction.getZ());

        int x = block.getX();
        int z = block.getZ();

        if(dx > dz) {
            x += (int)Math.signum(direction.getX());
        } else {
            z += (int)Math.signum(direction.getZ());
        }

        return block.getWorld().getBlockAt(x, block.getY(), z);
    }

    private static final Set<Rail.Shape> EAST_RAIL_SHAPES = ImmutableSet.<Rail.Shape>builder()
            .add(Rail.Shape.EAST_WEST)
            .add(Rail.Shape.ASCENDING_EAST)
            .add(Rail.Shape.ASCENDING_WEST)
            .add(Rail.Shape.NORTH_EAST)
            .add(Rail.Shape.SOUTH_EAST)
            .build();
    private static final Set<Rail.Shape> WEST_RAIL_SHAPES = ImmutableSet.<Rail.Shape>builder()
            .add(Rail.Shape.EAST_WEST)
            .add(Rail.Shape.ASCENDING_EAST)
            .add(Rail.Shape.ASCENDING_WEST)
            .add(Rail.Shape.NORTH_WEST)
            .add(Rail.Shape.SOUTH_WEST)
            .build();
    private static final Set<Rail.Shape> SOUTH_RAIL_SHAPES = ImmutableSet.<Rail.Shape>builder()
            .add(Rail.Shape.NORTH_SOUTH)
            .add(Rail.Shape.ASCENDING_SOUTH)
            .add(Rail.Shape.ASCENDING_NORTH)
            .add(Rail.Shape.SOUTH_WEST)
            .add(Rail.Shape.SOUTH_EAST)
            .build();
    private static final Set<Rail.Shape> NORTH_RAIL_SHAPES = ImmutableSet.<Rail.Shape>builder()
            .add(Rail.Shape.NORTH_SOUTH)
            .add(Rail.Shape.ASCENDING_SOUTH)
            .add(Rail.Shape.ASCENDING_NORTH)
            .add(Rail.Shape.NORTH_WEST)
            .add(Rail.Shape.NORTH_EAST)
            .build();
    private static Set<Rail.Shape> getAllowedRailShapes(final Vector direction) {
        if(Math.abs(direction.getX()) > Math.abs(direction.getZ())) {
            if(direction.getX() > 0) {
                return EAST_RAIL_SHAPES;
            } else {
                return WEST_RAIL_SHAPES;
            }
        } else {
            if(direction.getZ() > 0) {
                return SOUTH_RAIL_SHAPES;
            } else {
                return NORTH_RAIL_SHAPES;
            }
        }
    }

    private static Block getBlockWithChangedY(final Block block, final int dy) {
        return block.getWorld().getBlockAt(block.getLocation().add(0.0D, dy, 0.0D));
    }

    private static InventoryItem fetchInventory(final Inventory inventory, final Material material) {
        return fetchInventory(inventory,
                (final ItemStack itemStack) ->
                        itemStack.getType().equals(material) ? 1 : null);
    }

    private static InventoryItem fetchInventory(final Inventory inventory, final Function<ItemStack, Integer> f) {
        /*
            getContents() returns a list of nulls
            even when the content isn't actually null,
            so I iterate the content by id.
         */
        Integer topId = null;
        Integer topPower = null;
        for(int i = 0; i < inventory.getSize(); i++) {
            final ItemStack itemStack = inventory.getItem(i);
            if (itemStack != null) {
                final Integer power = f.apply(itemStack);
                if((power != null) && ((topPower == null) || (power > topPower))) {
                    topPower = power;
                    topId = i;
                }
            }
        }

        return (topId != null) ? new InventoryItem(inventory, topId) : null;
    }

    /*
        According to https://minecraft.wiki/w/Breaking#Blocks_by_hardness,
        the most critical blocks have hardness at least 22.5.
     */
    private static final float MAX_BLOCK_HARDNESS = 20.0f;

    private static boolean breakBlock(final Block block, final Inventory inventory) {
        if (block.isEmpty() || block.isLiquid()) {
            return true;
        } else if (block.getType().getHardness() > MAX_BLOCK_HARDNESS) {
            return false;
        }

        final InventoryItem tool = getTool(inventory);

        if(tool == null) {
            return false;
        } else if(block.breakNaturally(tool.getItem())) {
            tool.damage();
            return true;
        } else {
            return false;
        }
    }
}
