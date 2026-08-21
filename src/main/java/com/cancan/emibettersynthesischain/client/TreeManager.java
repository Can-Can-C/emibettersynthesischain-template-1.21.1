package com.cancan.emibettersynthesischain.client;

import java.util.ArrayList;
import java.util.List;

import com.cancan.emibettersynthesischain.EMIBettersynthesischain;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 已加入合成树的物品集合（多树）+ 持久化。
 *
 * <p>每个条目 = 目标物品 + 其"最后一步配方 id"（加入时查看的配方，目标拆解不需要默认配方）。
 * 保存完整 NBT（含组件）；旧格式也能读。纯业务模型，不依赖 EMI。</p>
 */
public class TreeManager {
    public static final TreeManager INSTANCE = new TreeManager();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "trees.json";

    private final List<ItemStack> items = new ArrayList<>();
    private final List<ResourceLocation> recipeIds = new ArrayList<>();
    /** 每条目的"单次合成数量"（仅会话内，默认 1，范围 1-999，重启回 1；每树独立）。 */
    private final List<Integer> amounts = new ArrayList<>();
    private final List<String> pending = new ArrayList<>();
    private boolean dirty = true;
    private boolean resolved = false;

    private TreeManager() {
    }

    public int size() {
        resolvePending();
        return items.size();
    }

    public ItemStack getItem(int index) {
        resolvePending();
        return index >= 0 && index < items.size() ? items.get(index) : null;
    }

    /** 目标"最后一步配方"id（可为 null）。 */
    public ResourceLocation getRecipeId(int index) {
        resolvePending();
        return index >= 0 && index < recipeIds.size() ? recipeIds.get(index) : null;
    }

    /** 该树（条目）的"单次合成数量"（默认 1）。 */
    public int getAmount(int index) {
        resolvePending();
        return index >= 0 && index < amounts.size() ? amounts.get(index) : 1;
    }

    /** 设置该树的"单次合成数量"（1-999，会话内；落盘）。 */
    public void setAmount(int index, int amount) {
        resolvePending();
        if (index < 0 || index >= amounts.size()) {
            return;
        }
        int clamped = Math.max(1, Math.min(999, amount));
        if (amounts.get(index) != clamped) {
            amounts.set(index, clamped);
            dirty = true;
            save();
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void markClean() {
        dirty = false;
    }

    public void add(ItemStack stack) {
        add(stack, null);
    }

    /** 加入一个目标物品（按 物品+组件 去重），并记录"最后一步配方"id。 */
    public void add(ItemStack stack, ResourceLocation recipeId) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        for (int i = 0; i < items.size(); i++) {
            if (ItemStack.isSameItemSameComponents(items.get(i), copy)) {
                if (recipeId != null) {
                    recipeIds.set(i, recipeId); // 刷新目标配方
                }
                EMIBettersynthesischain.LOGGER.info("EBS tree item already present: {}", copy);
                return;
            }
        }
        items.add(copy);
        recipeIds.add(recipeId);
        amounts.add(1);
        dirty = true;
        TreeMode.requestScrollToBottom();
        EMIBettersynthesischain.LOGGER.info("EBS added tree item: {}, total={}", copy, items.size());
        save();
    }

    public boolean contains(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (ItemStack existing : items) {
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                return true;
            }
        }
        return false;
    }

    public boolean remove(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            if (ItemStack.isSameItemSameComponents(items.get(i), stack)) {
                ItemStack removed = items.remove(i);
                recipeIds.remove(i);
                amounts.remove(i);
                dirty = true;
                EMIBettersynthesischain.LOGGER.info("EBS removed tree item: {}, total={}", removed, items.size());
                save();
                return true;
            }
        }
        return false;
    }

    public void remove(int index) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        ItemStack removed = items.remove(index);
        recipeIds.remove(index);
        amounts.remove(index);
        dirty = true;
        EMIBettersynthesischain.LOGGER.info("EBS removed tree item: {}, total={}", removed, items.size());
        save();
    }

    public void load() {
        items.clear();
        recipeIds.clear();
        pending.clear();
        resolved = false;
        Path file = dataFile();
        if (!Files.exists(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null || !obj.has("items")) {
                return;
            }
            JsonArray arr = obj.getAsJsonArray("items");
            for (JsonElement e : arr) {
                if (e.isJsonObject()) {
                    // 新格式：{"item": <nbt>, "recipe": <id 或空>, "amount": <数量 可选>}
                    JsonObject o = e.getAsJsonObject();
                    if (o.has("item")) {
                        String item = o.get("item").getAsString();
                        String recipe = o.has("recipe") ? o.get("recipe").getAsString() : null;
                        int amount = o.has("amount") ? o.get("amount").getAsInt() : 1;
                        pending.add("ITEM:" + item + (recipe == null ? "" : "|RECIPE:" + recipe)
                                + "|AMOUNT:" + amount);
                    }
                } else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                    // 旧格式：纯 NBT 或纯 item id
                    pending.add("ITEM:" + e.getAsString());
                }
            }
        } catch (IOException | RuntimeException ex) {
            EMIBettersynthesischain.LOGGER.warn("Failed to load tree list from {}", file, ex);
        }
    }

    /** 进游戏后解析暂存条目（需 RegistryAccess）。 */
    private void resolvePending() {
        if (resolved || pending.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        RegistryAccess access = mc.level.registryAccess();
        for (String raw : pending) {
            ResourceLocation recipeId = null;
            int amount = 1;
            // 可选段（固定顺序 item |RECIPE:<id> |AMOUNT:<n>）
            int sepAmt = raw.indexOf("|AMOUNT:");
            if (sepAmt >= 0) {
                try {
                    amount = Math.max(1, Math.min(999, Integer.parseInt(raw.substring(sepAmt + 8))));
                } catch (NumberFormatException ignored) {
                }
                raw = raw.substring(0, sepAmt);
            }
            int sep = raw.indexOf("|RECIPE:");
            if (sep >= 0) {
                recipeId = ResourceLocation.tryParse(raw.substring(sep + 8));
                raw = raw.substring(0, sep);
            }
            String itemPart = raw;
            if (itemPart.startsWith("ITEM:")) {
                itemPart = itemPart.substring("ITEM:".length());
            }
            final int amt = amount;
            if (itemPart.startsWith("{")) {
                try {
                    CompoundTag tag = TagParser.parseTag(itemPart);
                    ResourceLocation rid = recipeId;
                    ItemStack.parse(access, tag).ifPresent(stack -> {
                        items.add(stack);
                        recipeIds.add(rid);
                        amounts.add(amt);
                    });
                } catch (Exception ex) {
                    EMIBettersynthesischain.LOGGER.warn("Failed to parse tree entry: {}", itemPart, ex);
                }
            } else {
                ResourceLocation key = ResourceLocation.tryParse(itemPart);
                if (key != null && BuiltInRegistries.ITEM.containsKey(key)) {
                    items.add(new ItemStack(BuiltInRegistries.ITEM.get(key)));
                    recipeIds.add(recipeId);
                    amounts.add(amt);
                }
            }
        }
        pending.clear();
        resolved = true;
        dirty = true;
    }

    public void save() {
        Path file = dataFile();
        try {
            JsonArray arr = new JsonArray();
            Minecraft mc = Minecraft.getInstance();
            RegistryAccess access = mc.level != null ? mc.level.registryAccess() : RegistryAccess.EMPTY;
            for (int i = 0; i < items.size(); i++) {
                JsonObject o = new JsonObject();
                ItemStack stack = items.get(i);
                try {
                    Tag tag = stack.save(access);
                    o.add("item", new JsonPrimitive(tag.toString()));
                } catch (Exception ex) {
                    o.add("item", new JsonPrimitive(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
                }
                ResourceLocation rid = getRecipeId(i);
                if (rid != null) {
                    o.add("recipe", new JsonPrimitive(rid.toString()));
                }
                o.add("amount", new JsonPrimitive(getAmount(i)));
                arr.add(o);
            }
            JsonObject obj = new JsonObject();
            obj.add("items", arr);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            EMIBettersynthesischain.LOGGER.warn("Failed to save tree list to {}", file, ex);
        }
    }

    private static Path dataFile() {
        return FMLPaths.CONFIGDIR.get().resolve(EMIBettersynthesischain.MODID).resolve(FILE);
    }
}
