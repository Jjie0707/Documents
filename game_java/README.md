# 山海 · 守夜

一款取材于《山海经》的单机 2D 生存游戏。第一夜，山彘会追击守夜人。第二天去东北方布防，第二夜异兽会围攻祭坛。守住祭坛后，带三枚青玉召唤并击败青丘九尾。

白天可以去西南的山彘巢或西北的三足乌巢挑战守兽。山彘巢的镇山印能为阵中的守夜人和祭坛减伤。三足乌巢的御风印能击退异兽，并吹散附近的飞羽和狐火。两种灵印共用 12 秒冷却。

## 启动

使用 JDK 17 或更高版本。从项目目录运行以下命令，首次构建会下载 Gradle 和 libGDX 依赖。

```sh
./gradlew run
```

Windows 使用 `gradlew.bat run`。如果已经构建桌面发行版，macOS 和 Linux 可直接运行 `./build/install/shanhai-nightwatch/bin/shanhai-nightwatch`。运行 `./gradlew installDist` 可以重新生成发行版。

## 操作

| 按键 | 动作 |
| --- | --- |
| Enter | 开始、继续或再来一局。建造时确认预览位置 |
| WASD / 方向键 | 移动 |
| E | 按住连续采集身边的资源 |
| 鼠标左键 / Space | 左键向鼠标方向挥刀，空格自动瞄准附近异兽。按住可连续挥刀，第三刀更重 |
| 鼠标右键 / Shift | 向移动方向翻滚，静止时向上一次移动的方向翻滚 |
| Q | 吃朱果。篝火旁会烤熟再吃，恢复更多饱食和气血 |
| 1 / 2 / 3 | 选择篝火 / 镇灵灯 / 绊灵桩，左键在附近空地放置。也可点击右侧建造卡片 |
| R | 在篝火旁消耗 2 山石和 2 青玉淬刃，提升刀法威力 |
| F | 白天探查兽巢。祭坛旁可在第二夜花 2 山石修复，或在破晓后用 3 青玉召唤九尾 |
| C / Tab | 施放当前灵印 / 切换灵印 |
| Esc / M | 暂停 / 切换音效。建造时 Esc 或右键取消放置 |

篝火消耗 4 灵木和 2 山石。镇灵灯消耗 2 灵木、4 山石和 1 青玉，会自动攻击附近异兽。可把青玉用在营地防御或淬刃，同时留出祭坛的供物。打怪也会提升气血上限和刀法威力。

绊灵桩消耗 3 灵木和 2 山石，最多可放四座。山彘进入浅绿圈时，移动速度降低约 55%，持续约 2.3 秒。绊灵桩 5 秒后可再次触发。把它放在镇灵灯的攻击范围内，能让山彘在灯下停留更久。

红圈表示扑击或撞击的落点，亮线表示飞羽或狐火的方向。九尾扑击落地或施放狐火后会短暂露出破绽，趁那时近身连斩。它气血接近一半时会召来三足乌，随后更频繁地扇射狐火。

在攻击即将命中时翻滚，可触发完美闪避。接下来 2 秒内，首次命中的挥刀额外造成刀法威力一半的伤害。顶部的金色计时条会提示反击还剩多久。

## 源码与素材

- `DesktopLauncher.java` 启动 libGDX 桌面窗口，`Nightwatch.java` 读取输入、绘制画面和播放声音，`Wilds.java` 处理生存、建造和战斗规则，`MotionArt.java` 将动作图的脚底对齐到地面位置。
- 游戏使用 libGDX 1.14.2 和 LWJGL3。 [libGDX 官方文档](https://libgdx.com/wiki/)
- 中文字体是 Noto Serif SC 的字形子集，授权文件随字体附在 `assets/fonts/OFL.txt`。 [字体来源](https://github.com/google/fonts/tree/main/ofl/notoserifsc)
- 水墨场景、角色动作图和兽巢插画使用内置 ImageGen 生成，提示词见下文。

<details>
<summary>美术素材与生成提示词</summary>

**封面** · `assets/art/title.png`

> Use case illustration-story. Game title screen art for a 2D Java survival game inspired by the Chinese Shan Hai Jing. Wide 16:9 landscape, refined traditional Chinese ink wash on pale worn xuan paper with indigo and celadon wash, restrained cinnabar. On the RIGHT 65%: very large mythical antlered fox-like ancient mountain beast silhouette looking down from a steep mountain ridge, winding silver river, jagged layered mountains, a lone tiny cloaked traveler carrying an amber lantern on a ridge bottom right, broad vermilion sun halo high on the right. LEFT 35% mostly dark fog and layered soft ink texture for an overlay but no text of any kind. Rich hand-painted ink granulation, deep almost-black green foreground, sophisticated editorial composition, ink bleeding in fine edges, antique Chinese landscape scroll mood, high legibility, no lettering, no UI, no border, no watermark.

**青丘地图** · `assets/art/valley.png`

> Use case stylized-concept. Production-ready playable 2D game battlefield background. Wide 16:10 image. Absolutely top-down overhead map, fixed orthographic, no horizon, Chinese Shan Hai Jing ink-wash adventure. Entire interior 80% is connected WALKABLE FLAT meadow of textured dark muted jade and gray teal with soft amber moss patches and a few very subtle ochre footpaths; low visual frequency and value contrast so foreground sprites will read at 45 pixels. Main flat clearing across center, extending all the way to most edges. All elaborate scenery confined to outermost 10%: layered black ink rocky cliffs, gnarled conifers and a thin ribbon of blue water outside right and upper border, pale parchment ink fog at upper edges, subtle texture of ink inkstone, some cinnabar leaves. Soft nighttime woodland green, slightly luminous pale moss, no bright central highlights, no characters, no icons, no buildings, no objects of gameplay, no UI, no labels or words. Sophisticated hand-painted ink and mineral-pigment game environment.

**角色、异兽、资源与建筑** · `assets/art/atlas.png`

> Use case stylized-concept. Transparent 4 by 3 GAME SPRITE ATLAS for 2D top down Shan Hai Jing Chinese ink wash survival game. Square canvas, exactly 12 separate illustrated full-body cutouts, evenly placed at precise centers of a 4-column x 3-row invisible uniform grid. Each of the 12 sprites fits entirely within central 75% of its cell, generous empty clear transparent margins all around each cutout. NO cell lines, no text, no black/white background, no labels, no floor or shadows outside sprite, no extra objects. View all from the same 3/4 overhead orthographic 2D game view, front or facing lower right. Traditional fine ink brush outlines, subtle antique mineral pigments, light paper highlights, dark outline, distinct bold silhouette at 50 pixels. Row ONE from left to right: (1) small cream-and-teal cloaked Chinese wanderer in conical bamboo hat carrying amber lantern; (2) small dark horned mountain boar with cinnabar tusks and red eyes, a Shan Hai Jing beast; (3) massive white nine-tailed fox beast with vermilion markings and separated flowing tails; (4) wind-bent evergreen pine with chunky layered sage foliage. Row TWO: (1) pile of bluish light gray irregular stones; (2) short verdigris bush of bright red wild berries; (3) three cyan pointed jade crystals; (4) amber campfire on a circle of stones. Row THREE: (1) Chinese bronze warding lantern on a squat stone pedestal with glowing pale green flame; (2) ancient Chinese circular stone ritual altar with tall central jade shard; (3) dark antique angular purple three-legged crow with red eyes; (4) stack of split golden brown logs. All 12 cutouts must be clearly isolated and countable, on real transparency.

**守夜人动作** · `assets/art/wanderer-motion.png`

> Stylized-concept, production-ready 2D animated sprite atlas, TRUE TRANSPARENCY. From reference, take only the cream and jade cloaked Chinese wanderer wearing woven conical hat and holding amber lantern. Give him a short steel saber in the other hand. Draw the SAME character in exactly 16 separate, substantially different full-body motion frames in a strict evenly spaced 4-column by 4-row grid. Canvas square, each frame contained in central 75% of its square cell and feet aligned to a consistent baseline, generous completely transparent padding, no grid lines, no text, no shadow below feet, no background. Every frame facing screen RIGHT in a consistent slightly overhead 3/4 game camera. Style: hand-painted Chinese Shan Hai Jing ink-brush and mineral pigments, clean controlled silhouettes and less noisy fine linework, broad cream overcoat, muted celadon trailing cloth, black shoes, broad straw hat, amber lantern visible. Same exact proportions, scale and colors across all frames. Row 1 left to right: idle four subtle breathing poses with lantern swaying. Row 2: four CLEARLY DIFFERENT running poses, first right foot extended ahead and left back, then compact passing pose, then left foot ahead and right back, then passing pose again. Row 3: four saber attack poses, draw sword behind body, explosive slash forward with sword horizontally extended, visible torso twisted follow-through, recover to ready stance. Row 4: four dodge poses, low crouch, shoulder tuck into forward roll with knees up, low landing, get back up. Exactly one character per cell, all 16 cells, no stray pixels, no cropped arms or blades, no extra characters, no decorative slash effect. The sheet will be programmatically sliced into four rows and four columns.

**青丘九尾动作** · `assets/art/fox-motion.png`

> Stylized-concept, production-ready 2D animated sprite atlas for a Chinese Shan Hai Jing game, TRUE TRANSPARENCY. From reference use only the ivory white nine-tailed fox with cinnabar-red marks, dark ear tips and large flowing red-edged tails. Draw exactly 16 substantially different motion frames of this SAME fox, in a strict equally spaced 4-column x 4-row grid on a square canvas. Every silhouette contained in the central 75% of its cell with roomy transparent gutters and legs aligned to a consistent baseline. Facing screen RIGHT, consistent three-quarter slightly overhead game camera, proportions and scale. Distinct elegant silhouettes and restrained mineral pigment outlines. No shadows under feet, labels, grids, text, background, creatures or objects beyond the 16 foxes. Row 1: four poised idle frames with subtle changes to ears and articulated tail fan. Row 2: four VISIBLY DIFFERENT quadruped run frames, extended paws out, legs passing under, alternate extended stride, compressed body ready to spring. Row 3: four powerful pounce frames, crouch coiled with head low, forelegs stretched forward airborne, claws landing forward, recovery crouch. Row 4: four spell casting frames, arch body and pull head back, head raised and tails fan upright, lunge head forward with open muzzle, settle back. Paint the fox only, no fire or magical effects outside its silhouette. Exactly one sprite per cell. Sheet will be programmatically sliced into four rows and four columns.

**山彘与三足乌动作** · `assets/art/beasts-motion.png`

> Stylized-concept. Transparent sprite atlas for a traditional Chinese mineral-pigment ink-brush game, 4 columns x 4 rows exactly, every cell well separated and evenly centered. Match the referenced atlas's dark horned boar with red tusks and dark violet three-legged crow only. Consistent three-quarter overhead camera facing RIGHT, scale and identity preserved across each creature. Row 1: four clearly different running poses of the same dark horned boar with alternating extended and bent legs. Row 2: four boar attack poses, lower head to charge, dig hooves, thrust red tusks forward, plant and recover. Row 3: four flight phases of the same three-legged violet crow, wings fully up, wings halfway down, wings down and extended, wings partially back up. Row 4: four crow attack frames, wings spread with neck pulled back, a strike with beak forward, wings flick back, recover. True transparent background, no ground shadows, no words, no grid lines, no trails or FX, one full-body character per cell. Keep all animals within central 75% of each cell. Clean bold silhouettes and consistent ground position.

**兽巢与灵印** · `assets/art/den-relics.png`

> Stylized-concept for a Shan Hai Jing isometric 2D game. TRUE TRANSPARENCY. Exactly four separate top-down three-quarter game sprites in a 2x2 grid, each centered in its cell with ample clear transparent padding on all sides. TOP LEFT: low stone burrow with an arched dark opening beneath ochre moss and two worn curved tusks, earthy amber. TOP RIGHT: dark crooked wind-twisted branch holding a raven's nest perched on a flat rock, soft violet hanging feathers. BOTTOM LEFT: round amber-brown jade talisman incised with a simple motif of three mountain peaks. BOTTOM RIGHT: round lavender jade talisman incised with a curved gust motif. Symbols only, no characters or text. Traditional ink-brush outlines and mineral-pigment painting, calm muted earth tones, bold readable silhouettes at small scale. Single cohesive style, carefully controlled game art, no ground, no backdrop, no cast shadows, no borders, no letters or words. Square canvas.

**绊灵桩** · `assets/art/snare.png`

> Single game sprite asset for the 2D top-down Chinese ink wash game Shan Hai Nightwatch. One compact ancient Chinese magical snare made of three short bamboo stakes lashed to a dark irregular shale base, with a few suspended jade-green knotted threads forming a low triangular tripwire across them. A small muted celadon stone inset at the middle. Clearly reads as a short broad ground trap, not a tall totem, not a campfire. Three quarter overhead orthographic, showing top and front, eye high looking down toward the upper center, with no visible horizon. Ground contact at center bottom of silhouette. Classic Chinese brush linework and mineral pigment shading in dark forest jade, pine charcoal, neutral gray stone, desaturated warm amber bamboo, soft paper highlights. Strong readable silhouette at 60 pixels, no excessive detail. Single full cutout centered with generous margins, real transparency, no floor, no background, no floating particles, no cast shadow, no writing or UI.

</details>
