package com.aristomenis.rareity.client.corpse;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ResolvableProfile;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

public final class CorpseTextures {
    private static volatile Map<CorpseDrop, byte[]> pngs = Map.of();
    private static final Map<CorpseDrop, Identifier> uploaded = new EnumMap<>(CorpseDrop.class);
    private static final Map<CorpseDrop, ItemStack> heads = new EnumMap<>(CorpseDrop.class);
    private static final Set<CorpseDrop> failed = EnumSet.noneOf(CorpseDrop.class);
    private CorpseTextures() {}

    // Called by the existing official-pack worker; no duplicate pack download.
    public static void prepare(Path pack) throws IOException {
        Map<CorpseDrop, byte[]> loaded = new EnumMap<>(CorpseDrop.class);
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            for (CorpseDrop drop : CorpseDrop.values()) {
                if (drop.texturePath == null) continue;
                var entry = zip.getEntry("assets/hypixel_skyblock/textures/item/" + drop.texturePath + ".png");
                if (entry == null) continue;
                try (InputStream input = zip.getInputStream(entry)) {
                    byte[] png = input.readNBytes(65_537);
                    if (png.length < 24 || png.length > 65_536) continue;
                    ByteBuffer header = ByteBuffer.wrap(png);
                    if (header.getLong() == 0x89504e470d0a1a0aL
                            && header.getInt(16) == 16 && header.getInt(20) == 16) loaded.put(drop, png);
                }
            }
        }
        pngs = Map.copyOf(loaded);
    }
    private static ItemStack head(CorpseDrop drop) {
        return heads.computeIfAbsent(drop, key -> {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/"
                    + key.skinHash + "\"}}}";
            String value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            var properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", value)));
            var profile = new GameProfile(UUID.nameUUIDFromBytes(key.skinHash.getBytes(StandardCharsets.UTF_8)),
                    "RareityReward", properties);
            ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
            return stack;
        });
    }
    public static void render(GuiGraphicsExtractor graphics, CorpseDrop drop, float x, float y, float scale) {
        if (drop != null && pngs.containsKey(drop) && !uploaded.containsKey(drop) && !failed.contains(drop)) {
            try {
                var image = NativeImage.read(new ByteArrayInputStream(pngs.get(drop)));
                Identifier id = Identifier.fromNamespaceAndPath("rareity", "com/aristomenis/rareity/client/corpse/" + drop.command);
                Minecraft.getInstance().getTextureManager().register(id,
                        new DynamicTexture(() -> "Rareity " + drop.displayName, image));
                uploaded.put(drop, id);
            } catch (Exception error) {
                failed.add(drop);
                LoggerFactory.getLogger("rareity").warn("Could not upload corpse reward texture", error);
            }
        }
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y); graphics.pose().scale(scale, scale);
            if (drop == null) clown(graphics);
            else if (drop.skinHash != null) graphics.item(head(drop), -8, -8);
            else if (uploaded.containsKey(drop)) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, uploaded.get(drop), -8, -8, 0F, 0F, 16, 16, 16, 16);
            } else {
                graphics.text(Minecraft.getInstance().font, "?", -3, -4, 0xFFFFAA00, true);
            }
        } finally { graphics.pose().popMatrix(); }
    }
    private static void clown(GuiGraphicsExtractor graphics) {
        // A small pixel clown, since Minecraft's font does not reliably contain the emoji.
        graphics.fill(-9, -5, -5, 5, 0xFFEE3333);
        graphics.fill(5, -5, 9, 5, 0xFFEE3333);
        for (int row = -7; row <= 7; row++) {
            int width = (int) Math.sqrt(49 - row * row);
            graphics.fill(-width, row, width + 1, row + 1, 0xFFFFF2E4);
        }
        graphics.fill(-4, -3, -2, -1, 0xFF3388DD);
        graphics.fill(2, -3, 4, -1, 0xFF3388DD);
        graphics.fill(-1, -1, 2, 2, 0xFFFF2222);
        graphics.fill(-4, 3, 5, 5, 0xFFDD2222);
        graphics.fill(-2, 3, 3, 4, 0xFFFFFFFF);
    }
    public static String status() { return pngs.size() + "/3 official key textures; 3 reward head skins configured"; }
    public static void close() {
        for (Identifier id : uploaded.values()) Minecraft.getInstance().getTextureManager().release(id);
        uploaded.clear(); heads.clear(); failed.clear();
    }
}