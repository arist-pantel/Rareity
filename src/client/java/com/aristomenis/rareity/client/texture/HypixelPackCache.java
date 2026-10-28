package com.aristomenis.rareity.client.texture;

import com.google.gson.*;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;


public final class HypixelPackCache {

    public static final int PACK_FORMAT = 97;

    private static final URI API = URI.create(
            "https://api.hypixel.net/v2/resources/packs"
    );

    private static final int MAX_PACK_BYTES = 64 * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 1024 * 1024;

    private static final String RODS =
            "assets/hypixel_skyblock/items/item/fishing/rod/";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private HypixelPackCache() {
    }

    public record Asset(byte[] png) {
    }

    public record Result(
            Map<String, Asset> rods,
            String hash,
            boolean offline
    ) {
    }

    public static Result load(Path directory) throws Exception {
        Files.createDirectories(directory);

        Path manifest = directory.resolve("manifest.json");
        Exception failure;

        try {
            JsonObject version;

            boolean recent = Files.isRegularFile(manifest)
                    && System.currentTimeMillis()
                    - Files.getLastModifiedTime(manifest).toMillis()
                    < Duration.ofHours(24).toMillis();

            if (recent) {
                version = JsonParser.parseString(
                        Files.readString(manifest)
                ).getAsJsonObject();
            } else {
                JsonObject response = JsonParser.parseString(
                        new String(
                                download(API, 1024 * 1024),
                                StandardCharsets.UTF_8
                        )
                ).getAsJsonObject();

                version = selectVersion(response);
            }

            String hash = validHash(
                    version.get("hash").getAsString()
            );

            Path zip = directory.resolve(hash + ".zip");

            if (!Files.isRegularFile(zip)
                    || !sha1(Files.readAllBytes(zip)).equals(hash)) {

                URI url = URI.create(
                        version.get("url").getAsString()
                );

                if (!"https".equals(url.getScheme())
                        || !"resourcepacks.hypixel.net".equals(url.getHost())) {
                    throw new IOException(
                            "Unexpected resource-pack download host"
                    );
                }

                byte[] bytes = download(url, MAX_PACK_BYTES);

                if (!sha1(bytes).equals(hash)) {
                    throw new IOException(
                            "Resource-pack SHA-1 mismatch"
                    );
                }

                atomicWrite(zip, bytes);
            }

            Map<String, Asset> rods = readRods(zip);

            if (!recent) {
                atomicWrite(
                        manifest,
                        version.toString().getBytes(StandardCharsets.UTF_8)
                );
            }

            return new Result(rods, hash, false);

        } catch (Exception error) {
            failure = error;
        }

        if (Files.isRegularFile(manifest)) {
            try {
                JsonObject version = JsonParser.parseString(
                        Files.readString(manifest)
                ).getAsJsonObject();

                String hash = validHash(
                        version.get("hash").getAsString()
                );

                Path zip = directory.resolve(hash + ".zip");

                if (Files.size(zip) <= MAX_PACK_BYTES
                        && sha1(Files.readAllBytes(zip)).equals(hash)) {
                    return new Result(readRods(zip), hash, true);
                }

            } catch (Exception ignored) {

            }
        }

        throw failure;
    }

    static JsonObject selectVersion(
            JsonObject response
    ) throws IOException {

        if (!response.get("success").getAsBoolean()) {
            throw new IOException("Hypixel API returned failure");
        }

        for (JsonElement element : response.getAsJsonArray("packs")) {
            JsonObject pack = element.getAsJsonObject();

            if (!"SkyBlock".equals(pack.get("id").getAsString())) {
                continue;
            }

            for (JsonElement candidate : pack.getAsJsonArray("versions")) {
                JsonObject version = candidate.getAsJsonObject();

                if (version.get("packFormat").getAsInt() == PACK_FORMAT) {
                    return version;
                }
            }
        }

        throw new IOException(
                "No SkyBlock resource pack for Minecraft 26.2 (format 97)"
        );
    }

    public static Map<String, Asset> readRods(
            Path path
    ) throws IOException {

        Map<String, Asset> rods = new HashMap<>();

        try (ZipFile zip = new ZipFile(path.toFile())) {
            JsonObject metadata = json(
                    zip,
                    "pack.mcmeta"
            ).getAsJsonObject("pack");

            if (metadata.get("pack_format").getAsInt() != PACK_FORMAT) {
                throw new IOException("Wrong resource-pack format");
            }

            for (ZipEntry entry : Collections.list(zip.entries())) {
                String name = entry.getName();

                if (!name.startsWith(RODS) || !name.endsWith(".json")) {
                    continue;
                }

                try {
                    JsonObject item = json(
                            zip,
                            name
                    ).getAsJsonObject("model");

                    String model = castModel(item);

                    if (model == null) {
                        continue;
                    }

                    String texture = layerZero(zip, model, 0);

                    if (texture == null) {
                        continue;
                    }

                    byte[] png = read(
                            zip,
                            assetPath(texture, "textures", ".png")
                    );

                    if (!isStatic16Png(png)) {
                        continue;
                    }

                    String itemId = "hypixel_skyblock:"
                            + name.substring(
                            "assets/hypixel_skyblock/items/".length(),
                            name.length() - 5
                    );

                    rods.put(itemId, new Asset(png));

                } catch (IOException | RuntimeException ignored) {

                }
            }
        }

        if (rods.isEmpty()) {
            throw new IOException(
                    "No supported clean cast rod textures found"
            );
        }

        return Map.copyOf(rods);
    }

    private static String castModel(JsonObject node) {
        String type = node.get("type").getAsString();

        if ("minecraft:condition".equals(type)
                && node.has("property")
                && "minecraft:fishing_rod/cast".equals(
                node.get("property").getAsString()
        )) {

            JsonObject cast = node.getAsJsonObject("on_true");

            if ("minecraft:model".equals(
                    cast.get("type").getAsString()
            )) {
                return cast.get("model").getAsString();
            }
        }

        return null;
    }

    private static String layerZero(
            ZipFile zip,
            String model,
            int depth
    ) throws IOException {

        if (depth > 8) {
            throw new IOException("Model parent chain too deep");
        }

        JsonObject definition = json(
                zip,
                assetPath(model, "models", ".json")
        );

        JsonObject textures = definition.getAsJsonObject("textures");

        if (textures != null && textures.has("layer0")) {
            String value = textures.get("layer0").getAsString();

            if (!value.startsWith("#")) {
                return value;
            }
        }

        if (definition.has("parent")) {
            return layerZero(
                    zip,
                    definition.get("parent").getAsString(),
                    depth + 1
            );
        }

        return null;
    }

    private static String assetPath(
            String id,
            String folder,
            String extension
    ) throws IOException {

        String[] parts = id.split(":", 2);

        String namespace = parts.length == 2
                ? parts[0]
                : "minecraft";

        String name = parts.length == 2
                ? parts[1]
                : parts[0];

        if (!namespace.matches("[a-z0-9_.-]+")
                || !name.matches("[a-z0-9_/.-]+")
                || name.contains("..")) {
            throw new IOException("Invalid resource identifier");
        }

        return "assets/" + namespace
                + "/" + folder
                + "/" + name
                + extension;
    }

    private static JsonObject json(
            ZipFile zip,
            String name
    ) throws IOException {

        return JsonParser.parseString(
                new String(
                        read(zip, name),
                        StandardCharsets.UTF_8
                )
        ).getAsJsonObject();
    }

    private static byte[] read(
            ZipFile zip,
            String name
    ) throws IOException {

        ZipEntry entry = zip.getEntry(name);

        if (entry == null || entry.getSize() > MAX_ENTRY_BYTES) {
            throw new IOException("Missing or oversized " + name);
        }

        try (InputStream input = zip.getInputStream(entry)) {
            byte[] data = input.readNBytes(MAX_ENTRY_BYTES + 1);

            if (data.length > MAX_ENTRY_BYTES) {
                throw new IOException("Oversized ZIP entry");
            }

            return data;
        }
    }

    private static boolean isStatic16Png(byte[] png) {
        if (png.length < 24) {
            return false;
        }

        var buffer = java.nio.ByteBuffer.wrap(png);

        return buffer.getLong() == 0x89504e470d0a1a0aL
                && buffer.getInt(16) == 16
                && buffer.getInt(20) == 16;
    }

    private static byte[] download(
            URI uri,
            int limit
    ) throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(45))
                .header(
                        "User-Agent",
                        "Rareity/0.2 (+https://github.com/arist-pantel/Rareity)"
                )
                .GET()
                .build();

        HttpResponse<InputStream> response = HTTP.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );

        InputStream body = response.body();

        try (var deadline =
                     java.util.concurrent.Executors.newSingleThreadScheduledExecutor()) {

            var closeTask = deadline.schedule(
                    () -> {
                        try {
                            body.close();
                        } catch (IOException ignored) {
                        }
                    },
                    60,
                    java.util.concurrent.TimeUnit.SECONDS
            );

            try (body) {
                if (response.statusCode() != 200) {
                    throw new IOException(
                            "HTTP " + response.statusCode()
                    );
                }

                byte[] bytes = body.readNBytes(limit + 1);

                if (bytes.length > limit) {
                    throw new IOException(
                            "Download exceeds size limit"
                    );
                }

                return bytes;

            } finally {
                closeTask.cancel(false);
            }
        }
    }

    static String sha1(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-1").digest(bytes)
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IOException(error);
        }
    }

    private static String validHash(String hash) throws IOException {
        if (!hash.matches("[a-fA-F0-9]{40}")) {
            throw new IOException("Invalid pack hash");
        }

        return hash.toLowerCase(Locale.ROOT);
    }

    private static void atomicWrite(
            Path target,
            byte[] bytes
    ) throws IOException {

        Path temporary = Files.createTempFile(
                target.getParent(),
                "rareity-",
                ".tmp"
        );

        try {
            Files.write(temporary, bytes);

            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}