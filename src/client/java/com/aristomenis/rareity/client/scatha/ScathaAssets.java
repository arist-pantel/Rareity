package com.aristomenis.rareity.client.scatha;

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

public final class ScathaAssets {
    private static volatile Map<ScathaDrop, byte[]> pngs = Map.of();
    private static final Map<ScathaDrop, Identifier> uploaded = new EnumMap<>(ScathaDrop.class);
    private static final Set<ScathaDrop> failed = EnumSet.noneOf(ScathaDrop.class);
    private static final Map<String, ItemStack> heads = new HashMap<>();
    private ScathaAssets() {}

    /** Reuses the existing verified Hypixel pack; this never downloads a second pack. */
    public static void prepare(Path pack) throws IOException {
        Map<ScathaDrop, byte[]> found = new EnumMap<>(ScathaDrop.class);
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            for (ScathaDrop drop : ScathaDrop.values()) {
                if (drop.texturePath == null) continue;
                var entry = zip.getEntry("assets/hypixel_skyblock/textures/item/" + drop.texturePath + ".png");
                if (entry == null) continue;
                try (InputStream input = zip.getInputStream(entry)) {
                    byte[] png = input.readNBytes(65_537);
                    if (png.length < 24 || png.length > 65_536) continue;
                    ByteBuffer header = ByteBuffer.wrap(png);
                    if (header.getLong() == 0x89504e470d0a1a0aL
                            && header.getInt(16) == 16 && header.getInt(20) == 16) found.put(drop, png);
                }
            }
        }
        pngs = Map.copyOf(found);
    }
    private static ItemStack head(String hash) {
        return heads.computeIfAbsent(hash, key -> {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/" + key + "\"}}}";
            String value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            var properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", value)));
            var profile = new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)), "RareityScatha", properties);
            ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
            return stack;
        });
    }
    public static void warmHeads() {
        for (String hash : List.of(ScathaDrop.SCATHA_SKIN, ScathaDrop.BRAN.skinHash)) {
            var profile = head(hash).get(DataComponents.PROFILE);
            Minecraft.getInstance().getSkinManager().get(profile.partialProfile());
        }
    }
    private static Identifier texture(ScathaDrop drop) {
        if (uploaded.containsKey(drop)) return uploaded.get(drop);
        byte[] png = pngs.get(drop);
        if (png == null || failed.contains(drop)) return null;
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            Identifier id = Identifier.fromNamespaceAndPath("rareity", "scatha/" + drop.command);
            Minecraft.getInstance().getTextureManager().register(id,
                    new DynamicTexture(() -> "Rareity Scatha " + drop.displayName, image));
            uploaded.put(drop, id);
            return id;
        } catch (Exception error) {
            failed.add(drop);
            LoggerFactory.getLogger("rareity").warn("Scatha reward texture upload failed: {}", drop, error);
            return null;
        }
    }
    public static void draw(GuiGraphicsExtractor graphics, ScathaDrop drop, float x, float y, float scale) {
        Identifier texture = drop.texturePath == null ? null : texture(drop);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y); graphics.pose().scale(scale, scale);
            if (drop.pet() || drop.skinHash != null) {
                graphics.item(head(drop.pet() ? ScathaDrop.SCATHA_SKIN : drop.skinHash), -8, -8);
            } else if (texture != null) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, -8, -8, 0F, 0F, 16, 16, 16, 16);
            } else {
                graphics.text(Minecraft.getInstance().font, "?", -3, -4, 0xFFFFAA00, true);
            }
        } finally { graphics.pose().popMatrix(); }
    }
    public static void drawWorm(GuiGraphicsExtractor graphics, float x, float y, float scale) {
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y); graphics.pose().scale(scale, scale);
            graphics.item(head(ScathaDrop.SCATHA_SKIN), -8, -8);
        } finally { graphics.pose().popMatrix(); }
    }
    public static String status() {
        return pngs.size() + "/6 official Scatha loot PNGs; Scatha and Block Bran head skins configured";
    }
    public static void close() {
        for (Identifier id : uploaded.values()) Minecraft.getInstance().getTextureManager().release(id);
        uploaded.clear(); failed.clear(); heads.clear();
    }
}