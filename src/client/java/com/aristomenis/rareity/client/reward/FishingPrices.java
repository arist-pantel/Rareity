package com.aristomenis.rareity.client.reward;

import com.google.gson.*;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FishingPrices {

    public record Quote(
            double sell,
            double buy,
            long timestamp
    ) {
    }

    private static final long MINUTE = 60_000L;

    private static final HttpClient HTTP =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

    private static final ExecutorService WORKER =
            Executors.newSingleThreadExecutor(
                    task -> daemon(task, "rareity-prices")
            );

    private static final ExecutorService PAGES =
            Executors.newFixedThreadPool(
                    8,
                    task -> daemon(task, "rareity-auction-page")
            );

    private static final AtomicBoolean BUSY =
            new AtomicBoolean();

    private static volatile Map<FishingDrop, Quote> quotes =
            Map.of();

    private static volatile String status =
            "Not requested yet";

    private static long nextBazaar;
    private static long nextAuctions;

    private FishingPrices() {
    }

    private static Path cacheFile() {
        return FabricLoader.getInstance()
                .getGameDir()
                .resolve(".rareity-cache/fishing-prices.json");
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    public static void initialize() {
        WORKER.submit(() -> {
            try {
                Path cache = cacheFile();

                if (!Files.isRegularFile(cache)) {
                    return;
                }

                JsonObject root = JsonParser.parseString(
                        Files.readString(cache)
                ).getAsJsonObject();

                Map<FishingDrop, Quote> loaded =
                        new EnumMap<>(FishingDrop.class);

                for (FishingDrop drop : FishingDrop.values()) {
                    if (!root.has(drop.name())) {
                        continue;
                    }

                    JsonObject q = root.getAsJsonObject(drop.name());

                    double sell = q.get("sell").getAsDouble();
                    double buy = q.get("buy").getAsDouble();
                    long stamp = q.get("timestamp").getAsLong();

                    if (Double.isFinite(sell)
                            && Double.isFinite(buy)
                            && sell >= 0
                            && buy >= 0
                            && stamp <= System.currentTimeMillis()) {
                        loaded.put(
                                drop,
                                new Quote(sell, buy, stamp)
                        );
                    }
                }

                quotes = Map.copyOf(loaded);
                status = "Cached prices loaded; refresh on preview";

            } catch (Exception error) {
                log("Could not read price cache", error);
            }
        });
    }

    public static void refreshIfNeeded() {
        if (!BUSY.compareAndSet(false, true)) {
            return;
        }

        WORKER.submit(() -> {
            List<String> messages = new ArrayList<>();

            try {
                long now = System.currentTimeMillis();

                if (now >= nextBazaar) {
                    status = "Loading Bazaar price...";
                    nextBazaar = now + MINUTE;

                    try {
                        Quote quote = parseBazaar(
                                json("skyblock/bazaar")
                        );

                        publish(Map.of(FishingDrop.TOOTH, quote));
                        messages.add("Bazaar ready");

                    } catch (Exception error) {
                        messages.add(
                                "Bazaar unavailable; cached value retained"
                        );

                        log("Bazaar fetch failed", error);
                    }
                }

                if (now >= nextAuctions) {
                    status = "Scanning auction pages for Megalodon...";
                    nextAuctions = now + 2 * MINUTE;

                    try {
                        publish(scanAuctions());

                        nextAuctions = System.currentTimeMillis()
                                + 15 * MINUTE;

                        messages.add("Auction snapshot ready");

                    } catch (Exception error) {
                        messages.add(
                                "Auction snapshot unavailable; "
                                        + "retry after 2 minutes"
                        );

                        log("Auction scan failed", error);
                    }
                }

                if (!messages.isEmpty()) {
                    status = String.join("; ", messages);
                }

            } finally {
                BUSY.set(false);
            }
        });
    }

    static Quote parseBazaar(JsonObject response) {
        JsonObject product = response
                .getAsJsonObject("products")
                .getAsJsonObject("GREAT_WHITE_SHARK_TOOTH");

        double sell = 0;
        double buy = Double.POSITIVE_INFINITY;

        for (JsonElement element :
                product.getAsJsonArray("sell_summary")) {

            JsonObject row = element.getAsJsonObject();

            if (row.get("amount").getAsDouble() > 0) {
                sell = Math.max(
                        sell,
                        row.get("pricePerUnit").getAsDouble()
                );
            }
        }

        for (JsonElement element :
                product.getAsJsonArray("buy_summary")) {

            JsonObject row = element.getAsJsonObject();
            double price = row.get("pricePerUnit").getAsDouble();

            if (row.get("amount").getAsDouble() > 0 && price > 0) {
                buy = Math.min(buy, price);
            }
        }

        if (!Double.isFinite(sell)
                || !Double.isFinite(buy)
                || sell <= 0) {
            throw new IllegalStateException("No Bazaar liquidity");
        }

        return new Quote(
                sell,
                buy,
                response.get("lastUpdated").getAsLong()
        );
    }

    private static Map<FishingDrop, Quote> scanAuctions()
            throws Exception {

        JsonObject first = json(
                "skyblock/auctions?page=0&snapshot="
                        + System.currentTimeMillis() / MINUTE
        );

        int count = first.get("totalPages").getAsInt();

        if (count < 1 || count > 150) {
            throw new IllegalStateException(
                    "Unsupported auction page count"
            );
        }

        long stamp = first.get("lastUpdated").getAsLong();
        long now = System.currentTimeMillis();

        List<CompletableFuture<JsonObject>> futures =
                new ArrayList<>();

        for (int page = 1; page < count; page++) {
            int index = page;

            futures.add(
                    CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    return json(
                                            "skyblock/auctions?page="
                                                    + index
                                                    + "&snapshot="
                                                    + stamp
                                    );
                                } catch (Exception error) {
                                    throw new CompletionException(error);
                                }
                            },
                            PAGES
                    )
            );
        }

        Map<FishingDrop, Double> minimum =
                new EnumMap<>(FishingDrop.class);

        Set<String> seen = new HashSet<>();

        try {
            processPage(
                    first,
                    stamp,
                    count,
                    now,
                    minimum,
                    seen
            );

            for (var future : futures) {
                processPage(
                        future.get(),
                        stamp,
                        count,
                        now,
                        minimum,
                        seen
                );
            }

        } finally {
            for (var future : futures) {
                try {
                    future.get();
                } catch (Exception ignored) {
                }
            }
        }

        Map<FishingDrop, Quote> result =
                new EnumMap<>(FishingDrop.class);

        for (FishingDrop drop : List.of(
                FishingDrop.EPIC_MEGALODON,
                FishingDrop.LEGENDARY_MEGALODON
        )) {
            result.put(
                    drop,
                    new Quote(
                            minimum.getOrDefault(drop, 0.0),
                            0,
                            stamp
                    )
            );
        }

        return result;
    }

    static void processPage(
            JsonObject page,
            long stamp,
            int pages,
            long now,
            Map<FishingDrop, Double> minimum,
            Set<String> seen
    ) {
        if (page.get("lastUpdated").getAsLong() != stamp
                || page.get("totalPages").getAsInt() != pages) {
            throw new IllegalStateException(
                    "Auction snapshot changed; partial results discarded"
            );
        }

        for (JsonElement element : page.getAsJsonArray("auctions")) {
            JsonObject auction = element.getAsJsonObject();

            if (!seen.add(auction.get("uuid").getAsString())
                    || auction.get("end").getAsLong() <= now) {
                continue;
            }

            if (auction.has("claimed")
                    && auction.get("claimed").getAsBoolean()) {
                continue;
            }

            if (auction.has("highest_bid_amount")
                    && auction.get("highest_bid_amount").getAsLong() > 0) {
                continue;
            }

            double price = auction.get("starting_bid").getAsDouble();

            if (!Double.isFinite(price) || price <= 0) {
                continue;
            }

            for (FishingDrop drop : List.of(
                    FishingDrop.EPIC_MEGALODON,
                    FishingDrop.LEGENDARY_MEGALODON
            )) {
                if (AuctionPets.matches(auction, drop)) {
                    minimum.merge(drop, price, Math::min);
                }
            }
        }
    }

    private static JsonObject json(String endpoint)
            throws Exception {

        var request = HttpRequest.newBuilder(
                        URI.create(
                                "https://api.hypixel.net/v2/" + endpoint
                        )
                )
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", "Rareity/0.3")
                .GET()
                .build();

        var response = HTTP.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "HTTP " + response.statusCode()
            );
        }

        if (response.body().length() > 16 * 1024 * 1024) {
            throw new IllegalStateException("Oversized API response");
        }

        var result = JsonParser.parseString(
                response.body()
        ).getAsJsonObject();

        if (!result.has("success")
                || !result.get("success").getAsBoolean()) {
            throw new IllegalStateException("API failure");
        }

        return result;
    }

    private static void publish(Map<FishingDrop, Quote> update) {
        Map<FishingDrop, Quote> merged =
                new EnumMap<>(FishingDrop.class);

        merged.putAll(quotes);
        merged.putAll(update);

        quotes = Map.copyOf(merged);

        try {
            JsonObject root = new JsonObject();

            for (var entry : quotes.entrySet()) {
                JsonObject row = new JsonObject();

                row.addProperty("sell", entry.getValue().sell());
                row.addProperty("buy", entry.getValue().buy());
                row.addProperty(
                        "timestamp",
                        entry.getValue().timestamp()
                );

                root.add(entry.getKey().name(), row);
            }

            Path cache = cacheFile();
            Files.createDirectories(cache.getParent());

            Path temporary = Files.createTempFile(
                    cache.getParent(),
                    "prices-",
                    ".tmp"
            );

            try {
                Files.writeString(temporary, root.toString());

                Files.move(
                        temporary,
                        cache,
                        StandardCopyOption.REPLACE_EXISTING
                );

            } finally {
                Files.deleteIfExists(temporary);
            }

        } catch (Exception error) {
            log("Could not save price cache", error);
        }
    }

    public static String line(FishingDrop drop) {
        Quote q = quotes.get(drop);

        if (q == null) {
            return BUSY.get()
                    ? "Price loading..."
                    : "Price unavailable";
        }

        long staleAfter = (
                drop == FishingDrop.TOOTH ? 5 : 20
        ) * MINUTE;

        String stale = System.currentTimeMillis()
                - q.timestamp() > staleAfter
                ? " [stale]"
                : "";

        if (q.sell() <= 0) {
            return "No matching BIN listings" + stale;
        }

        if (drop == FishingDrop.TOOTH) {
            return "Bazaar sell: "
                    + coins(q.sell())
                    + " | buy: "
                    + coins(q.buy())
                    + " coins"
                    + stale;
        }

        return "Lowest BIN (all levels): "
                + coins(q.sell())
                + " coins"
                + stale;
    }

    private static String coins(double value) {
        if (value >= 1_000_000) {
            return String.format(
                    Locale.ROOT,
                    "%.2fM",
                    value / 1_000_000
            );
        }

        if (value >= 1_000) {
            return String.format(
                    Locale.ROOT,
                    "%.1fK",
                    value / 1_000
            );
        }

        return String.format(Locale.ROOT, "%.0f", value);
    }

    public static String status() {
        return status;
    }

    public static void close() {
        WORKER.shutdownNow();
        PAGES.shutdownNow();
    }

    private static void log(String message, Exception error) {
        LoggerFactory.getLogger("rareity").warn(message, error);
    }
}