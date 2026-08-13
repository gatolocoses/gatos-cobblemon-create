# Gato's AI Chat

Chat with an AI directly in Minecraft chat. Wired by default to Gato's Open WebUI (`https://chat.gatolocoses.com/api`), which routes through DeepSeek and can search the web (SearXNG).

## Usage

- `!ai <message>` in chat or `/ai <message>` asks the AI. Replies are sent only to you.
- `/ai key <key>` sets the API key in game and saves it to the server config file (`config/gatos_ai_chat-server.toml` on the server filesystem only — it is never committed to the pack repository). Running `/ai key` without an argument explains where to get one.
- `/ai config` shows the current base URL, model, history size, and whether a key is set (the key itself is never shown).
- `/ai clear` clears your conversation history.

The mod ships with no API key. On first use it asks for one; keys are entered by players with `/ai key <key>` and are only stored on the server, never in the pack or on GitHub.

## Configuration

The server config file `config/gatos_ai_chat-server.toml` is created on first start:

- `apiKey` — Open WebUI API key (can be set with `/ai key` instead).
- `baseUrl` — OpenAI-compatible base URL. Defaults to `https://chat.gatolocoses.com/api`.
- `model` — model name. Defaults to `deepseek-v4-flash`.
- `systemPrompt` — system prompt sent with every request.
- `historySize` — number of previous messages kept as context per player (0 disables memory).
- `webSearch` — enables web search through Open WebUI on every request.

Build with Java 21:

```sh
./gradlew build
```

