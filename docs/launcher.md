# SkyPilot Launcher

A desktop app (Java 25, Swing + FlatLaf) that installs SkyPilot for Minecraft 26.2 + Fabric and
starts the game. There are two ways to play:

| Mode | Sign-in | Who starts Minecraft |
|---|---|---|
| **This launcher** (default when signed in) | Microsoft device-code sign-in in your browser | SkyPilot Launcher, with Mojang's own game files and Java runtime |
| **Official Minecraft Launcher** | In the official launcher | The official launcher, using the "SkyPilot (SkyBlock)" profile this launcher adds |

```
apps/launcher/   (a Gradle subproject of apps/mod - shares core/, network/ and the Gradle wrapper)
  src/main/java/dev/skypilot/launcher/
    LauncherMain       window, or --status / --setup on the command line
    Setup              status checks, "Repair", run() for the official launcher, runDirect()
    MicrosoftAuth      device code → Microsoft token → Xbox Live → XSTS → Minecraft session
    Account            the signed-in account (session in memory, renewal)
    AccountStore       account.json: name, UUID, DPAPI-encrypted refresh token
    Dpapi              Windows Data Protection through Java's foreign function API
    GameFiles          version files, libraries, client jar, assets, Java runtime (SHA-1 checked)
    GameLauncher       the start command from the version files' argument lists
    Updater            signed launcher updates from your SkyPilot server
    FabricInstaller    versions/<fabric id>/<fabric id>.json from meta.fabricmc.net
    LauncherProfiles   the "SkyPilot (SkyBlock)" entry in launcher_profiles*.json
    Mods, ModScan, ModCheck, Presets, ModrinthUpdates     mod list, compatibility, presets
    CrashAnalyzer, GameWatcher, Backups, Importer, ServerList, GameOptions, Diagnostics
    Net                https only, per-purpose host lists, size limits, hashes
  src/main/java/dev/skypilot/launcher/ui/
    LauncherFrame (sidebar + account card), HomePage, ModsPage, FeaturesPage, ScreenshotsPage,
    LogsPage, ToolsPage, SettingsPage, AboutPage, SignInDialog, UpdateUi
```

## Microsoft sign-in and direct start

SkyPilot includes its public Microsoft application ID (b6c08d26-b301-4de0-bec8-6b5d69a42325), approved by Mojang on 2026-10-07. No app registration or client ID entry is needed for normal use. Existing empty settings automatically use this ID; valid custom IDs are preserved. Microsoft sign-in uses the consumers endpoint for personal accounts, so the directory tenant ID is not used.

**Signing in** (sidebar → "Sign in with Microsoft"):

1. The launcher asks `login.microsoftonline.com/consumers` for a device code
   (scope `XboxLive.signin offline_access`).
2. It shows the code, copies it and opens `microsoft.com/link`. You type the code and sign in
   **on Microsoft's page**; the launcher never sees your password.
3. Xbox Live (`user.auth.xboxlive.com`) → XSTS (`xsts.auth.xboxlive.com`) →
   `login_with_xbox` → `minecraft/profile` turn the Microsoft token into a game session.
   Endpoint shapes: minecraft.wiki "Microsoft authentication" (checked 2026-10).

Problems are explained instead of shown as codes: client ID unknown or without personal accounts,
"Allow public client flows" off, not approved by Mojang (403), no Xbox profile
(XErr 2148916233), Xbox not available in the region (…235), adult verification (…236/237),
child account without a family (…238), account without Minecraft (profile 404), expired code.

**What is kept, and where:**

| Data | Where | How |
|---|---|---|
| Player name and UUID | `account.json` in the launcher folder | plain (shown in the sidebar) |
| Microsoft refresh token | same file | encrypted with Windows DPAPI for the current Windows user (+ app entropy). Copying the file to another user or PC makes it useless. Not saved on other systems: you sign in again next time |
| Minecraft access token | memory only | renewed from the refresh token when it is older than 23 h 50 min |

Nothing of this is sent to the SkyPilot server, logged or shown. The start command in
`logs/launcher.log` has the token replaced by `<hidden>`. "Sign out" deletes `account.json`.

**PLAY in direct mode:**

1. `Setup.runDirect`: Fabric's version file, the bundled SkyPilot mod (SHA-256) and the pinned
   Fabric API (SHA-512), only if missing. The official launcher's profile is updated too when
   that launcher is installed.
2. A valid session (renewed silently, or "Sign in again").
3. `GameFiles.prepare`, from `Net.GAME_HOSTS` only:
   - Mojang's version list → the pinned version's file (SHA-1 from the list).
   - Libraries for this OS (Mojang's rules) plus Fabric's; Fabric's version wins when both list
     the same artifact (ASM).
   - The client jar, the asset index and its objects (`resources.download.minecraft.net`).
   - Mojang's Java runtime named in the version file (`java-runtime-epsilon`, Java 25) into
     `<launcher folder>/runtime`.
   - Each download is checked against its SHA-1 and size before it is moved into place; files
     already present with the right size are reused (shared with the official launcher's
     `versions`, `libraries` and `assets`). Settings → "Check and repair game files" checks
     every SHA-1. HTTP 429 stops the download instead of retrying.
4. `GameLauncher.command`: the vanilla and Fabric argument lists with their rules and
   placeholders, memory, allowed extra Java flags, window size, and Minecraft's Quick Play
   (`--quickPlayMultiplayer mc.hypixel.net`) when "Join Hypixel right away" is ticked.
5. The game's console output goes to `<launcher folder>/logs/game-console.log`; a non-zero exit
   shows a toast that opens the crash explanation.

## Install and update

The Mods page now offers optional, SHA-512-verified performance, Iris and Skyblocker packs
for Fabric 26.2. See [client expansion](client-expansion.md) for the pinned versions and new in-game features.

```bash
cd apps/mod
./gradlew :launcher:jpackageImage       # portable app: apps/launcher/build/jpackage/SkyPilot Launcher/
./gradlew :launcher:jpackageInstaller   # installer: apps/launcher/build/installer/SkyPilot-Launcher-<version>.exe
```

- The installer needs WiX Toolset 5 on the PATH:
  `dotnet tool install --global wix --version 5.0.2`, then
  `wix extension add -g WixToolset.UI.wixext/5.0.2` and
  `wix extension add -g WixToolset.Util.wixext/5.0.2`.
  If `wix` is not found, use `"$USERPROFILE/.dotnet/tools/wix.exe"` in Git Bash
  or `& "$env:USERPROFILE/.dotnet/tools/wix.exe"` in PowerShell.
  The Gradle installer task also searches the user's `.dotnet/tools` directory.
- Double-click `build/jpackage/SkyPilot Launcher/SkyPilot Launcher.exe` to start
  the portable app. Keep the entire `SkyPilot Launcher` folder together: its `app`
  and `runtime` folders are required, and Java does not need to be installed separately.
  Close the app before rebuilding; read-only output attributes are cleared automatically.
- Double-click `build/installer/SkyPilot-Launcher-<version>.exe` to install it.
- The installer is per user (no administrator rights), adds Start menu and desktop shortcuts and
  appears in "Installed apps" for removal. A fixed upgrade UUID lets a newer installer replace
  the installed version.
- Without code signing, Windows SmartScreen may warn on first start ("More info → Run anyway").

**Updates** come from your SkyPilot server and are installed only after two checks:

1. `GET /api/launcher/latest` returns `latest.json` and its **Ed25519 signature**; the signature
   must match the public key built into the launcher (`src/main/resources/update-key.pub`).
2. The installer from `GET /api/launcher/files/<name>` must have the SHA-256 and size given in
   the signed `latest.json`.

Only then, and only after you click "Install", the installer runs; the launcher closes and opens
again when it has finished. Settings → Updates: "Check now", and "Check when the launcher
starts" (on by default).

**Publishing a release:**

```bash
./gradlew :launcher:generateUpdateKey      # once: private key → ~/.skypilot/launcher-update-key.txt
export SKYPILOT_UPDATE_KEY="$(cat ~/.skypilot/launcher-update-key.txt)"
./gradlew :launcher:launcherRelease        # build/release: installer, latest.json, latest.json.sig
```

Copy `build/release/*` to the server's `LAUNCHER_RELEASES_DIR` (default
`data/launcher-releases`). Keep the private key out of the repository (it never is in it);
whoever has it can sign launcher updates.

## Pages

Open the web app from Home → Quick actions → **Open SkyPilot Web**. In Minecraft,
press **J** and select **Web**, or run **`/skypilot web`**. Minecraft displays its link
confirmation before opening your browser. Both use the server URL configured in the
launcher Settings / the mod Settings (default: `http://localhost:3000`). The web server
must be running at that address; opening the browser does not start a local server.

| Page | Contents |
|---|---|
| Home | Hero with PLAY (install / sign in / start / running), "Join Hypixel right away", Hypixel server status (SkyBlock / server / login / API, maintenance with local times and countdowns, incidents; refreshed every 2 min), mayor and perks, upcoming events, SkyBlock news, installation checks with Repair, quick actions (add Hypixel, import settings, FPS boost, game folder) |
| Mods | Search, filters, presets, troubleshoot (safe mode), compatibility problems (missing / wrong dependencies, conflicts, wrong Java), details, Modrinth update links, drag and drop |
| Features | The mod's switchable features by category, recommended set, consent features |
| Screenshots | Grid and viewer |
| Logs | latest.log with search and filters, crash reports with "What happened" explanations, copy for support (anonymised) |
| Tools | Import from your main game, server list, backups / restore, diagnostics, game language, disk use |
| Settings | Appearance; **Account and launch** (account, launch mode, client ID, check and repair game files); game (memory, window size, extra Java flags); profile picture; folders; SkyPilot server; **Updates** |
| About | Versions, what it does and never does, third-party licences |

## Never

- Asks for your password, or for a token. Sign-in happens on Microsoft's page; tokens come from
  Microsoft / Xbox / Mojang directly and stay on this PC (see the table above).
- Sends your sign-in anywhere but Microsoft, Xbox Live and Minecraft services.
- Runs anything it downloaded without a check: game files against Mojang's / Fabric's SHA-1,
  mods against pinned hashes, launcher updates against SkyPilot's signature.
- Installs other mods without selecting an expansion pack (update checks still only link to Modrinth), or deletes a mod without explicit confirmation
  (mods are switched off by renaming; settings are backed up before changes).

## Tests

`./gradlew :launcher:test` runs in temporary folders only, never in the real `.minecraft`.

| Area | What is tested |
|---|---|
| Sign-in | Device code → pending → token → Xbox → XSTS → Minecraft → profile with scripted answers; request shapes; every explained error; tokens never in `toString` |
| Saved account | DPAPI round trip (Windows); the refresh token is not in the file in clear text; a damaged file is "signed out"; sign-out deletes it |
| Game files | OS / feature rules, Maven paths, library merging (Fabric wins), runtime platform |
| Start command | Order, placeholders, Quick Play and window size only when set, blocked Java flags dropped, token hidden in the log line |
| Updates | Signature checked, changed manifest / other key / bad file name refused, version order |
| Earlier areas | Fabric version file, profile file, mods and compatibility, presets, Modrinth, NBT server list, import, backups, crash explanations, settings limits |
| Pages | Every page in English and Japanese, light theme, the sign-in dialog and the signed-in home page painted to `apps/launcher/build/launcher-screens/*.png` |

`LiveLaunchTest` downloads everything from the real servers (about 700 MB) and starts the game
with an offline session until the title screen; it runs only with
`-Dskypilot.liveLaunch=<empty folder>`.

## Removing it

To delete an individual mod, select it on the Mods page and click **Delete…** in its details.
The confirmation names the exact `.jar` or `.jar.disabled` file. Deletion is permanent;
switching off continues to keep the file. Close Minecraft first. Repair / Play reinstalls the
bundled SkyPilot and Fabric API when they are missing.

1. Windows: "Installed apps" → SkyPilot Launcher → Uninstall (or delete the portable folder).
2. Delete the "SkyPilot (SkyBlock)" profile in the official launcher (or restore
   `launcher_profiles.json.skypilot-backup`).
3. Delete `.minecraft/skypilot` and `.minecraft/versions/fabric-loader-0.19.5-26.2`.
4. Delete `%APPDATA%\SkyPilot` (settings, saved sign-in, Java runtime, logs).
