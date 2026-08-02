# Gato's Cobblemon Create

Packwiz source for Minecraft 1.21.1 with NeoForge 21.1.247.

## Client Setup

Import the PrismLauncher ZIP from the project release. Its pre-launch command
automatically synchronizes mods and shared configuration from:

`https://raw.githubusercontent.com/gatolocoses/gatos-cobblemon-create/main/pack.toml`

Personal saves, multiplayer servers, screenshots, keybinds, video settings,
accounts, and server data are not managed by this pack.

## Publishing Updates

Test changes before publishing, then run:

```bash
packwiz update --all
packwiz refresh
git add .
git commit -m "Update modpack"
git push
```

Removing or changing world-content mods can damage existing worlds. Back up
and stop the dedicated server before applying those updates there.

## Server Sync

Server-side synchronization can use:

```bash
java -jar packwiz-installer-bootstrap.jar -g -s server \
  https://raw.githubusercontent.com/gatolocoses/gatos-cobblemon-create/main/pack.toml
```

The world, operators, whitelist, properties, and secrets are intentionally not
stored in this public repository.
