# DSH Pet Whale — 鲸鱼娘桌宠（JetBrains IDEA 2023.1+）

> Port of [DSH Pet](https://github.com/zhu1090093659/dsh-web-ui/tree/main/packages/dsh-pet) whale-girl theme
> to JetBrains IDEA. A floating desktop companion that watches your editor and VCS activity and switches
> animations accordingly.

![whale](src/main/resources/images/whale/previews/idle.gif) ·
![running](src/main/resources/images/whale/previews/running.gif) ·
![waving](src/main/resources/images/whale/previews/waving.gif) ·
![jumping](src/main/resources/images/whale/previews/jumping.gif) ·
![failed](src/main/resources/images/whale/previews/failed.gif) ·
![review](src/main/resources/images/whale/previews/review.gif)

## Features

| Feature | Description |
|---|---|
| 9-state animation | idle / running-right / running-left / waving / jumping / failed / waiting / running / review, driven by per-track durations from `pet.json` |
| 2 built-in themes | **Original** whale-girl + **Refined** AI-assisted variant, switchable from the hover panel |
| Editor linkage | Each editor that gains focus flips the pet to `thinking/running`; releasing settles after the idle timer |
| VCS linkage | Any create/delete/property change under `.git/` nudges the pet into a brief `review` animation (commit/pull hint) |
| Drag-to-move | Hold-drag the pet anywhere on the screen; position resets next session only if you hide & summon |
| Hide / Summon | Right-click or hover panel hides the pet; a small "召唤鲸鱼娘" button stays at the bottom-right |
| Always-on-top | A 1-pixel-bordered transparent `JFrame` floats over every editor and tool window |

## Animation contract

The two built-in atlases use the same 8-column × 9-row contract as DSH Pet:

| Row | Animation    | Default frames |
|---|---|---|
| 0 | idle         | 6 |
| 1 | running-right| 8 |
| 2 | running-left | 8 |
| 3 | waving       | 4 |
| 4 | jumping      | 5 |
| 5 | failed       | 8 |
| 6 | waiting      | 6 |
| 7 | running      | 6 |
| 8 | review       | 6 |

State mapping is the same as the DSH host:

```
thinking → running     tool → running-right
review   → review      waiting → waiting
done     → jumping     failed → failed
idle     → idle
```

`done` plays the jumping animation for 2.4 s before settling to `idle`; `failed` plays for the same window
then falls silent. Both windows are configurable via `PetStateConfig`.

## Compatibility

| Item | Value |
|---|---|
| IDE | IntelliJ IDEA 2023.1+ (`<idea-version since-build="231"/>`) — runs on 2023.1 / 2023.3 / 2024.x / 2025.x |
| JDK | 17 (IDEA 2023.1+ ships JBR 17; bytecode release 17) |
| Build | Apache Maven 3.9+, JDK 17 |
| Resource codec | WebP via `ImageIO` (JDK 21+ ships native WebP; on JDK 17 the plugin logs a clear error if the codec is missing) |

## Build

```sh
mvn -B -DskipTests=true package          # produces target/dsh-pet-whale-1.0.0.jar + ...-plugin.zip
mvn -B test                              # runs 20 unit tests
mvn -B clean verify                      # full clean verify pipeline
```

The shipped zip layout (`target/dsh-pet-whale-1.0.0-plugin.zip`):

```
dsh-pet-whale/
└── lib/
    └── dsh-pet-whale-1.0.0.jar     ← contains META-INF/plugin.xml + images/
```

## Install

1. **Settings → Plugins → ⚙ → Install Plugin from Disk…**
2. Pick `target/dsh-pet-whale-1.0.0-plugin.zip` (or your release build).
3. Restart IDEA. The whale-girl appears at the bottom-right of the screen.

## Repository layout

```
dsh-pet-whale/
├── pom.xml                                      # Maven 17 + platform 231
├── src/assembly/plugin-distribution.xml         # zip layout
├── src/main/resources/
│   ├── META-INF/plugin.xml                      # <idea-version since-build="231"/>
│   └── images/
│       ├── whale/{pet.json, spritesheet.webp}   # original (copied from dsh-pet)
│       ├── whale-refined/{pet.json, spritesheet.webp}
│       └── decorations/whale/{decoration.json, whale-frames.png}
├── src/main/java/com/dsh/petwhale/
│   ├── state/                                   # PetStateMachine + 9-state / 7-phase enums
│   ├── resource/                                # Manifest parser + theme + theme loader
│   ├── listener/                                # EditorFactoryListener + VirtualFileListener
│   ├── ui/                                      # PetFrame (transparent always-on-top) + PetPanel + hover
│   └── startup/PetStartupActivity.java
└── src/test/java/com/dsh/petwhale/              # 20 unit tests
```

## Credits

- Sprites & manifests: derived from [`@linxin666/dsh-pet`](https://github.com/zhu1090093659/dsh-web-ui)
  (BSD-3-Clause / MIT).
- Built with the [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/) and the
  `com.jetbrains.intellij.platform:*` published artifacts at build `231.8109.175`.

## License

BSD-3-Clause (matching the upstream DSH Pet license).