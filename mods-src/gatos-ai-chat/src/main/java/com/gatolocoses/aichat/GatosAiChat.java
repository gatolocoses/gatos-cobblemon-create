package com.gatolocoses.aichat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.Deque;
import java.util.HashMap;
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
                .define("systemPrompt", "You are a friendly AI assistant on a Minecraft server. Answer in the language the player uses and keep answers concise.");
        HISTORY_SIZE = builder
                .comment("Number of previous chat messages kept as context per player. 0 disables memory.")
                .defineInRange("historySize", 8, 0, 64);
        WEB_SEARCH = builder
                .comment("Enable web search through Open WebUI for every request.")
                .define("webSearch", true);
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
        if (!SYSTEM_PROMPT.get().isBlank()) {
            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content", SYSTEM_PROMPT.get());
            messages.add(system);
        }
        for (HistoryEntry entry : history) {
            JsonObject message = new JsonObject();
            message.addProperty("role", entry.role());
            message.addProperty("content", entry.content());
            messages.add(message);
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL.get());
        body.add("messages", messages);
        if (WEB_SEARCH.get()) {
            JsonObject features = new JsonObject();
            features.addProperty("web_search", true);
            body.add("features", features);
            JsonObject params = new JsonObject();
            params.addProperty("function_calling", "legacy");
            body.add("params", params);
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL.get().replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid AI chat base URL: {}", BASE_URL.get());
            player.sendSystemMessage(Component.literal("[AI] Invalid base URL in server config.").withStyle(ChatFormatting.RED));
            return;
        }

        player.sendSystemMessage(Component.literal("[AI] Thinking...").withStyle(ChatFormatting.GRAY));

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(95, TimeUnit.SECONDS)
                .thenAccept(response -> server.execute(() -> handleResponse(server, player, history, response)))
                .exceptionally(throwable -> {
                    server.execute(() -> {
                        player.sendSystemMessage(Component.literal("[AI] Request failed: " + friendlyError(throwable)).withStyle(ChatFormatting.RED));
                    });
                    return null;
                });
    }

    private static void handleResponse(MinecraftServer server, ServerPlayer player, Deque<HistoryEntry> history, HttpResponse<String> response) {
        String answer = extractContent(response);
        if (answer == null) {
            String status = response.statusCode() + " " + (response.body() == null ? "" : response.body().substring(0, Math.min(300, response.body().length())));
            player.sendSystemMessage(Component.literal("[AI] API error: " + status).withStyle(ChatFormatting.RED));
            return;
        }

        history.addLast(new HistoryEntry("assistant", answer));
        trimHistory(history);

        for (String line : answer.split("\\n", -1)) {
            player.sendSystemMessage(Component.literal("[AI] ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(line)));
        }
    }

    private static String extractContent(HttpResponse<String> response) {
        try {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            return root.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        } catch (Exception e) {
            LOGGER.error("Failed to parse AI chat response", e);
            return null;
        }
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
