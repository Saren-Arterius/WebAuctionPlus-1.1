package me.lorenzop.webauctionplus;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class ItemUtils {

    public static Map<String, Object> serialize(ConfigurationSerializable cs) {
        final Map<String, Object> serialized = ItemUtils.recreateMap(cs.serialize());
        for (final Entry<String, Object> entry: serialized.entrySet()) {
            if (entry.getValue() instanceof ConfigurationSerializable) {
                entry.setValue(ItemUtils.serialize((ConfigurationSerializable) entry.getValue()));
            }
        }
        serialized.put(ConfigurationSerialization.SERIALIZED_TYPE_KEY,
                ConfigurationSerialization.getAlias(cs.getClass()));
        return serialized;
    }

    public static Map<String, Object> recreateMap(Map<String, Object> original) {
        final Map<String, Object> map = new HashMap<String, Object>();
        for (final Entry<String, Object> entry: original.entrySet()) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static ConfigurationSerializable deserialize(Map<String, Object> map) {
        for (final Entry<String, Object> entry: map.entrySet()) {
            if (entry.getValue() instanceof Long) {
                entry.setValue(((Long) entry.getValue()).intValue());
            }
        }
        for (final Entry<String, Object> entry: map.entrySet()) {
            if (entry.getValue() instanceof Map
                    && ((Map<?, ?>) entry.getValue()).containsKey(ConfigurationSerialization.SERIALIZED_TYPE_KEY)) {
                entry.setValue(ItemUtils.deserialize((Map<String, Object>) entry.getValue()));
            }
        }
        return ConfigurationSerialization.deserializeObject(map);
    }

    @SuppressWarnings("unchecked")
    public static String encodeMeta(ItemStack stack) {
        if (stack == null) {
            throw new NullPointerException();
        }

        final JSONObject metaJson = new JSONObject();
        metaJson.putAll(ItemUtils.serialize(stack.getItemMeta()));
        final Map<String, Integer> tempEnchantmentMap = new HashMap<String, Integer>();
        if (stack.getType() == Material.ENCHANTED_BOOK) {
            final EnchantmentStorageMeta esm = (EnchantmentStorageMeta) stack.getItemMeta();
            for (final Entry<Enchantment, Integer> entry: esm.getStoredEnchants().entrySet()) {
                tempEnchantmentMap.put(entry.getKey().getName(), entry.getValue());
            }
        } else {
            for (final Entry<Enchantment, Integer> entry: stack.getEnchantments().entrySet()) {
                tempEnchantmentMap.put(entry.getKey().getName(), entry.getValue());
            }
        }
        final JSONObject itemJson = new JSONObject();
        itemJson.put("item-meta", metaJson.toJSONString());
        itemJson.put("all-enchants", JSONObject.toJSONString(tempEnchantmentMap));
        itemJson.put("item-type", stack.getType().name());
        itemJson.put("item-qty", stack.getAmount());
        return itemJson.toJSONString();
    }

    @SuppressWarnings("unchecked")
    public static void restoreMeta(ItemStack stack, String string) {
        Map<String, Long> enchantmentMap = null;
        Map<String, Object> metaMap = null;
        final Map<String, String> itemJson = (Map<String, String>) JSONValue.parse(string);
        for (final Entry<String, String> entry: itemJson.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("item-meta")) {
                metaMap = (Map<String, Object>) JSONValue.parse(entry.getValue());
            } else if (entry.getKey().equalsIgnoreCase("all-enchants")) {
                enchantmentMap = (Map<String, Long>) JSONValue.parse(entry.getValue());
            }
        }
        if (stack.getType() == Material.ENCHANTED_BOOK) {
            final EnchantmentStorageMeta esm = (EnchantmentStorageMeta) ItemUtils.deserialize(metaMap);
            for (final Entry<String, Long> entry: enchantmentMap.entrySet()) {
                esm.addStoredEnchant(Enchantment.getByName(entry.getKey()), entry.getValue().intValue(), false);
            }
            stack.setItemMeta(esm);
        } else {
            stack.setItemMeta((ItemMeta) ItemUtils.deserialize(metaMap));
            for (final Entry<String, Long> entry: enchantmentMap.entrySet()) {
                stack.addUnsafeEnchantment(Enchantment.getByName(entry.getKey()), entry.getValue().intValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void restoreMeta(String string) {
        Map<String, Long> enchantmentMap = null;
        Map<String, Object> metaMap = null;
        Material itemType = Material.DIRT;
        int itemQty = 1;
        final Map<String, String> itemJson = (Map<String, String>) JSONValue.parse(string);
        for (final Entry<String, String> entry: itemJson.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("item-meta")) {
                metaMap = (Map<String, Object>) JSONValue.parse(entry.getValue());
            } else if (entry.getKey().equalsIgnoreCase("all-enchants")) {
                enchantmentMap = (Map<String, Long>) JSONValue.parse(entry.getValue());
            } else if (entry.getKey().equalsIgnoreCase("item-type")) {
                itemType = Material.valueOf(entry.getValue());
            } else if (entry.getKey().equalsIgnoreCase("item-qty")) {
                itemQty = Integer.valueOf(entry.getValue());
            }
        }
        final ItemStack stack = new ItemStack(itemType, itemQty);
        if (stack.getType() == Material.ENCHANTED_BOOK) {
            final EnchantmentStorageMeta esm = (EnchantmentStorageMeta) ItemUtils.deserialize(metaMap);
            for (final Entry<String, Long> entry: enchantmentMap.entrySet()) {
                esm.addStoredEnchant(Enchantment.getByName(entry.getKey()), entry.getValue().intValue(), false);
            }
            stack.setItemMeta(esm);
        } else {
            stack.setItemMeta((ItemMeta) ItemUtils.deserialize(metaMap));
            for (final Entry<String, Long> entry: enchantmentMap.entrySet()) {
                stack.addUnsafeEnchantment(Enchantment.getByName(entry.getKey()), entry.getValue().intValue());
            }
        }
    }
}
