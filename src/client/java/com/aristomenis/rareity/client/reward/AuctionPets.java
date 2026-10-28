package com.aristomenis.rareity.client.reward;

import com.google.gson.*;

import net.minecraft.nbt.*;

import java.io.*;
import java.util.Base64;

public final class AuctionPets {

    private AuctionPets() {
    }

    public static boolean matches(
            JsonObject auction,
            FishingDrop drop
    ) {
        if (drop == FishingDrop.TOOTH
                || !auction.has("bin")
                || !auction.get("bin").getAsBoolean()) {
            return false;
        }

        if (!drop.tier.equals(
                auction.get("tier").getAsString()
        )) {
            return false;
        }

        if (!auction.get("item_name")
                .getAsString()
                .contains("Megalodon")) {
            return false;
        }

        try {
            String encoded = auction.get("item_bytes").getAsString();

            if (encoded.length() > 262144) {
                return false;
            }

            byte[] bytes = Base64.getDecoder().decode(encoded);

            CompoundTag root = NbtIo.readCompressed(
                    new ByteArrayInputStream(bytes),
                    NbtAccounter.create(2 * 1024 * 1024)
            );

            CompoundTag item = root.getListOrEmpty("i")
                    .getCompoundOrEmpty(0);

            CompoundTag extra = item.getCompoundOrEmpty("tag")
                    .getCompoundOrEmpty("ExtraAttributes");

            if (!"PET".equals(extra.getStringOr("id", ""))) {
                return false;
            }

            JsonObject pet = JsonParser.parseString(
                    extra.getStringOr("petInfo", "{}")
            ).getAsJsonObject();

            boolean skin = pet.has("skin")
                    && !pet.get("skin").isJsonNull()
                    && !pet.get("skin").getAsString().isEmpty();

            return !skin
                    && "MEGALODON".equals(
                    pet.get("type").getAsString()
            )
                    && drop.tier.equals(
                    pet.get("tier").getAsString()
            );

        } catch (Exception error) {
            return false;
        }
    }
}