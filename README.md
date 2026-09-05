# DSH Pet Whale — 鲸鱼娘桌宠（JetBrains IDEA 2023.1+）

> 把 DSH Web 端的鲸鱼娘桌宠移植到 JetBrains IDEA 平台。一只悬浮在屏幕右下角的
> 桌面宠物，监听你的编辑器和 VCS 活动，自动切换动画状态。
>
> 中文详细说明见 [`README.zh.md`](README.zh.md)。

<table>
  <tr>
    <td align="center"><img src="https://gitee.com/li-luoqiang/dsh-pet-whale/raw/main/src/main/resources/images/whale/previews/idle.gif" alt="idle" /><br /><b>idle</b></td>
    <td align="center"><img src="https://gitee.com/li-luoqiang/dsh-pet-whale/raw/main/src/main/resources/images/whale/previews/running.gif" alt="running" /><br /><b>running</b></td>
    <td align="center"><img src="https://gitee.com/li-luoqiang/dsh-pet-whale/raw/main/src/main/resources/images/whale/previews/waving.gif" alt="waving" /><br /><b>waving</b></td>
    <td align="center"><img src="https://gitee.com/li-luoqiang/dsh-pet-whale/raw/main/src/main/resources/images/whale/previews/jumping.gif" alt="jumping" /><br /><b>jumping</b></td>
    <td align="center"><img src="https://gitee.com/li-luoqiang/dsh-pet-whale/raw/main/src/main/resources/images/whale/previews/failed.gif" alt="failed" /><br /><b>failed</b></td>
    <td align="center"><img src="https://gitee.com/li-luoqiang/dsh-pet-whale/raw/main/src/main/resources/images/whale/previews/review.gif" alt="review" /><br /><b>review</b></td>
  </tr>
</table>

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

## Compatibility

- IDE: IntelliJ IDEA 2023.1+ (`<idea-version since-build="231"/>`)
- JDK: 17 (release 17 bytecode)
- Build: Apache Maven 3.9+

## Build

```sh
mvn -B -DskipTests=true package          # produces target/dsh-pet-whale-1.0.0.jar + ...-plugin.zip
mvn -B test                              # runs 20 unit tests
mvn -B clean verify                      # full clean verify pipeline
```

## Install

1. **Settings → Plugins → ⚙ → Install Plugin from Disk…**
2. Pick `target/dsh-pet-whale-1.0.0-plugin.zip` (or your release build).
3. Restart IDEA. The whale-girl appears at the bottom-right of the screen.

## Credits

- Sprites & manifests: derived from [`@linxin666/dsh-pet`](https://github.com/zhu1090093659/dsh-web-ui)
  (BSD-3-Clause / MIT).
- Built with the [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/) and the
  `com.jetbrains.intellij.platform:*` published artifacts at build `231.8109.175`.

## License

BSD-3-Clause (matching the upstream DSH Pet license).