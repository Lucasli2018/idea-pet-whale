# Idea Pet Whale — 鲸鱼娘桌宠（JetBrains IDEA 2023.1+）

> 把 [DSH Web 端的鲸鱼娘桌宠](https://github.com/zhu1090093659/dsh-web-ui/tree/main/packages/dsh-pet)
> 移植到 JetBrains IDEA 平台。一只悬浮在屏幕右下角的桌面宠物，监听你的编辑器和 VCS 活动，
> 自动切换动画状态。

<table>
  <tr>
    <td align="center"><img src="src/main/resources/images/whale/previews/idle.gif" alt="idle" /><br /><b>idle（空闲）</b></td>
    <td align="center"><img src="src/main/resources/images/whale/previews/running.gif" alt="running" /><br /><b>running（跑动）</b></td>
    <td align="center"><img src="src/main/resources/images/whale/previews/waving.gif" alt="waving" /><br /><b>waving（挥手）</b></td>
    <td align="center"><img src="src/main/resources/images/whale/previews/jumping.gif" alt="jumping" /><br /><b>jumping（跳跃）</b></td>
    <td align="center"><img src="src/main/resources/images/whale/previews/failed.gif" alt="failed" /><br /><b>failed（失败）</b></td>
    <td align="center"><img src="src/main/resources/images/whale/previews/review.gif" alt="review" /><br /><b>review（审视）</b></td>
  </tr>
</table>

## 项目简介

DSH Pet Whale 是一个 JetBrains IDEA 平台的桌面宠物插件（Plugin）。它把 DSH Web 端的"鲸鱼娘"
桌宠体验带到 IDE 里：写代码时，桌面右下角会出现一只可爱的鲸鱼娘，跟着你的操作变换状态。
你写代码时它在悠闲地游泳，你提交代码时它挥手致敬，测试全绿时它开心地跳起来庆祝。

资源（动画、配置、装饰）全部来自 `@linxin666/dsh-pet` 项目，原汁原味搬过来，**没有任何重绘或
二次创作**。本项目仅做平台适配：在 IDEA Swing 框架下复刻 DSH 状态机。

## 功能特性

| 功能 | 说明 |
|---|---|
| 9 状态动画 | idle / running-right / running-left / waving / jumping / failed / waiting / running / review，每种状态都有专属的节奏和帧时长 |
| 2 套内置主题 | **原版**鲸鱼娘和**精致版**鲸鱼娘，设置页一键切换 + 实时预览 |
| 编辑器联动 | 编辑器获得焦点时切换到 `thinking/running` 动画；关闭后定时器自动回到 IDLE |
| VCS 联动 | `.git/` 目录下任何创建/删除/属性变更触发短暂的 `review` 动画，提示正在提交/拉取 |
| 分区悬浮交互层 | 鼠标停在**头部**→头顶数值胶囊（名字 / Lv / 称号 + 亲密度、小鱼干、点数三条彩色渐变进度条，左对齐）；停在**脚部**→紧凑按钮卡片（喂食 / 改名 / 设置 / 隐藏，按钮紧贴文字） |
| 台词气泡 | 渐变底 + 指向宠物的三角尾巴，淡入下滑出现、2.5s 后淡出；**实时跟随宠物移动**（拖拽时贴着走，贴屏幕边自动翻尾巴），文字居中 |
| 数值帮助说明 | 胶囊上的"？"按钮，悬停或点击弹出亲密度 / 称号 / 小鱼干 / 点数的玩法说明 |
| 投喂与成长 | 喂小鱼干（亲密度 +10、点数 +5）；每 200 亲密度升 1 级；称号五阶：素昧平生 → 灵魂伴侣 |
| 久坐关怀 | 连续编码 60 分钟气泡提醒喝水/起身（可关闭）；深夜自动困倦台词 |
| 拖拽定位 | 按住桌宠可以拖到屏幕任何位置，位置跨重启记忆 |
| 自动溜达 | 空闲时鲸鱼娘在屏幕上自由左右跑动、撞墙反弹；设置页「行为 → 溜达速度」可拖滑块调速（1 最慢 ~ 8 最欢快，默认 3） |
| 隐藏 / 归位 | 悬停面板的"隐藏"按钮收起桌宠（无残留召唤按钮，需到设置页「显示宠物」重新显示或卡片「归位」）；设置页「回到原位」与卡片「归位」一键送回右下角老家 |
| 始终置顶 | 无边框透明 `JWindow`，浮在所有窗口之上，不进任务栏 / Alt-Tab |

## 玩法数值说明

| 数值 | 规则 |
|---|---|
| 亲密度 | 每次喂食 +10；Lv 每 200 点升 1 级（Lv.1 起算）；进度条按当前称号区间折算 |
| 称号 | 素昧平生 <100 / 一见如故 100~299 / 心意相通 300~599 / 心有灵犀 600~999 / 灵魂伴侣 1000+ |
| 小鱼干 | 喂食消耗，初始 20 条；进度条按 99 条满格 |
| 点数 | 每次喂食 +5 累计；进度条按 500 满格 |

## 动画契约（Animation Contract）

插件使用与 DSH Pet 兼容的 8 列 × 9 行精灵图，每行一种动画：

| 行号 | 动画 | 默认帧数 |
|---|---|---|
| 0 | idle（空闲） | 6 |
| 1 | running-right（向右跑） | 8 |
| 2 | running-left（向左跑） | 8 |
| 3 | waving（挥手） | 4 |
| 4 | jumping（跳跃） | 5 |
| 5 | failed（失败） | 8 |
| 6 | waiting（等待） | 6 |
| 7 | running（跑动） | 6 |
| 8 | review（审视） | 6 |

活动阶段（ActivityPhase）到动画的映射，与 DSH 主机完全一致：

```
thinking → running-left  tool    → running-right
review   → review       waiting → waiting
done     → jumping      failed  → failed
idle     → idle
```

`done` 会播放 2.4 秒的 jumping 庆祝动画，然后切回 idle；`failed` 同样播放 2.4 秒后归零沉默。
两个时间窗都可以通过 `PetStateConfig` 配置。

## 兼容性

| 项 | 值 |
|---|---|
| IDE | IntelliJ IDEA 2023.1+（`<idea-version since-build="231"/>`），兼容 2023.1 / 2023.3 / 2024.x / 2025.x |
| JDK | 17（IDEA 2023.1+ 自带 JBR 17，字节码 release 17） |
| 构建 | Apache Maven 3.9+ |
| WebP 资源 | 依赖 `ImageIO`（JDK 21+ 自带 WebP；JDK 17 需要 JDK 自带 codec 或第三方插件，否则报清晰错误） |

## 构建

```sh
mvn -B -DskipTests=true package          # 产出 target/idea-pet-whale-0.0.15.jar + ...-plugin.zip
mvn -B test                              # 运行 65 个单元测试
mvn -B clean verify                      # 完整验证流水线
```

打包后的 zip 结构（`target/idea-pet-whale-0.0.15-plugin.zip`）：

```
idea-pet-whale/
├── README.md
├── LICENSE
└── lib/
    └── idea-pet-whale-0.0.1.jar     ← 内含 META-INF/plugin.xml + images/
```

## 安装

1. **Settings → Plugins → ⚙ → Install Plugin from Disk…**
2. 选择 `target/idea-pet-whale-0.0.15-plugin.zip`（或你的发布产物）。
4. 重启 IDEA。鲸鱼娘会出现在屏幕右下角，开始她的空闲动画。

## 仓库结构

```
idea-pet-whale/
├── pom.xml                                      # Maven 17 + 平台 231
├── src/assembly/plugin-distribution.xml         # zip 布局
├── src/main/resources/
│   ├── META-INF/plugin.xml                      # <idea-version since-build="231"/>
│   └── images/
│       ├── whale/{pet.json, spritesheet.webp}   # 原版（拷贝自 dsh-pet）
│       ├── whale-refined/{pet.json, spritesheet.webp}
│       └── decorations/whale/{decoration.json, whale-frames.png}
├── src/main/java/com/dsh/petwhale/
│   ├── state/                                   # PetStateMachine 状态机 + 9 动画/7 阶段枚举
│   ├── resource/                                # Manifest 解析 + 主题枚举 + 资源加载
│   ├── listener/                                # EditorFactoryListener + VirtualFileListener
│   ├── ui/                                      # PetFrame（透明始终置顶）+ PetPanel + 悬停面板
│   └── startup/PetStartupActivity.java          # StartupActivity 启动入口
└── src/test/java/com/dsh/petwhale/              # 65 个单元测试
```

## 关键设计决策

| 决策 | 选择 | 原因 |
|---|---|---|
| 插件形态 | 全局透明 `JFrame`（always-on-top） | 视觉上最像 dsh-pet 的桌面宠物，不占工具窗口位 |
| 平台版本 | build `231.8109.175`（2023.1） | 最低兼容 2023.1，向前到 2025.x；API 都是 2023.1 就有的 |
| 资源解析 | 手写 JSON parser（无第三方依赖） | `pet.json` 字段稳定且小，手写解析最简；避免引入 json-simple 等 |
| 状态机 | 与 dsh-pet `state.ts` 一一对应 | 行为跨平台一致；以后想做平台互操作（如 MCP）易对接 |
| 构建 | Apache Maven + `maven-assembly-plugin` | 与同仓库 `idea-keyboard-stats` 完全一致的流水线 |
| Build/Test 联动 | MVP 不实现 | 官方 Maven 仓库没有 `build` / `smRunner` artifact，需 IDEA 平台级 plugin |

## 鸣谢

- 精灵图与清单文件：源自 [`@linxin666/dsh-pet`](https://github.com/zhu1090093659/dsh-web-ui)
  （BSD-3-Clause / MIT）。
- 基于 [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/) 构建，使用
  `com.jetbrains.intellij.platform:*` 在 build `231.8109.175` 上发布的制品。

## 许可证

BSD-3-Clause（与上游 DSH Pet 一致）。