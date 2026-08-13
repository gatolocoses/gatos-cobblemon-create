# Gato's AI Chat

Chat with an AI directly in Minecraft chat. Wired by default to Gato's Open WebUI (`https://chat.gatolocoses.com/api`), which routes through DeepSeek and can search the web (SearXNG).

The AI can call read-only tools on demand to see live game state: player info (position, dimension, biome, health, food, XP, game mode, held item), player inventories, who is online, server time/weather/difficulty, and biomes at coordinates.

## Usage

- `!ai <message>` in chat or `/ai <message>` asks the AI. Replies are sent only to you.
- `/ai key <key>` sets the API key in game and saves it to the server config file (`config/gatos_ai_chat-server.toml` on the server filesystem only — it is never committed to the pack repository). Only operators can set the key. Running `/ai key` without an argument explains where to get one.
- `/ai config` shows the current base URL, model, history size, and feature flags (the key itself is never shown).
- `/ai clear` or `/ai new` clears your conversation history and starts fresh.

The mod ships with no API key. On first use it asks for one; keys are entered by ops with `/ai key <key>` and are only stored on the server, never in the pack or on GitHub.

## Configuration

The server config file `config/gatos_ai_chat-server.toml` is created on first start:

- `apiKey` — Open WebUI API key (can be set with `/ai key` instead).
- `baseUrl` — OpenAI-compatible base URL. Defaults to `https://chat.gatolocoses.com/api`.
- `model` — model name. Defaults to `deepseek-v4-flash`.
- `systemPrompt` — system prompt that describes the AI's capabilities and tools.
- `historySize` — number of previous messages kept as context per player (0 disables memory). Default 20.
- `webSearch` — enables web search through Open WebUI.
- `toolsEnabled` — allows the AI to call read-only tools for live game data.
- `inventoryContext` — if true, always sends the asking player's inventory with every request (off by default; the AI can request it via tools).

Build with Java 21:

```sh
./gradlew build
```

