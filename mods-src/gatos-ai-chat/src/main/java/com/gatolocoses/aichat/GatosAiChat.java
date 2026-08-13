package com.gatolocoses.aichat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Mod(GatosAiChat.MOD_ID)
public final class GatosAiChat {
    public static final String MOD_ID = "gatos_ai_chat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final ModConfigSpec CONFIG_SPEC;
    public static final ModConfigSpec.ConfigValue<String> API_KEY;
    public static final ModConfigSpec.ConfigValue<String> BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> MODEL;
    public static final ModConfigSpec.ConfigValue<String> SYSTEM_PROMPT;
    public static final ModConfigSpec.IntValue HISTORY_SIZE;
    public static final ModConfigSpec.BooleanValue WEB_SEARCH;
    public static final ModConfigSpec.BooleanValue TOOLS_ENABLED;
    public static final ModConfigSpec.BooleanValue INVENTORY_CONTEXT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        API_KEY = builder
                .comment("API key for the OpenAI-compatible API. Can also be set in game with /ai key <key>.")
                .define("apiKey", "");
        BASE_URL = builder
                .comment("Base URL of the OpenAI-compatible API.",
                        "Defaults to Gato's Open WebUI instance (https://chat.gatolocoses.com/api).",
                        "Open WebUI exposes an OpenAI-compatible endpoint at /api.")
                .define("baseUrl", "https://chat.gatolocoses.com/api");
        MODEL = builder
                .comment("Model name used for chat completions. Defaults to the Open WebUI model.",
                        "Examples: deepseek-v4-flash, deepseek-chat, gpt-4o-mini.")
                .define("model", "deepseek-v4-flash");
        SYSTEM_PROMPT = builder
                .comment("System prompt sent with every request.")
                .define("systemPrompt", "You are a friendly AI assistant inside a Minecraft multiplayer server. You chat with players in their language. You can call tools on demand: web_search (query the web for current information), get_player_info (position, dimension, biome, health, food, XP level, game mode, held item), get_player_inventory (items and counts a player has), list_players (who is online and where), get_server_info (in-game time, weather, difficulty, dimensions), and get_biome (biome at a player or at coordinates). Use the tools whenever the answer depends on live data instead of guessing. Keep answers concise.");
        HISTORY_SIZE = builder
                .comment("Number of previous chat messages kept as context per player. 0 disables memory.")
                .defineInRange("historySize", 20, 0, 64);
        WEB_SEARCH = builder
                .comment("Enable web search through Open WebUI for every request.")
                .define("webSearch", true);
        TOOLS_ENABLED = builder
                .comment("Allow the AI to call read-only tools to fetch server and player data on demand.")
                .define("toolsEnabled", true);
        INVENTORY_CONTEXT = builder
                .comment("Always include the asking player's inventory in every request. With toolsEnabled the AI can request it on demand.")
                .define("inventoryContext", false);
        CONFIG_SPEC = builder.build();
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private static final Map<UUID, Deque<HistoryEntry>> HISTORY = new HashMap<>();

    private record HistoryEntry(String role, String content) {
    }

    public GatosAiChat(IEventBus modBus, ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, CONFIG_SPEC);
        NeoForge.EVENT_BUS.addListener(GatosAiChat::registerCommands);
        NeoForge.EVENT_BUS.addListener(GatosAiChat::onChatMessage);
        NeoForge.EVENT_BUS.addListener(GatosAiChat::onPlayerLeave);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ai")
                .then(Commands.argument("prompt", StringArgumentType.greedyString())
                        .executes(context -> {
                            ask(context.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(context, "prompt"));
                            return 1;
                        }))
                .then(Commands.literal("key")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Create an API key in Open WebUI (Account > API Keys), then run /ai key <key>.").withStyle(ChatFormatting.YELLOW), false);
                            return 1;
                        })
                        .then(Commands.argument("key", StringArgumentType.greedyString())
                                .executes(context -> {
                                    if (!context.getSource().hasPermission(2)) {
                                        context.getSource().sendFailure(Component.literal("Only server operators can set the API key."));
                                        return 0;
                                    }
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    API_KEY.set(StringArgumentType.getString(context, "key"));
                                    try {
                                        CONFIG_SPEC.save();
                                        player.sendSystemMessage(Component.literal("API key saved.").withStyle(ChatFormatting.GREEN));
                                    } catch (Exception e) {
                                        player.sendSystemMessage(Component.literal("API key is set for this session but could not be saved to the config file.").withStyle(ChatFormatting.YELLOW));
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("config")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String keyState = API_KEY.get().isBlank() ? "not set" : "set";
                            player.sendSystemMessage(Component.literal("[AI] baseUrl=" + BASE_URL.get()
                                    + " | model=" + MODEL.get()
                                    + " | historySize=" + HISTORY_SIZE.get()
                                    + " | webSearch=" + WEB_SEARCH.get()
                                    + " | tools=" + TOOLS_ENABLED.get()
                                    + " | inventoryContext=" + INVENTORY_CONTEXT.get()
                                    + " | apiKey=" + keyState).withStyle(ChatFormatting.GRAY));
                            return 1;
                        }))
                .then(Commands.literal("clear")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            HISTORY.remove(player.getUUID());
                            player.sendSystemMessage(Component.literal("[AI] Chat history cleared.").withStyle(ChatFormatting.GRAY));
                            return 1;
                        }))
                .then(Commands.literal("new")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            HISTORY.remove(player.getUUID());
                            player.sendSystemMessage(Component.literal("[AI] Started a new conversation.").withStyle(ChatFormatting.GRAY));
                            return 1;
                        })));
    }

    private static void onChatMessage(ServerChatEvent event) {
        String raw = event.getRawText().trim();
        if (!raw.toLowerCase(Locale.ROOT).startsWith("!ai")) {
            return;
        }

        String prompt = raw.substring(3).trim();
        if (prompt.isEmpty()) {
            event.getPlayer().sendSystemMessage(Component.literal("Usage: !ai <message> or /ai <message>").withStyle(ChatFormatting.GRAY));
            return;
        }

        ask(event.getPlayer(), prompt);
    }

    private static final int MAX_TOOL_ITERATIONS = 4;

    private static void ask(ServerPlayer player, String prompt) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        String apiKey = API_KEY.get();
        if (apiKey.isBlank()) {
            player.sendSystemMessage(Component.literal("[AI] No API key set. Create one in Open WebUI (Account > API Keys) and run /ai key <key>.").withStyle(ChatFormatting.YELLOW));
            return;
        }

        Deque<HistoryEntry> history = HISTORY.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        history.addLast(new HistoryEntry("user", prompt));
        trimHistory(history);

        JsonArray messages = new JsonArray();
        String systemContent = SYSTEM_PROMPT.get();
        if (INVENTORY_CONTEXT.get()) {
            systemContent = systemContent + "\n\n" + inventoryContext(player);
        }
        if (!systemContent.isBlank()) {
            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content", systemContent);
            messages.add(system);
        }
        for (HistoryEntry entry : history) {
            JsonObject message = new JsonObject();
            message.addProperty("role", entry.role());
            message.addProperty("content", entry.content());
            messages.add(message);
        }

        player.sendSystemMessage(Component.literal("[AI] Thinking...").withStyle(ChatFormatting.GRAY));
        sendCompletion(server, player, messages, 0);
    }

    private static void sendCompletion(MinecraftServer server, ServerPlayer player, JsonArray messages, int iteration) {
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL.get());
        body.add("messages", messages);
        if (TOOLS_ENABLED.get()) {
            body.add("tools", toolSpecs());
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL.get().replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY.get())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid AI chat base URL: {}", BASE_URL.get());
            player.sendSystemMessage(Component.literal("[AI] Invalid base URL in server config.").withStyle(ChatFormatting.RED));
            return;
        }

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(95, TimeUnit.SECONDS)
                .thenAccept(response -> server.execute(() -> handleCompletion(server, player, messages, iteration, response)))
                .exceptionally(throwable -> {
                    server.execute(() ->
                            player.sendSystemMessage(Component.literal("[AI] Request failed: " + friendlyError(throwable)).withStyle(ChatFormatting.RED)));
                    return null;
                });
    }

    private static void handleCompletion(MinecraftServer server, ServerPlayer player, JsonArray messages, int iteration, HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String status = response.statusCode() + " " + (response.body() == null ? "" : response.body().substring(0, Math.min(300, response.body().length())));
            player.sendSystemMessage(Component.literal("[AI] API error: " + status).withStyle(ChatFormatting.RED));
            return;
        }

        JsonObject message;
        try {
            message = JsonParser.parseString(response.body()).getAsJsonObject()
                    .getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message");
        } catch (Exception e) {
            LOGGER.error("Failed to parse AI chat response", e);
            player.sendSystemMessage(Component.literal("[AI] Failed to read the AI response.").withStyle(ChatFormatting.RED));
            return;
        }

        JsonArray toolCalls = message.getAsJsonArray("tool_calls");
        if (toolCalls != null && !toolCalls.isEmpty() && iteration < MAX_TOOL_ITERATIONS) {
            messages.add(message.deepCopy());
            List<JsonObject> results = new ArrayList<>();
            List<CompletableFuture<Void>> asyncTasks = new ArrayList<>();
            for (JsonElement element : toolCalls) {
                JsonObject call = element.getAsJsonObject();
                String callId = call.get("id") == null || call.get("id").isJsonNull() ? "" : call.get("id").getAsString();
                JsonObject function = call.getAsJsonObject("function");
                String name = function.get("name").getAsString();
                JsonObject args = new JsonObject();
                try {
                    JsonElement parsed = JsonParser.parseString(function.get("arguments").getAsString());
                    if (parsed.isJsonObject()) {
                        args = parsed.getAsJsonObject();
                    }
                } catch (Exception ignored) {
                }

                JsonObject result = new JsonObject();
                result.addProperty("role", "tool");
                result.addProperty("tool_call_id", callId);
                if ("web_search".equals(name)) {
                    String query = args.has("query") && !args.get("query").isJsonNull() ? args.get("query").getAsString() : "";
                    results.add(result);
                    asyncTasks.add(searchWeb(query).thenAccept(content -> result.addProperty("content", content)));
                } else {
                    result.addProperty("content", executeTool(server, player, name, args));
                    results.add(result);
                }
            }
            if (asyncTasks.isEmpty()) {
                results.forEach(messages::add);
                sendCompletion(server, player, messages, iteration + 1);
            } else {
                CompletableFuture.allOf(asyncTasks.toArray(new CompletableFuture[0]))
                        .thenRun(() -> server.execute(() -> {
                            results.forEach(messages::add);
                            sendCompletion(server, player, messages, iteration + 1);
                        }));
            }
            return;
        }

        String answer = message.get("content") == null || message.get("content").isJsonNull() ? null : message.get("content").getAsString();
        if (answer == null || answer.isBlank()) {
            player.sendSystemMessage(Component.literal("[AI] The AI did not produce an answer.").withStyle(ChatFormatting.RED));
            return;
        }

        Deque<HistoryEntry> history = HISTORY.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        history.addLast(new HistoryEntry("assistant", answer));
        trimHistory(history);

        for (String line : answer.split("\\n", -1)) {
            player.sendSystemMessage(Component.literal("[AI] ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(line)));
        }
    }

    private static JsonArray toolSpecs() {
        JsonArray tools = new JsonArray();
        if (WEB_SEARCH.get()) {
            tools.add(tool("web_search",
                    "Search the web for current information. Returns titles, links, and snippets of the top results.",
                    "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"description\":\"Search query\"}},\"required\":[\"query\"]}"));
        }
        tools.add(tool("get_player_info",
                "Information about a Minecraft player: position, dimension, biome, health, food, XP level, game mode, and held item. Omit \"player\" for the player who asked.",
                "{\"type\":\"object\",\"properties\":{\"player\":{\"type\":\"string\",\"description\":\"Player name. Omit for the asking player.\"}}}"));
        tools.add(tool("get_player_inventory",
                "The inventory contents (item: count) of a Minecraft player. Omit \"player\" for the asking player.",
                "{\"type\":\"object\",\"properties\":{\"player\":{\"type\":\"string\",\"description\":\"Player name. Omit for the asking player.\"}}}"));
        tools.add(tool("list_players",
                "The players currently online on the Minecraft server with their dimension, position, and health.",
                "{\"type\":\"object\",\"properties\":{}}"));
        tools.add(tool("get_server_info",
                "Current Minecraft server state: in-game day and time, weather, difficulty, and the list of dimensions.",
                "{\"type\":\"object\",\"properties\":{}}"));
        tools.add(tool("get_biome",
                "The biome at a position. Provide a player name, or provide x, z, and optional y and dimension.",
                "{\"type\":\"object\",\"properties\":{\"player\":{\"type\":\"string\"},\"x\":{\"type\":\"number\"},\"y\":{\"type\":\"number\"},\"z\":{\"type\":\"number\"},\"dimension\":{\"type\":\"string\",\"description\":\"Dimension id, e.g. minecraft:overworld. Defaults to minecraft:overworld.\"}}}"));
        return tools;
    }

    private static JsonObject tool(String name, String description, String parameters) {
        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);
        function.add("parameters", JsonParser.parseString(parameters));
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.add("function", function);
        return tool;
    }

    private static CompletableFuture<String> searchWeb(String query) {
        JsonObject body = new JsonObject();
        JsonArray queries = new JsonArray();
        queries.add(query);
        body.add("queries", queries);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL.get().replaceAll("/+$", "") + "/api/v1/retrieval/process/web/search"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY.get())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture("{\"error\":\"Invalid base URL\"}");
        }

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return "{\"error\":\"Web search failed with HTTP " + response.statusCode() + "\"}";
                    }
                    try {
                        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                        JsonArray items = root.getAsJsonArray("items");
                        JsonArray results = new JsonArray();
                        int max = items == null ? 0 : Math.min(items.size(), 5);
                        for (int i = 0; i < max; i++) {
                            JsonObject item = items.get(i).getAsJsonObject();
                            JsonObject entry = new JsonObject();
                            entry.addProperty("title", item.has("title") ? item.get("title").getAsString() : "");
                            entry.addProperty("link", item.has("link") ? item.get("link").getAsString() : "");
                            entry.addProperty("snippet", item.has("snippet") ? item.get("snippet").getAsString() : "");
                            results.add(entry);
                        }
                        return results.toString();
                    } catch (Exception e) {
                        return "{\"error\":\"Could not parse search results\"}";
                    }
                })
                .exceptionally(throwable -> "{\"error\":\"Web search failed\"}");
    }

    private static String executeTool(MinecraftServer server, ServerPlayer asker, String name, JsonObject args) {
        try {
            return switch (name) {
                case "get_player_info" -> playerInfo(resolvePlayer(server, asker, args));
                case "get_player_inventory" -> playerInventory(resolvePlayer(server, asker, args));
                case "list_players" -> listPlayers(server);
                case "get_server_info" -> serverInfo(server);
                case "get_biome" -> biomeInfo(server, asker, args);
                default -> "{\"error\":\"Unknown tool: " + name + "\"}";
            };
        } catch (Exception e) {
            return "{\"error\":\"Tool failed\"}";
        }
    }

    private static ServerPlayer resolvePlayer(MinecraftServer server, ServerPlayer asker, JsonObject args) {
        if (args.has("player") && !args.get("player").getAsString().isBlank()) {
            return server.getPlayerList().getPlayerByName(args.get("player").getAsString());
        }
        return asker;
    }

    private static String playerInfo(ServerPlayer player) {
        if (player == null) {
            return "{\"error\":\"Player not found\"}";
        }
        JsonObject info = new JsonObject();
        info.addProperty("name", player.getGameProfile().getName());
        info.addProperty("dimension", player.serverLevel().dimension().location().toString());
        info.addProperty("biome", biomeAt(player.serverLevel(), player.blockPosition()));
        info.addProperty("x", Math.round(player.getX()));
        info.addProperty("y", Math.round(player.getY()));
        info.addProperty("z", Math.round(player.getZ()));
        info.addProperty("health", Math.round(player.getHealth()));
        info.addProperty("maxHealth", Math.round(player.getMaxHealth()));
        info.addProperty("food", player.getFoodData().getFoodLevel());
        info.addProperty("xpLevel", player.experienceLevel);
        info.addProperty("gameMode", player.gameMode.getGameModeForPlayer().getName());
        info.addProperty("heldItem", player.getMainHandItem().isEmpty() ? "nothing" : player.getMainHandItem().getHoverName().getString());
        return info.toString();
    }

    private static String playerInventory(ServerPlayer player) {
        if (player == null) {
            return "{\"error\":\"Player not found\"}";
        }
        JsonObject result = new JsonObject();
        result.addProperty("player", player.getGameProfile().getName());
        JsonArray items = new JsonArray();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                items.add(stack.getHoverName().getString() + ": " + stack.getCount());
            }
        }
        result.add("items", items);
        return result.toString();
    }

    private static String listPlayers(MinecraftServer server) {
        JsonObject result = new JsonObject();
        JsonArray players = new JsonArray();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            JsonObject info = new JsonObject();
            info.addProperty("name", p.getGameProfile().getName());
            info.addProperty("dimension", p.serverLevel().dimension().location().toString());
            info.addProperty("x", Math.round(p.getX()));
            info.addProperty("y", Math.round(p.getY()));
            info.addProperty("z", Math.round(p.getZ()));
            info.addProperty("health", Math.round(p.getHealth()));
            players.add(info);
        }
        result.add("players", players);
        return result.toString();
    }

    private static String serverInfo(MinecraftServer server) {
        JsonObject info = new JsonObject();
        ServerLevel overworld = server.overworld();
        info.addProperty("day", overworld.getDayTime() / 24000L);
        info.addProperty("timeOfDay", overworld.getDayTime() % 24000L);
        info.addProperty("raining", overworld.isRaining());
        info.addProperty("thundering", overworld.isThundering());
        info.addProperty("difficulty", server.getWorldData().getDifficulty().getKey());
        info.addProperty("onlinePlayers", server.getPlayerList().getPlayerCount());
        info.addProperty("serverTickCount", server.getTickCount());
        JsonArray dimensions = new JsonArray();
        for (ServerLevel level : server.getAllLevels()) {
            dimensions.add(level.dimension().location().toString());
        }
        info.add("dimensions", dimensions);
        return info.toString();
    }

    private static String biomeInfo(MinecraftServer server, ServerPlayer asker, JsonObject args) {
        ServerLevel level = asker.serverLevel();
        double x = asker.getX();
        double y = asker.getY();
        double z = asker.getZ();
        if (args.has("player") && !args.get("player").getAsString().isBlank()) {
            ServerPlayer target = server.getPlayerList().getPlayerByName(args.get("player").getAsString());
            if (target == null) {
                return "{\"error\":\"Player not found\"}";
            }
            level = target.serverLevel();
            x = target.getX();
            y = target.getY();
            z = target.getZ();
        } else if (args.has("x") && args.has("z")) {
            x = args.get("x").getAsDouble();
            z = args.get("z").getAsDouble();
            y = args.has("y") ? args.get("y").getAsDouble() : 64;
            if (args.has("dimension")) {
                ResourceLocation dimensionId = ResourceLocation.tryParse(args.get("dimension").getAsString());
                ServerLevel targetLevel = dimensionId == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
                if (targetLevel != null) {
                    level = targetLevel;
                }
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("dimension", level.dimension().location().toString());
        result.addProperty("x", Math.round(x));
        result.addProperty("y", Math.round(y));
        result.addProperty("z", Math.round(z));
        result.addProperty("biome", biomeAt(level, BlockPos.containing(x, y, z)));
        return result.toString();
    }

    private static String biomeAt(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unknown");
    }

    private static String inventoryContext(ServerPlayer player) {
        StringBuilder context = new StringBuilder("The player \"")
                .append(player.getGameProfile().getName())
                .append("\" has the following items in their inventory (item: count):");
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                context.append("\n- ").append(stack.getHoverName().getString()).append(": ").append(stack.getCount());
            }
        }
        return context.toString();
    }

    private static String friendlyError(Throwable throwable) {
        if (throwable instanceof java.util.concurrent.TimeoutException) {
            return "timed out";
        }
        return throwable.getClass().getSimpleName();
    }

    private static void trimHistory(Deque<HistoryEntry> history) {
        int limit = HISTORY_SIZE.get();
        if (limit <= 0) {
            history.clear();
            return;
        }
        while (history.size() > limit) {
            history.removeFirst();
        }
    }

    private static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        HISTORY.remove(event.getEntity().getUUID());
    }
}
