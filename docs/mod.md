# SkyPilot Fabric mod (Minecraft 26.2)

## Layout

```
apps/mod/
  core/          config (JSON, sanitised), language, text helpers, sidebar/tab parser
  network/       async HTTP client (timeouts, TTL cache, circuit breaker), device flow, token store, DTOs
  translation/   channel classifier, translation modes, styled-segment model, placeholder protection, async service
  session/       session tracker, action-bar XP parser
  hud/           HUD rows and layout maths
  platform/mc26_2/  Fabric entrypoint, HUD renderer, commands, screens, chat/tooltip hooks (26.2 API)
```

Only `platform/mc26_2` touches Minecraft classes. The other modules are plain Java 25,
unit-tested without the game, and nested into the mod jar (jar-in-jar).

## Build

- Gradle 9.7.1 wrapper (checksum-pinned), Fabric Loom 1.18.2, Fabric Loader 0.19.5,
  Fabric API 0.161.0+26.2.
- Loom 1.18 needs Gradle to run on Java 25: `gradle/gradle-daemon-jvm.properties` makes the
  wrapper download a JDK 25 automatically (foojay), so any JDK ≥ 17 can start the build.
- `./gradlew build` → `platform/mc26_2/build/libs/skypilot-<version>.jar` + `.sha256`
  (and a jarsigner signature when `SKYPILOT_SIGN_*` are set).
- `./gradlew :platform:mc26_2:runClient` starts a development client;
  `./gradlew :platform:mc26_2:runClientGameTest` runs the in-game test (`src/gametest`).

## Runtime behaviour

- Every network call is asynchronous with a 6 s timeout; results are applied on the game
  thread with `Minecraft.execute`. A circuit breaker pauses requests to a failing server.
- HUD state refreshes every 60 s, or 5 s after the area changes, and only while linked.
- Price tooltips: at most one request per 250 ms; results kept in an LRU of 256 items and refreshed after 5 minutes.
- Translation: dictionary phrases are answered locally; other lines go through a bounded
  worker (4 in flight, further lines skipped), 4 s timeout, LRU cache of 512, circuit breaker.
  Player names, SkyBlock terms, numbers and commands are replaced by `⟦n⟧` placeholders and
  restored; a translation that loses a placeholder is discarded. The original chat line is
  never changed - the translation is added below it with the original prefix components
  (rank, name with its hover/click events) preserved.
- Click to translate (`translation.clickToTranslate`, off by default; `/skypilot translate click on`
  or the settings screen): single-line messages with text worth translating get a ` [訳]` button
  appended after the server's component (`ClientReceiveMessageEvents.MODIFY_GAME`), so the
  message's own hover/click events keep working. The button runs the client command
  `/skypilot tr <id>`; Fabric executes it locally (chat clicks go through
  `ClientPacketListener.sendUnattendedCommand`, which Fabric's command API intercepts), so nothing
  reaches the game server. The clicked line is translated (after the one-time disclosure) and
  replaced in the chat history in place (`ChatComponentAccessor`: `allMessages` +
  `refreshTrimmedMessages`, scroll position kept); `[原文]` switches back. Up to 300 recent
  lines are kept in memory for this and cleared on disconnect.
- Config: `config/skypilot/config.json`; token: `config/skypilot/token.json` (SkyPilot token only).

## Adding a Minecraft version

Create `platform/mcXX_Y` with its own Loom configuration and copy the platform classes,
adapting renamed Minecraft APIs. The shared modules need no changes.
