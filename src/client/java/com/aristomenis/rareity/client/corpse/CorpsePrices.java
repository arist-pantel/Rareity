package com.aristomenis.rareity.client.corpse;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.*;
import org.slf4j.LoggerFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CorpsePrices {
    public record Quote(double unitPrice, long timestamp) {}
    private static final long MINUTE = 60_000;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(
            task -> daemon(task, "rareity-corpse-prices"));
    private static final ExecutorService PAGES = Executors.newFixedThreadPool(4,
            task -> daemon(task, "rareity-corpse-auction-page"));
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static volatile Map<CorpseDrop, Quote> quotes = Map.of();
    private static volatile String status = "Prices not requested";
    private static long nextBazaar, nextAuctions;
    private CorpsePrices() {}

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name); thread.setDaemon(true); return thread;
    }
    private static Path cacheFile() {
        return FabricLoader.getInstance().getGameDir().resolve(".rareity-cache/corpse-prices.json");
    }
    public static void initialize() {
        WORKER.submit(() -> {
            try {
                if (Files.isRegularFile(cacheFile())) {
                    JsonObject root = JsonParser.parseString(Files.readString(cacheFile())).getAsJsonObject();
                    Map<CorpseDrop, Quote> loaded = new EnumMap<>(CorpseDrop.class);
                    for (CorpseDrop drop : CorpseDrop.values()) {
                        if (!root.has(drop.name())) continue;
                        JsonObject row = root.getAsJsonObject(drop.name());
                        double value = row.get("price").getAsDouble();
                        long stamp = row.get("timestamp").getAsLong();
                        if (Double.isFinite(value) && value > 0 && stamp > 0
                                && stamp <= System.currentTimeMillis()) loaded.put(drop, new Quote(value, stamp));
                    }
                    quotes = Map.copyOf(loaded);
                }
            } catch (Exception error) { log("Could not load corpse price cache", error); }
        });
        refreshIfNeeded();
    }
    public static Map<CorpseDrop, Quote> snapshot() { return quotes; }
    public static String status() { return status; }
    public static void refreshIfNeeded() {
        if (!BUSY.compareAndSet(false, true)) return;
        WORKER.submit(() -> {
            List<String> results = new ArrayList<>();
            try {
                long now = System.currentTimeMillis();
                if (now >= nextBazaar) {
                    nextBazaar = now + MINUTE;
                    status = "Loading corpse Bazaar prices";
                    try { merge(parseBazaar(json("skyblock/bazaar"))); results.add("Bazaar ready"); }
                    catch (Exception error) { results.add("Bazaar fetch failed; cache retained"); log("Corpse Bazaar fetch failed", error); }
                }
                if (now >= nextAuctions) {
                    nextAuctions = now + 2 * MINUTE;
                    status = "Scanning BIN auctions for corpse rewards";
                    try {
                        Map<CorpseDrop, Quote> update = scanAuctions();
                        Map<CorpseDrop, Quote> copy = new EnumMap<>(CorpseDrop.class);
                        copy.putAll(quotes);
                        for (CorpseDrop drop : CorpseDrop.values()) if (!drop.bazaar()) copy.remove(drop);
                        copy.putAll(update); quotes = Map.copyOf(copy);
                        nextAuctions = System.currentTimeMillis() + 15 * MINUTE;
                        results.add("BIN snapshot ready");
                    } catch (Exception error) { results.add("BIN scan failed; cache retained"); log("Corpse BIN scan failed", error); }
                }
                if (!results.isEmpty()) {
                    status = String.join("; ", results);
                    save();
                }
            } finally { BUSY.set(false); }
        });
    }
    static Map<CorpseDrop, Quote> parseBazaar(JsonObject response) {
        Map<CorpseDrop, Quote> result = new EnumMap<>(CorpseDrop.class);
        JsonObject products = response.getAsJsonObject("products");
        long stamp = response.get("lastUpdated").getAsLong();
        for (CorpseDrop drop : CorpseDrop.values()) {
            if (!drop.bazaar() || !products.has(drop.itemId)) continue;
            double best = 0;
            for (JsonElement element : products.getAsJsonObject(drop.itemId).getAsJsonArray("sell_summary")) {
                JsonObject row = element.getAsJsonObject();
                double price = row.get("pricePerUnit").getAsDouble();
                if (row.get("amount").getAsDouble() > 0 && Double.isFinite(price)) best = Math.max(best, price);
            }
            if (best > 0) result.put(drop, new Quote(best, stamp));
        }
        if (result.isEmpty()) throw new IllegalStateException("No key buy orders available");
        return result;
    }
    private static Map<CorpseDrop, Quote> scanAuctions() throws Exception {
        JsonObject first = json("skyblock/auctions?page=0&snapshot=" + System.currentTimeMillis() / MINUTE);
        int count = first.get("totalPages").getAsInt();
        long stamp = first.get("lastUpdated").getAsLong();
        if (count < 1 || count > 150) throw new IllegalStateException("Invalid auction page count");
        List<CompletableFuture<JsonObject>> futures = new ArrayList<>();
        for (int index = 1; index < count; index++) {
            int page = index;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try { return json("skyblock/auctions?page=" + page + "&snapshot=" + stamp); }
                catch (Exception error) { throw new CompletionException(error); }
            }, PAGES));
        }
        Map<CorpseDrop, Quote> minimum = new EnumMap<>(CorpseDrop.class);
        Set<String> seen = new HashSet<>();
        try {
            processPage(first, 0, count, stamp, minimum, seen);
            for (int i = 0; i < futures.size(); i++) processPage(futures.get(i).get(), i + 1, count, stamp, minimum, seen);
        } finally {
            for (var future : futures) { try { future.get(); } catch (Exception ignored) {} }
        }
        return minimum;
    }
    static void processPage(JsonObject data, int index, int count, long stamp,
                            Map<CorpseDrop, Quote> minimum, Set<String> seen) {
        if (data.get("page").getAsInt() != index || data.get("totalPages").getAsInt() != count
                || data.get("lastUpdated").getAsLong() != stamp) {
            throw new IllegalStateException("Mixed auction snapshots; discarding partial scan");
        }
        for (JsonElement element : data.getAsJsonArray("auctions")) {
            JsonObject auction = element.getAsJsonObject();
            if (!seen.add(auction.get("uuid").getAsString()) || !auction.has("bin")
                    || !auction.get("bin").getAsBoolean()
                    || auction.get("end").getAsLong() <= System.currentTimeMillis()
                    || (auction.has("claimed") && auction.get("claimed").getAsBoolean())
                    || (auction.has("highest_bid_amount") && auction.get("highest_bid_amount").getAsLong() > 0)) continue;
            CorpseDrop drop = null;
            String name = CorpseLootParser.clean(auction.get("item_name").getAsString());
            for (CorpseDrop candidate : CorpseDrop.values()) {
                if (!candidate.bazaar() && candidate.displayName.equals(name)) { drop = candidate; break; }
            }
            if (drop == null) continue;
            double unit = auctionUnitPrice(auction, drop);
            if (unit > 0 && (!minimum.containsKey(drop) || unit < minimum.get(drop).unitPrice())) {
                minimum.put(drop, new Quote(unit, stamp));
            }
        }
    }
    static double auctionUnitPrice(JsonObject auction, CorpseDrop drop) {
        try {
            String encoded = auction.get("item_bytes").getAsString();
            if (encoded.length() > 262144) return -1;
            CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)),
                    NbtAccounter.create(2 * 1024 * 1024));
            CompoundTag item = root.getListOrEmpty("i").getCompoundOrEmpty(0);
            String id = item.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes").getStringOr("id", "");
            if (!drop.itemId.equals(id)) return -1;
            int count = item.getIntOr("Count", 0);
            double total = auction.get("starting_bid").getAsDouble();
            return count > 0 && count <= 64 && Double.isFinite(total) && total > 0 ? total / count : -1;
        } catch (Exception ignored) { return -1; }
    }
    private static JsonObject json(String endpoint) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("https://api.hypixel.net/v2/" + endpoint))
                .timeout(Duration.ofSeconds(25)).header("User-Agent", "Rareity/0.4").GET().build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body().length() > 16 * 1024 * 1024) throw new IllegalStateException("API HTTP " + response.statusCode());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!result.get("success").getAsBoolean()) throw new IllegalStateException("API failure");
        return result;
    }
    private static void merge(Map<CorpseDrop, Quote> update) {
        Map<CorpseDrop, Quote> copy = new EnumMap<>(CorpseDrop.class);
        copy.putAll(quotes); copy.putAll(update); quotes = Map.copyOf(copy);
    }
    private static void save() {
        try {
            JsonObject root = new JsonObject();
            for (var entry : quotes.entrySet()) {
                JsonObject row = new JsonObject();
                row.addProperty("price", entry.getValue().unitPrice());
                row.addProperty("timestamp", entry.getValue().timestamp());
                root.add(entry.getKey().name(), row);
            }
            Path path = cacheFile(); Files.createDirectories(path.getParent());
            Path temporary = Files.createTempFile(path.getParent(), "corpse-prices-", ".tmp");
            try { Files.writeString(temporary, root.toString()); Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            finally { Files.deleteIfExists(temporary); }
        } catch (Exception error) { log("Could not save corpse prices", error); }
    }
    public static String line(CorpseDrop drop) {
        Quote quote = quotes.get(drop);
        String market = drop.bazaar() ? "BZ sell" : "Lbin";
        if (quote == null) return market + (BUSY.get() ? ": loading..." : ": unavailable");
        String price = String.format(Locale.ROOT, "%,.0f", quote.unitPrice()).strip();
        long limit = (drop.bazaar() ? 5 : 20) * MINUTE;
        return market + ": " + price + " coins each"
                + (System.currentTimeMillis() - quote.timestamp() > limit ? " [stale]" : "");
    }
    public static void close() { WORKER.shutdownNow(); PAGES.shutdownNow(); }
    private static void log(String message, Exception error) { LoggerFactory.getLogger("rareity").warn(message, error); }
}