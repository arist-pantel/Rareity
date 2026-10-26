package com.aristomenis.rareity.client.item;

import com.aristomenis.rareity.client.texture.RodTextures;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class RodCache {

    private static ItemStack lastRod = ItemStack.EMPTY;

    private RodCache() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(RodCache::update);

        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> lastRod = ItemStack.EMPTY
        );
    }

    private static void update(Minecraft client) {
        if (client.player == null || client.level == null) {
            lastRod = ItemStack.EMPTY;
            return;
        }

        ItemStack candidate = client.player.getMainHandItem();

        if (!isRod(candidate)) {
            candidate = client.player.getOffhandItem();
        }

        if (isRod(candidate)
                && !ItemStack.isSameItemSameComponents(lastRod, candidate)) {
            lastRod = candidate.copy();
            lastRod.setCount(1);
        }
    }

    public static boolean isRod(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        String skyblockId = skyblockId(stack);

        if (!skyblockId.isEmpty()) {
            Identifier model = stack.get(DataComponents.ITEM_MODEL);

            return model != null
                    && RodTextures.recognizes(model, skyblockId);
        }

        return stack.is(Items.FISHING_ROD);
    }

    public static String skyblockId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);

        return data == null
                ? ""
                : data.copyTag().getStringOr("id", "");
    }

    public static ItemStack capture() {
        update(Minecraft.getInstance());

        return lastRod.isEmpty()
                ? new ItemStack(Items.FISHING_ROD)
                : lastRod.copy();
    }
}