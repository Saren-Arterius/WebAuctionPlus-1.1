package me.lorenzop.webauctionplus;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class WebItemMeta {
    @Override
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    private WebItemMeta() {
    }

    // encode enchantments for database storage
    public static String encodeItem(final ItemStack stack, final Player player) {
        return ItemUtils.encodeMeta(stack);
    }

    // decode enchantments from string
    public static void decodeItem(final ItemStack stack, final Player player, final String str) {
        ItemUtils.restoreMeta(stack, str);
    }

}
