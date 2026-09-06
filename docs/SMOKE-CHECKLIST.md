# SMOKE-CHECKLIST — idea-pet-whale v0.0.1

Run after every build. Each step is a single human-verifiable action.

## 0. Build artifacts present

```sh
ls -lh target/idea-pet-whale-0.0.1.jar target/idea-pet-whale-0.0.1-plugin.zip
# Expect both files; jar ~4.2MB, zip ~4.2MB
```

## 1. Zip structure check

```sh
unzip -l target/idea-pet-whale-0.0.1-plugin.zip
# Expect:
#   idea-pet-whale/
#   idea-pet-whale/lib/
#   idea-pet-whale/lib/idea-pet-whale-0.0.1.jar
#   idea-pet-whale/README.md
```

## 2. plugin.xml at jar root

```sh
unzip -p target/idea-pet-whale-0.0.1.jar META-INF/plugin.xml | head -20
# Expect <idea-version since-build="231"/> and 3 extensions
# (postStartupActivity, editorFactoryListener, applicationService)
```

## 3. Images inside jar

```sh
unzip -l target/idea-pet-whale-0.0.1.jar | grep images
# Expect 5 files: 2x pet.json, 2x spritesheet.webp, decoration.json, whale-frames.png
```

## 4. Unit tests

```sh
mvn -B test
# Expect: Tests run: 23, Failures: 0, Errors: 0, Skipped: 1
# (Skipped 1 = PetResourcesTest.loadWhaleOrAssumeSkip 在 JDK 17 无 WebP codec 时 assumeAborting 跳过)
```

## 5. Install in a real IDEA

1. Settings → Plugins → ⚙ → Install Plugin from Disk… → pick the zip.
2. Restart IDEA.
3. Expect: a whale-girl sprite appears at the bottom-right of the screen, idle animation looping.

## 6. Interaction smoke

| Step | Expected |
|---|---|
| Click on an editor tab | Whale switches to `thinking` (running animation) |
| Right-click the whale | Hover panel appears with "精致版 / 隐藏" buttons |
| Click "精致版" | Whale swaps to the refined atlas on the next repaint |
| Click "隐藏" | Whale disappears, a small "召唤鲸鱼娘" button shows at the bottom-right |
| Click "召唤鲸鱼娘" | Whale reappears |
| `git commit` / `git pull` in terminal inside the project | Whale briefly plays the `review` animation, then settles back |
| Drag the whale | Frame follows the cursor; release anywhere |

## 7. Failure-mode smoke

| Failure | Expected behavior |
|---|---|
| IdeaPlatform version older than 2023.1 | Install fails with "requires 2023.1 or newer" |
| WebP codec missing on JDK 17 | Pet panel logs a clear error; the plugin still loads (other features intact) |