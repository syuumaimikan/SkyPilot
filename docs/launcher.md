# SkyPilot Launcher

A small desktop app (Java 25, Swing + FlatLaf) that prepares SkyPilot for the **official
Minecraft Launcher**. It doesn't start Minecraft and doesn't sign in. You sign in with Microsoft
and press Play in the official launcher, as you always do.

```
apps/launcher/   (a Gradle subproject of apps/mod - shares core/ and the Gradle wrapper)
  src/main/java/dev/skypilot/launcher/
    LauncherMain        window, or --status / --setup on the command line
    Setup               the status checks and "Set up / Repair"
    FabricInstaller     versions/<fabric id>/<fabric id>.json from meta.fabricmc.net
    LauncherProfiles    the "SkyPilot (SkyBlock)" entry in launcher_profiles*.json
    Mods                bundled SkyPilot mod + pinned Fabric API, list / switch on-off / add
    Instance            the SkyPilot game folder: mod config (server URL), link state, logs
    Net                 https downloads from the two allowed hosts, size limit, hashes
    OfficialLauncher    opens the official launcher (fixed commands)
    ui/LauncherFrame    Home, Mods, Settings, Logs, About
```

## What "Set up / Repair" does

1. Checks that the official launcher's profile file exists (`launcher_profiles.json`, or
   `launcher_profiles_microsoft_store.json` for the Microsoft Store / Xbox app). If it doesn't,
   it stops before doing anything and asks you to start the official launcher once.
2. **Fabric Loader:** downloads the version file for Minecraft 26.2 + Fabric Loader 0.19.5
   from `meta.fabricmc.net`, the same way Fabric's own installer does.
   - The file is checked (`id`, `inheritsFrom`, `libraries`, `mainClass`) and written to
     `versions/fabric-loader-0.19.5-26.2/`.
   - The official launcher then downloads and verifies the libraries and Minecraft itself.
3. **SkyPilot mod:** copies the jar bundled inside the launcher to `<game folder>/mods`.
   - Its SHA-256 is recorded at build time and checked before the copy.
   - When a signing keystore is configured, the bundled jar is the signed one.
4. **Fabric API:** downloads exactly the file the mod is built and tested against
   (Modrinth version `ewUK83HI`).
   - The size and SHA-512 are pinned in `gradle.properties`, and a mismatch means nothing is
     installed.
   - If the pinned file is already there, nothing is downloaded.
5. **Profile:** adds or updates the **"SkyPilot (SkyBlock)"** profile with:
   - type `custom`, version `fabric-loader-0.19.5-26.2`;
   - its own game folder (default `.minecraft/skypilot`);
   - memory `-Xmx<N>M` and the SkyPilot icon.
   - Every other profile and setting in the file is kept.
   - The original file is copied once to `launcher_profiles.json.skypilot-backup` before the
     first change.

Older copies of the SkyPilot mod or Fabric API are switched off (renamed to `.jar.disabled`),
never deleted. Running setup again is safe and only repairs what is missing.

## Pages

| Page | Contents |
|---|---|
| Home | Status checklist and the buttons below; the setup log and the steps to play |
| Mods | The game folder's mods: name, version, file. Switch on/off (rename) or add a local Fabric `.jar` you picked (it must contain `fabric.mod.json`). Open the mods folder |
| Settings | Language (auto / English / 日本語), memory for Minecraft (2–32 GB), Minecraft folder, SkyPilot game folder, SkyPilot server URL. The server URL is written to the mod's own config; https only, http allowed only for localhost |
| Logs | `logs/latest.log` (last 400 lines) and crash reports of the game folder |
| About | What the launcher does and never does, pinned versions and hashes, folders |

**Status checklist on Home:**

- Official launcher found
- Fabric version installed
- Profile up to date
- SkyPilot mod (byte-identical)
- Fabric API (pinned SHA-512)
- SkyPilot server reachable (optional)
- Mod linked (optional: only checks that the mod's token file exists, never reads it)

**Buttons on Home:**

- Set up / Repair
- Open Minecraft Launcher
- Open SkyPilot web
- Refresh

The game folder is separate from your normal `.minecraft` game, so its mods, options and worlds
don't mix with your vanilla or other modded profiles.

## Never

- Asks for, reads or stores a Microsoft or Minecraft account, password, refresh token or session
  token. Signing in and starting the game happen in the official launcher only.
- Starts Minecraft itself, changes the game, or runs downloaded code.
  - The only downloads are a JSON file from `meta.fabricmc.net` and the hash-pinned Fabric API
    jar from `cdn.modrinth.com`.
  - Both are https only, with redirects off and a size limit.
  - Any other host is refused before a request is made.
- Downloads other mods. "Add a mod file…" only copies a file you already have.
- Deletes your files.

## Build and run

```bash
cd apps/mod
./gradlew :launcher:build             # tests + build/libs/skypilot-launcher-<version>-all.jar
./gradlew :launcher:run               # open the window from the source tree
./gradlew :launcher:jpackageImage     # Windows/macOS/Linux app with its own Java runtime
```

- `jpackageImage` writes `apps/launcher/build/jpackage/SkyPilot Launcher/`.
  - On Windows, run `SkyPilot Launcher.exe` from that folder.
  - It is about 80 MB, because it includes a trimmed Java 25 runtime: java.base, java.desktop,
    java.net.http, java.sql and jdk.charsets.
- The runnable jar needs Java 25: `java -jar skypilot-launcher-0.1.0-all.jar`.
- Without code signing, Windows SmartScreen may warn on first start ("More info → Run anyway").
- For releases, sign the exe and installer with your code-signing certificate, and publish the
  SHA-256 of the jar next to it.

**Command line** (same checks without a window):

```bash
java -jar skypilot-launcher-0.1.0-all.jar --status
java -jar skypilot-launcher-0.1.0-all.jar --setup
java -jar skypilot-launcher-0.1.0-all.jar --minecraft-dir /tmp/mc-test --instance-dir /tmp/mc-test/sp --setup
```

The launcher's own settings are in `%APPDATA%\SkyPilot\launcher.json`
(`~/Library/Application Support/SkyPilot`, `~/.config/skypilot`).

## Tests

`./gradlew :launcher:test` runs only in temporary folders, never in the real `.minecraft`.

| Area | What is tested |
|---|---|
| Fabric version file | Installed from fixture metadata, and the placeholder jar is removed. Wrong id, wrong `inheritsFrom` and non-JSON are rejected with nothing written |
| Profile file | Other profiles, settings and `version` are kept. The backup is made once and never overwritten. `created` is kept, memory is updated. The Microsoft Store file name is supported |
| Mods | The bundled mod is installed only with the matching SHA-256. Fabric API is installed only with the pinned SHA-512 and isn't downloaded again. Older copies are switched off, not deleted. List, toggle and add (non-Fabric and non-`.jar` files are rejected) |
| Network rules | http, other hosts, look-alike hosts and `file:` are refused |
| Server URL and folder | Server URL rules, mod config written, link state, logs and crash reports, settings limits |
| Full setup | Fails safely without the official launcher. With fixture downloads every check turns green and no account data is written |
| Pages | Every page in English and Japanese is painted headless to `apps/launcher/build/launcher-screens/*.png` |

## Removing it

1. Delete the "SkyPilot (SkyBlock)" profile in the official launcher (or restore
   `launcher_profiles.json.skypilot-backup`).
2. Delete the folders `.minecraft/skypilot` and `.minecraft/versions/fabric-loader-0.19.5-26.2`.
3. Delete `%APPDATA%\SkyPilot`.
