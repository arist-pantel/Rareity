package com.aristomenis.rareity.client.ghost;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.DyedItemColor;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

public final class GhostAssets {
    private static volatile Map<GhostDrop, byte[]> pngs = Map.of();
    private static final Map<GhostDrop, Identifier> uploaded = new EnumMap<>(GhostDrop.class);
    private static final Set<GhostDrop> failed = EnumSet.noneOf(GhostDrop.class);
    private static ItemStack boots;
    private GhostAssets() {}

    public static void prepare(Path pack) throws IOException {
        Map<GhostDrop, byte[]> found = new EnumMap<>(GhostDrop.class);
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            for (GhostDrop drop : GhostDrop.values()) {
                if (drop == GhostDrop.BOOTS) continue;
                var entry = zip.getEntry("assets/hypixel_skyblock/textures/item/uncategorized/" + drop.command + ".png");
                if (entry == null) continue;
                try (InputStream input = zip.getInputStream(entry)) {
                    byte[] png = input.readNBytes(65_537);
                    if (png.length < 24 || png.length > 65_536) continue;
                    var header = ByteBuffer.wrap(png);
                    if (header.getLong() == 0x89504e470d0a1a0aL
                            && header.getInt(16) == 16 && header.getInt(20) == 16) found.put(drop, png);
                }
            }
        }
        pngs = Map.copyOf(found);
    }
    private static Identifier texture(GhostDrop drop) {
        if (uploaded.containsKey(drop)) return uploaded.get(drop);
        byte[] png = pngs.get(drop);
        if (png == null || failed.contains(drop)) return null;
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            Identifier id = Identifier.fromNamespaceAndPath("rareity", "ghost/" + drop.command);
            Minecraft.getInstance().getTextureManager().register(id,
                    new DynamicTexture(() -> "Rareity Ghost " + drop.displayName, image));
            uploaded.put(drop, id); return id;
        } catch (Exception error) {
            failed.add(drop);
            LoggerFactory.getLogger("rareity").warn("Could not upload Ghost texture {}", drop, error);
            return null;
        }
    }
    public static void render(GuiGraphicsExtractor graphics, GhostDrop drop, float x, float y, float scale) {
        Identifier texture = drop == GhostDrop.BOOTS ? null : texture(drop);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y); graphics.pose().scale(scale, scale);
            if (drop == GhostDrop.BOOTS) {
                if (boots == null) {
                    boots = new ItemStack(Items.LEATHER_BOOTS);
                    boots.set(DataComponents.DYED_COLOR, new DyedItemColor(0x808080));
                }
                graphics.item(boots, -8, -8);
            } else if (texture != null) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, -8, -8, 0F, 0F, 16, 16, 16, 16);
            } else {
                graphics.text(Minecraft.getInstance().font, "?", -3, -4, 0xFFFFAA00, true);
            }
        } finally { graphics.pose().popMatrix(); }
    }
    public static String status() { return pngs.size() + "/3 official Ghost PNGs; gray Ghostly Boots configured"; }
    public static void close() {
        for (Identifier id : uploaded.values()) Minecraft.getInstance().getTextureManager().release(id);
        uploaded.clear(); failed.clear(); boots = null;
    }
}