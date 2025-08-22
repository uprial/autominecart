package com.gmail.uprial.autominecart.common;

import org.bukkit.block.Block;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static com.gmail.uprial.autominecart.common.Utils.joinStrings;

public class Formatter {
    public static String format(final Block block) {
        return String.format("%s[%s:%d:%d:%d]",
                block.getType(),
                block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ());
    }

    public static String format(final Inventory inventory) {
        final List<String> contents = new ArrayList<>();
        /*
            getContents() returns a list of nulls
            even when the content isn't actually null,
            so I iterate the content by id.
         */
        for(int i = 0; i < inventory.getSize(); i++) {
            final ItemStack itemStack = inventory.getItem(i);
            if (itemStack != null) {
                final StringBuilder sb = new StringBuilder(itemStack.getType().toString());
                if(itemStack.getMaxStackSize() > 1) {
                    sb.append(String.format("x%d",
                            itemStack.getAmount()));
                }
                if(itemStack.getType().getMaxDurability() > 0) {
                    sb.append(String.format(":%d/%d",
                            ((Damageable)itemStack.getItemMeta()).getDamage(),
                            itemStack.getType().getMaxDurability()));
                }
                contents.add(sb.toString());
            }
        }

        return "[" + joinStrings(", ", contents) + "]";
    }
}
