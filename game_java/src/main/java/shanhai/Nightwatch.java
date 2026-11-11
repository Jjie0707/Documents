package shanhai;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;

import static shanhai.Wilds.Resource;
import static shanhai.Wilds.Structure;

final class Nightwatch extends ApplicationAdapter {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 800;
    private static final int FIELD_X = 24;
    private static final int FIELD_Y = 91;
    private static final int FIELD_TOP = FIELD_Y + Wilds.HEIGHT;
    private static final int SIDE_X = 956;
    private static final int SIDE_W = 300;

    private static final Color INK = Color.valueOf("101915");
    private static final Color PANEL = Color.valueOf("1b2721");
    private static final Color BORDER = Color.valueOf("405349");
    private static final Color PAPER = Color.valueOf("f4e7cd");
    private static final Color MUTE = Color.valueOf("b3bcb0");
    private static final Color GOLD = Color.valueOf("dfb875");
    private static final Color GOLD_LIGHT = Color.valueOf("f7d69a");
    private static final Color JADE = Color.valueOf("9dd6b3");
    private static final Color RED = Color.valueOf("e58a70");
    private static final Color FOX_FIRE = Color.valueOf("e77e92");
    private static final Color FEATHER = Color.valueOf("cbb1f2");
    private static final Color MIST = Color.valueOf("a7c6b9");
    private static final Color RUST = Color.valueOf("a84c39");
    private static final Color WHITE = Color.WHITE;
    private static final Color BLACK = Color.BLACK;

    private enum Icon {
        FOX(699, 38, 333, 337),
        TREE(1078, 61, 355, 313), STONE(38, 463, 290, 208), BERRY(377, 429, 307, 248),
        JADE(753, 426, 236, 259), FIRE(1115, 411, 281, 279), WARD(93, 729, 196, 320),
        ALTAR(364, 735, 337, 296), LOGS(1094, 793, 316, 231),
        DEN_STONE(true, 11, 119, 612, 467), DEN_WIND(true, 634, 55, 609, 554),
        SEAL_STONE(true, 63, 664, 502, 552), SEAL_WIND(true, 674, 664, 510, 554);

        final boolean relic;
        final int x;
        final int y;
        final int w;
        final int h;
        TextureRegion front;
        TextureRegion reverse;

        Icon(int x, int y, int w, int h) {
            this(false, x, y, w, h);
        }

        Icon(boolean relic, int x, int y, int w, int h) {
            this.relic = relic;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private record Actor(Icon icon, MotionArt.Frame frame, float x, float y, float size, float lift, boolean right,
                         float brightness, float opacity) {
        Actor(Icon icon, float x, float y, float size, float lift, boolean right, float brightness, float opacity) {
            this(icon, null, x, y, size, lift, right, brightness, opacity);
        }
    }

    private record Button(int x, int y, int w, int h) {
        boolean contains(Vector2 p) {
            return p.x >= x && p.x <= x + w && p.y >= y && p.y <= y + h;
        }
    }

    private static final Button START = new Button(77, 228, 252, 57);
    private static final Button PAUSE = new Button(1186, 711, 70, 39);
    private static final Button SOUND = new Button(1105, 711, 71, 39);
    private static final Button FIRE_CARD = new Button(SIDE_X + 15, 300, SIDE_W - 30, 53);
    private static final Button WARD_CARD = new Button(SIDE_X + 15, 239, SIDE_W - 30, 53);
    private static final Button PRIMARY = new Button(440, 265, 400, 52);
    private static final Button HOME = new Button(440, 207, 400, 42);

    private final FitViewport viewport = new FitViewport(WIDTH, HEIGHT);
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector2 mouse = new Vector2();
    private final Rectangle fieldBounds = new Rectangle(FIELD_X, FIELD_Y, Wilds.WIDTH, Wilds.HEIGHT);
    private final Rectangle scissors = new Rectangle();
    private final ArrayList<Actor> actors = new ArrayList<>();
    private final EnumMap<Wilds.Cue, Sound> sounds = new EnumMap<>(Wilds.Cue.class);
    private SpriteBatch batch;
    private BitmapFont small;
    private BitmapFont body;
    private BitmapFont medium;
    private BitmapFont large;
    private BitmapFont headline;
    private Texture title;
    private Texture valley;
    private Texture atlas;
    private Texture relics;
    private Texture wandererMotion;
    private Texture foxMotion;
    private Texture beastsMotion;
    private MotionArt motion;
    private Texture pixel;
    private Texture glow;
    private Texture ring;
    private Texture shade;
    private Texture vignette;
    private Texture slashArc;
    private Wilds wilds;
    private Structure placing;
    private boolean placementUsesPointer;
    private boolean pointerMoved;
    private float harvestCooldown;
    private boolean paused;
    private boolean muted;
    private boolean waitForLeftRelease;
    private float clock;

    @Override
    public void create() {
        batch = new SpriteBatch();
        title = texture("art/title.png");
        valley = texture("art/valley.png");
        atlas = texture("art/atlas.png");
        relics = texture("art/den-relics.png");
        wandererMotion = texture("art/wanderer-motion.png");
        foxMotion = texture("art/fox-motion.png");
        beastsMotion = texture("art/beasts-motion.png");
        motion = new MotionArt(wandererMotion, foxMotion, beastsMotion);
        for (Icon icon : Icon.values()) {
            icon.front = new TextureRegion(icon.relic ? relics : atlas, icon.x, icon.y, icon.w, icon.h);
            icon.reverse = new TextureRegion(icon.front);
            icon.reverse.flip(true, false);
        }
        pixel = makeTexture(1, 1, (u, v) -> 1f);
        glow = makeTexture(96, 96, (u, v) -> {
            float distance = (float) Math.hypot(u * 2 - 1, v * 2 - 1);
            return Math.max(0, 1 - distance) * Math.max(0, 1 - distance);
        });
        ring = makeTexture(192, 192, (u, v) -> {
            float distance = (float) Math.hypot(u * 2 - 1, v * 2 - 1);
            return Math.max(0, 1 - Math.abs(distance - 0.954f) * 47);
        });
        shade = makeTexture(256, 1, (u, v) -> Math.max(0, 0.85f - u * 0.98f));
        vignette = makeTexture(256, 160, (u, v) -> {
            double radius = Math.hypot((u - 0.5) * 1.55, (v - 0.5) * 1.65);
            return (float) Math.pow(Math.min(1, Math.max(0, (radius - 0.33) / 0.69)), 1.7);
        });
        slashArc = makeTexture(192, 192, (u, v) -> {
            double ax = u * 2 - 1;
            double ay = v * 2 - 1;
            double angle = Math.abs(Math.atan2(ay, ax));
            if (angle > 1.72) {
                return 0;
            }
            double radius = Math.hypot(ax, ay);
            double edge = 0.91 - 0.075 * angle;
            double width = 0.17 * (1 - angle / 1.75) + 0.022;
            return (float) (Math.max(0, 1 - Math.abs(radius - edge) / width)
                    * Math.pow(1 - angle / 1.72, 0.6));
        });
        try (var fonts = new FontSource()) {
            small = fonts.make(15);
            body = fonts.make(19);
            medium = fonts.make(25);
            large = fonts.make(43);
            headline = fonts.make(75);
        }
        for (var cue : Wilds.Cue.values()) {
            String name = cue.name().toLowerCase(java.util.Locale.ROOT);
            sounds.put(cue, Gdx.audio.newSound(Gdx.files.classpath("sound/" + name + ".wav")));
        }
    }

    private static final class FontSource implements AutoCloseable {
        private final FreeTypeFontGenerator source =
                new FreeTypeFontGenerator(Gdx.files.classpath("fonts/serif.ttf"));
        private final String glyphs = FreeTypeFontGenerator.DEFAULT_CHARS
                + Gdx.files.classpath("fonts/glyphs.txt").readString("UTF-8");

        BitmapFont make(int size) {
            var settings = new FreeTypeFontGenerator.FreeTypeFontParameter();
            settings.size = size;
            settings.characters = glyphs;
            settings.minFilter = Texture.TextureFilter.Linear;
            settings.magFilter = Texture.TextureFilter.Linear;
            return source.generateFont(settings);
        }

        @Override
        public void close() {
            source.dispose();
        }
    }

    @FunctionalInterface
    private interface Alpha {
        float sample(float u, float v);
    }

    private static Texture makeTexture(int width, int height, Alpha alpha) {
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        image.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int a = Math.round(alpha.sample((x + 0.5f) / width, (y + 0.5f) / height) * 255);
                image.drawPixel(x, y, 0xffffff00 | a);
            }
        }
        Texture result = new Texture(image);
        image.dispose();
        result.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return result;
    }

    private static Texture texture(String path) {
        Texture texture = new Texture(Gdx.files.classpath(path), true);
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        return texture;
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void render() {
        float dt = Math.min(0.05f, Gdx.graphics.getDeltaTime());
        clock += dt;
        float previousMouseX = mouse.x;
        float previousMouseY = mouse.y;
        viewport.unproject(mouse.set(Gdx.input.getX(), Gdx.input.getY()));
        pointerMoved = mouse.x != previousMouseX || mouse.y != previousMouseY;
        handleInput(dt);

        ScreenUtils.clear(0.035f, 0.06f, 0.045f, 1f);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        if (wilds == null) {
            drawTitle();
        } else {
            drawGame();
        }
        batch.end();
    }

    private void handleInput(float dt) {
        boolean click = Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
        boolean leftDown = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        if (!leftDown) {
            waitForLeftRelease = false;
        }
        boolean rightClick = Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT);
        boolean enter = pressed(Keys.ENTER);
        if (pressed(Keys.M) || (wilds != null && click && SOUND.contains(mouse))) {
            muted = !muted;
        }
        if (wilds == null) {
            if (enter || (click && START.contains(mouse))) {
                start();
            }
            return;
        }
        if (wilds.result != Wilds.Result.ALIVE || paused) {
            if (enter || (click && PRIMARY.contains(mouse))) {
                if (paused) {
                    paused = false;
                } else {
                    start();
                }
            } else if (click && HOME.contains(mouse)) {
                wilds = null;
                paused = false;
                placing = null;
            } else if (pressed(Keys.ESCAPE)) {
                if (paused) {
                    paused = false;
                } else {
                    wilds = null;
                }
            }
            return;
        }
        boolean escape = pressed(Keys.ESCAPE);
        if (escape && placing != null) {
            placing = null;
            return;
        }
        if (escape || (click && PAUSE.contains(mouse))) {
            paused = true;
            return;
        }

        float moveX = (held(Keys.D, Keys.RIGHT) ? 1 : 0) - (held(Keys.A, Keys.LEFT) ? 1 : 0);
        float moveY = (held(Keys.W, Keys.UP) ? 1 : 0) - (held(Keys.S, Keys.DOWN) ? 1 : 0);
        if (rightClick && placing != null) {
            placing = null;
        } else if (pressed(Keys.SHIFT_LEFT) || pressed(Keys.SHIFT_RIGHT) || rightClick && onField(mouse)) {
            wilds.requestDodge();
        }
        harvestCooldown = Math.max(0, harvestCooldown - dt);
        boolean harvestPressed = pressed(Keys.E);
        if (harvestPressed || Gdx.input.isKeyPressed(Keys.E) && harvestCooldown <= 0) {
            wilds.harvest(harvestPressed);
            harvestCooldown = 0.19f;
        }
        if (pressed(Keys.Q)) {
            wilds.eat();
        }
        if (pressed(Keys.NUM_1) || click && FIRE_CARD.contains(mouse)) {
            placing = Structure.FIRE;
            placementUsesPointer = click;
        } else if (pressed(Keys.NUM_2) || click && WARD_CARD.contains(mouse)) {
            placing = Structure.WARD;
            placementUsesPointer = click;
        } else if (placing != null && pointerMoved) {
            placementUsesPointer = true;
        }
        if (placing != null && (enter || click && onField(mouse))) {
            boolean point = onField(mouse) && (placementUsesPointer || click);
            int previous = wilds.structures(placing);
            wilds.build(placing, point ? mouse.x - FIELD_X : wilds.x + wilds.dx * 46,
                    point ? mouse.y - FIELD_Y : wilds.y + wilds.dy * 46);
            if (wilds.structures(placing) > previous) {
                placing = null;
                waitForLeftRelease = leftDown;
            }
        } else if (placing == null && !waitForLeftRelease && onField(mouse) && (click || leftDown)) {
            wilds.requestAttack(true, mouse.x - FIELD_X, mouse.y - FIELD_Y);
        } else if (pressed(Keys.SPACE) || Gdx.input.isKeyPressed(Keys.SPACE)) {
            wilds.requestAttack(false, 0, 0);
        }
        if (pressed(Keys.F)) {
            wilds.interact();
        }
        if (pressed(Keys.C)) {
            wilds.castSeal();
        }
        if (pressed(Keys.TAB)) {
            wilds.switchSeal();
        }
        if (pressed(Keys.R)) {
            wilds.temper();
        }
        wilds.step(dt, moveX, moveY);
        playCues();
    }

    private void start() {
        wilds = new Wilds(System.nanoTime());
        paused = false;
        placing = null;
        harvestCooldown = 0;
        waitForLeftRelease = false;
        if (!muted) {
            sounds.get(Wilds.Cue.GONG).play(0.36f);
        }
    }

    private void playCues() {
        if (!muted) {
            for (var cue : wilds.cues) {
                sounds.get(cue).play(cue == Wilds.Cue.SWING ? 0.20f : 0.38f);
            }
        }
        wilds.cues.clear();
    }

    private static boolean pressed(int key) {
        return Gdx.input.isKeyJustPressed(key);
    }

    private static boolean held(int key, int alternate) {
        return Gdx.input.isKeyPressed(key) || Gdx.input.isKeyPressed(alternate);
    }

    private static boolean onField(Vector2 point) {
        return point.x >= FIELD_X && point.x <= FIELD_X + Wilds.WIDTH
                && point.y >= FIELD_Y && point.y <= FIELD_TOP;
    }

    private void drawTitle() {
        float coverWidth = HEIGHT * (float) title.getWidth() / title.getHeight();
        image(title, (WIDTH - coverWidth) / 2, 0, coverWidth, HEIGHT, WHITE, 1);
        image(shade, 0, 0, 840, HEIGHT, INK, 0.91f);
        rect(0, 0, WIDTH, 84, INK, 0.64f);
        frame(22, 22, WIDTH - 44, HEIGHT - 44, GOLD, 0.48f);
        rect(79, 533, 46, 5, RED, 1);
        label(headline, "山海 · 守夜", 72, 473, PAPER);
        label(medium, "两夜守山野，一灯照青丘。", 79, 362, PAPER);
        label(body, "探异兽巢，习灵印，迎战青丘九尾。", 80, 322, MUTE);
        button(START, "踏入大荒", true);
        label(small, "ENTER  开始", 79, 207, GOLD);
        label(small, "WASD  移动     E  采集     左键  挥刀     右键  翻滚", 79, 57, PAPER);
        right(small, muted ? "M  声音关闭" : "M  声音开启", 1204, 57, PAPER);
    }

    private void drawGame() {
        rect(0, 0, WIDTH, HEIGHT, INK, 1);
        drawField();
        drawHeader();
        drawJournal();
        drawControls();
        if (paused || wilds.result != Wilds.Result.ALIVE) {
            drawModal();
        }
    }

    private void drawHeader() {
        rect(24, 679, 1232, 98, PANEL, 1);
        frame(24, 679, 1232, 98, BORDER, 1);
        rect(41, 708, 37, 37, RUST, 1);
        frame(45, 712, 29, 29, GOLD, 0.68f);
        center(body, "山", 59.5f, 737, PAPER);
        label(medium, "山海 · 守夜", 91, 752, PAPER);
        label(small, "青丘山野", 92, 712, MUTE);
        rect(252, 700, 1, 56, BORDER, 1);

        meter(275, 734, 162, "气血", (int) Math.ceil(wilds.health) + " / " + wilds.maxHealth(),
                wilds.health / wilds.maxHealth(), RED);
        meter(460, 734, 162, "饱食", (int) Math.ceil(wilds.fullness) + " / 100",
                wilds.fullness / 100, GOLD);
        meter(646, 734, 128, "翻滚", wilds.dodgeCooldown > 0 ? "恢复中" : "可用",
                1 - wilds.dodgeCooldown / Wilds.DODGE_COOLDOWN, JADE);

        label(body, wilds.phase.name, 815, 752, wilds.phase.night ? RED : PAPER);
        String remaining = wilds.phase == Wilds.Phase.DAWN
                ? (wilds.boss == null ? "前往祭坛" : "迎战九尾")
                : (int) Math.ceil(wilds.phase.end - wilds.elapsed) + " 秒";
        right(small, remaining, 1079, 751, MUTE);
        drawTimeLine();
        button(SOUND, muted ? "静音" : "声音", false);
        button(PAUSE, "暂停", false);
    }

    private void meter(float x, float y, int width, String title, String value, float fill, Color color) {
        label(small, title, x, y + 12, MUTE);
        right(small, value, x + width, y + 12, PAPER);
        bar(x, y - 14, width, 7, fill, color);
    }

    private void drawTimeLine() {
        float x = 816;
        for (var phase : Wilds.Phase.values()) {
            boolean current = phase == wilds.phase;
            float fill = 0;
            if (phase.ordinal() < wilds.phase.ordinal() || phase == Wilds.Phase.DAWN && current) {
                fill = 1;
            } else if (current) {
                float start = phase.ordinal() == 0 ? 0 : Wilds.Phase.values()[phase.ordinal() - 1].end;
                fill = (wilds.elapsed - start) / (phase.end - start);
            }
            bar(x, 718, 48, 5, fill, phase.night ? RED : GOLD);
            if (current) {
                rect(x, 710, 48, 1, GOLD, 0.58f);
            }
            x += 54;
        }
    }

    private void drawField() {
        float dusk = wilds.phase.night ? 1f : 0f;
        if (wilds.phaseFlash > 2.5f && wilds.phase != Wilds.Phase.FIRST_DAY) {
            float transition = (wilds.phaseFlash - 2.5f) / 1.5f;
            dusk = wilds.phase.night ? 1 - transition : transition;
        }
        batch.flush();
        viewport.calculateScissors(batch.getTransformMatrix(), fieldBounds, scissors);
        if (ScissorStack.pushScissors(scissors)) {
            batch.setColor(1 - dusk * 0.45f, 1 - dusk * 0.34f, 1 - dusk * 0.09f, 1);
            batch.draw(valley, FIELD_X, FIELD_Y, Wilds.WIDTH, Wilds.HEIGHT);
            batch.setColor(WHITE);
            image(vignette, FIELD_X, FIELD_Y, Wilds.WIDTH, Wilds.HEIGHT, INK, 0.44f + dusk * 0.29f);
            drawMist(dusk);
            drawGroundEffects(dusk);
            drawActors(dusk);
            drawSealEffects();
            for (var enemy : wilds.enemies) {
                drawWarning(enemy);
            }
            drawCombatEffects();
            drawPlacement();
            drawFieldLabels();
            batch.flush();
            ScissorStack.popScissors();
        }
        frame(FIELD_X, FIELD_Y, Wilds.WIDTH, Wilds.HEIGHT, BORDER, 1);
        corners(FIELD_X + 2, FIELD_Y + 2, Wilds.WIDTH - 4, Wilds.HEIGHT - 4, GOLD);
    }

    private void drawMist(float dusk) {
        for (int i = 0; i < 5; i++) {
            float x = FIELD_X - 110 + i * 211 + (float) Math.sin(clock * 0.19f + i * 2) * 63;
            float y = FIELD_Y + 25 + (i * 137) % 498 + (float) Math.cos(clock * 0.28f + i) * 19;
            image(glow, x, y, 354, 90 + i * 7, MIST, 0.14f + dusk * 0.16f);
        }
    }

    private void drawGroundEffects(float dusk) {
        float ax = FIELD_X + Wilds.ALTAR_X;
        float ay = FIELD_Y + Wilds.ALTAR_Y + 9;
        boolean altarReady = wilds.phase == Wilds.Phase.DAWN && wilds.boss == null;
        boolean activeAltar = altarReady || wilds.defending();
        Color shrineColor = wilds.shrineFlash > 0 ? RED : JADE;
        halo(ax, ay, 98, shrineColor, activeAltar ? 0.83f : 0.32f);
        if (activeAltar) {
            circle(ax, ay, 50 + wave(2) * 4, shrineColor, 0.60f);
        }
        for (var den : Wilds.Den.values()) {
            if (!wilds.clearedDens.contains(den)) {
                halo(FIELD_X + den.x, FIELD_Y + den.y + 13, 76, denColor(den), wilds.activeDen == den ? 0.67f : 0.37f);
            }
        }
        if (wilds.stoneProtection > 0) {
            float sx = FIELD_X + wilds.sealX;
            float sy = FIELD_Y + wilds.sealY;
            halo(sx, sy, Wilds.SEAL_RANGE * 1.16f, GOLD_LIGHT, 0.61f);
            circle(sx, sy, Wilds.SEAL_RANGE, GOLD_LIGHT, 0.70f);
        }
        for (var building : wilds.buildings) {
            float x = FIELD_X + building.x;
            float y = FIELD_Y + building.y + 7;
            if (building.kind == Structure.FIRE) {
                image(glow, x - 137, y - 76, 274, 170, GOLD, 0.66f + dusk * 0.30f);
                halo(x, y + 11, 62 + wave(12) * 7, GOLD_LIGHT, 0.54f);
                circle(x, y - 5, 72, GOLD, 0.24f);
            } else {
                halo(x, y + 10, 73, JADE, 0.62f);
                if (building.pulse > 0.55f) {
                    float progress = (0.9f - building.pulse) / 0.35f;
                    circle(x, y, 15 + progress * (Wilds.WARD_RANGE - 15), JADE, (1 - progress) * 0.72f);
                }
            }
        }
        var nearest = wilds.nearestNode();
        if (nearest != null) {
            circle(FIELD_X + nearest.x, FIELD_Y + nearest.y + 4, 24, GOLD, 0.82f);
        }
        halo(FIELD_X + wilds.x, FIELD_Y + wilds.y + 14, 67, GOLD, 0.34f + dusk * 0.51f);

    }

    private void drawWarning(Wilds.Enemy enemy) {
        if (enemy.windup <= 0 && enemy.leapTime <= 0 && enemy.strikeFlash <= 0) {
            return;
        }
        float x = FIELD_X + enemy.targetX;
        float y = FIELD_Y + enemy.targetY;
        float progress = 1 - enemy.windup / enemy.windupLength;
        if (enemy.strike == Wilds.Strike.BOLT || enemy.strike == Wilds.Strike.FAN) {
            if (enemy.windup <= 0) {
                return;
            }
            float fromX = FIELD_X + enemy.x;
            float fromY = FIELD_Y + enemy.y + 2;
            Color color = enemy.boss() ? FOX_FIRE : FEATHER;
            line(fromX, fromY, x, y, 1.5f + progress, color, 0.29f + progress * 0.52f);
            circle(x, y, 12 + 9 * (1 - progress), color, 0.73f);
            return;
        }
        Color color = enemy.boss() ? FOX_FIRE : RED;
        float radius = enemy.dangerRadius();
        float alpha = enemy.leapTime > 0 ? 1
                : enemy.windup > 0 ? 0.65f + 0.33f * progress : enemy.strikeFlash / 0.25f;
        halo(x, y, radius * 1.11f, color, alpha * 0.44f);
        circle(x, y, radius, color, alpha);
        if (enemy.windup > 0) {
            circle(x, y, Math.max(6, progress * radius), color, 0.51f);
        }
    }

    private void drawActors(float dusk) {
        actors.clear();
        float normal = 1 - dusk * 0.18f;
        for (var node : wilds.nodes) {
            Icon icon = switch (node.kind) {
                case WOOD -> Icon.TREE;
                case STONE -> Icon.STONE;
                case BERRY -> Icon.BERRY;
                case JADE -> Icon.JADE;
            };
            float size = switch (node.kind) {
                case WOOD -> 68 + (node.x * 3 % 17);
                case STONE -> 49;
                case BERRY -> 47;
                case JADE -> 53;
            };
            actors.add(new Actor(icon, node.x, node.y, size, 0, true, normal,
                    node.regrow > 0 ? 0.23f : 1));
        }
        actors.add(new Actor(Icon.ALTAR, Wilds.ALTAR_X, Wilds.ALTAR_Y, 121, 0, true, normal, 1));
        for (var den : Wilds.Den.values()) {
            Icon icon = den == Wilds.Den.STONE ? Icon.DEN_STONE : Icon.DEN_WIND;
            float light = wilds.clearedDens.contains(den) ? normal * 0.78f : normal;
            actors.add(new Actor(icon, den.x, den.y, 103, 0, true, light, 1));
        }
        for (var building : wilds.buildings) {
            Icon icon = building.kind == Structure.FIRE ? Icon.FIRE : Icon.WARD;
            actors.add(new Actor(icon, building.x, building.y, 56, wave(8) * 1.4f, true, 1, 1));
        }
        for (var enemy : wilds.enemies) {
            float size = enemy.boss() ? 128 : (enemy.den != null ? 89 : enemy.kind == Wilds.Creature.CROW ? 60 : 66);
            float lift = enemy.kind == Wilds.Creature.CROW ? 11 + wave(10 + enemy.x * 0.003f) * 4 : 0;
            if (enemy.leapTime > 0) {
                lift = (float) Math.sin(Math.PI * enemy.leapTime / Wilds.POUNCE_TIME) * 25;
            }
            float light = enemy.hitFlash > 0 ? 0.64f : normal;
            var pose = motion.enemy(enemy, wilds.elapsed);
            actors.add(new Actor(null, pose, enemy.x, enemy.y, size, lift, enemy.facesRight, light, 1));
        }
        var player = motion.wanderer(wilds);
        float bounce = 0;
        circle(FIELD_X + wilds.x, FIELD_Y + wilds.y + 1, 18, GOLD_LIGHT, 0.73f);
        if (wilds.dodgeTime > 0) {
            bounce = (float) Math.sin(Math.PI * wilds.dodgeTime / Wilds.DODGE_DURATION) * 10;
            float distance = Math.min(38, (Wilds.DODGE_DURATION - wilds.dodgeTime) * 420);
            for (int i = 3; i >= 1; i--) {
                float tail = distance * i / 3f;
                player.draw(batch, FIELD_X + wilds.x - wilds.dodgeX * tail,
                        FIELD_Y + wilds.y + bounce - wilds.dodgeY * tail,
                        wilds.facesRight, 0.78f, 0.24f - i * 0.045f);
            }
        }
        actors.add(new Actor(null, player, wilds.x, wilds.y, 70, bounce,
                wilds.facesRight, wilds.hurtFlash > 0 ? 0.62f : 1, 1));
        actors.sort(Comparator.comparingDouble(Actor::y).reversed());
        for (Actor actor : actors) {
            float x = FIELD_X + actor.x;
            float y = FIELD_Y + actor.y;
            if (actor.opacity > 0.5f) {
                image(glow, x - actor.size * 0.41f + 6, y - 10, actor.size * 0.88f, 22, BLACK, 0.72f);
            }
            if (actor.frame != null) {
                actor.frame.draw(batch, x, y + actor.lift, actor.right, actor.brightness, actor.opacity);
            } else {
                sprite(actor.icon, x, y - 3 + actor.lift, actor.size, actor.right, actor.brightness, actor.opacity);
            }
        }
    }

    private void drawSealEffects() {
        if (wilds.sealFlash <= 0) {
            return;
        }
        float x = FIELD_X + wilds.sealX;
        float y = FIELD_Y + wilds.sealY;
        float progress = 1 - wilds.sealFlash / Wilds.SEAL_FLASH;
        float radius = 33 + (1 - (1 - progress) * (1 - progress)) * (Wilds.SEAL_RANGE - 28);
        Color tint = denColor(wilds.lastSeal);
        circle(x, y, radius, tint, (1 - progress) * 0.88f);
        if (wilds.lastSeal == Wilds.Den.WIND) {
            for (int i = 0; i < 3; i++) {
                rotated(slashArc, x, y, radius * 2, radius * 2, i * 120 + progress * 55,
                        tint, (1 - progress) * 0.58f);
            }
        }
    }

    private static Color denColor(Wilds.Den den) {
        return den == Wilds.Den.STONE ? GOLD_LIGHT : FEATHER;
    }

    private void drawCombatEffects() {
        float x = FIELD_X + wilds.x;
        float y = FIELD_Y + wilds.y;
        float followThrough = Wilds.SLASH_TIME - Wilds.SLASH_WINDUP;
        if (wilds.slash > 0 && wilds.slash < followThrough) {
            float progress = 1 - wilds.slash / followThrough;
            boolean finish = wilds.combo == 3;
            float radius = (finish ? 72 : 58) + progress * 19;
            float angle = wilds.slashAngle + (progress - 0.5f) * (wilds.combo == 2 ? -39 : 39);
            rotated(slashArc, x, y + 6, radius * 2, radius * 2, angle, finish ? JADE : GOLD_LIGHT,
                    (1 - progress) * 0.97f);
            if (finish) {
                rotated(slashArc, x, y + 6, radius * 1.68f, radius * 1.68f, angle - 12,
                        PAPER, (1 - progress) * 0.62f);
            }
        }
        if (wilds.hurtFlash > 0) {
            frame(FIELD_X + 2, FIELD_Y + 2, Wilds.WIDTH - 4, Wilds.HEIGHT - 4, RED,
                    wilds.hurtFlash * 2.6f);
        }
        for (var enemy : wilds.enemies) {
            if (enemy.boss() || enemy.health >= enemy.maxHealth) {
                continue;
            }
            bar(FIELD_X + enemy.x - 19, FIELD_Y + enemy.y + 58, 38, 4,
                    enemy.health / enemy.maxHealth, RED);
        }
        for (var bolt : wilds.bolts) {
            float bx = FIELD_X + bolt.x;
            float by = FIELD_Y + bolt.y + 4;
            Color color = bolt.foxFire ? FOX_FIRE : FEATHER;
            line(bx - bolt.vx * 0.10f, by - bolt.vy * 0.10f, bx, by, 3.5f, color, 0.60f);
            halo(bx, by, bolt.foxFire ? 22 : 16, color, 0.92f);
            rect(bx - 2, by - 2, 4, 4, PAPER, 0.90f);
        }
        for (var item : wilds.feedback) {
            float lift = (0.85f - item.life) * 34;
            float fx = FIELD_X + item.x;
            float fy = FIELD_Y + item.y + lift;
            if (item.life > 0.51f && item.text.startsWith("-")) {
                float t = (0.85f - item.life) / 0.34f;
                for (int i = -1; i <= 1; i++) {
                    float px = fx + i * (9 + t * 20);
                    float py = FIELD_Y + item.y - 18 + t * (24 - Math.abs(i) * 8);
                    halo(px, py, 7 - t * 3, item.damage ? RED : GOLD_LIGHT, (1 - t) * 0.9f);
                }
            }
            center(small, item.text, fx + 1, fy - 1, INK);
            center(small, item.text, fx, fy, item.damage ? RED : PAPER);
        }
    }

    private void drawPlacement() {
        if (placing == null) {
            return;
        }
        circle(FIELD_X + wilds.x, FIELD_Y + wilds.y, Wilds.BUILD_RANGE, JADE, 0.58f);
        boolean pointer = placementUsesPointer && onField(mouse);
        float px = pointer ? mouse.x : FIELD_X + wilds.x + wilds.dx * 46;
        float py = pointer ? mouse.y : FIELD_Y + wilds.y + wilds.dy * 46;
        boolean room = wilds.structures(placing) < placing.limit;
        boolean funded = wilds.affordable(placing);
        boolean valid = room && funded && wilds.canBuildAt(px - FIELD_X, py - FIELD_Y);
        Color color = valid ? JADE : RED;
        circle(px, py, placing == Structure.WARD ? Wilds.WARD_RANGE : 72, color, 0.44f);
        halo(px, py + 12, 44, color, 0.66f);
        sprite(placing == Structure.FIRE ? Icon.FIRE : Icon.WARD, px, py - 1, 57, true, 1, 0.66f);
        String hint = !room ? "已达建造上限" : !funded ? "材料不足"
                : valid ? (pointer ? "左键放置" : "ENTER 放置") : "此处不可建造";
        tag(hint, Math.max(FIELD_X + 68, Math.min(FIELD_X + Wilds.WIDTH - 68, px)), py - 20, color);
    }

    private void drawFieldLabels() {
        if (wilds.boss != null && wilds.boss.health > 0) {
            rect(FIELD_X + 250, FIELD_TOP - 58, 418, 43, INK, 0.89f);
            label(small, wilds.boss.enraged() ? "青丘九尾  ·  狂怒" : "青丘九尾",
                    FIELD_X + 266, FIELD_TOP - 24, RED);
            right(small, wilds.boss.exposed() ? "破绽" : "护身", FIELD_X + 652, FIELD_TOP - 24,
                    wilds.boss.exposed() ? GOLD_LIGHT : MUTE);
            bar(FIELD_X + 266, FIELD_TOP - 49, 386, 5,
                    wilds.boss.health / wilds.boss.maxHealth, RED);
        } else if (wilds.defending()) {
            rect(FIELD_X + 250, FIELD_TOP - 58, 418, 43, INK, 0.89f);
            label(small, "祭坛耐久", FIELD_X + 266, FIELD_TOP - 24, PAPER);
            right(small, (int) Math.ceil(wilds.shrine) + " / " + (int) Wilds.SHRINE_MAX,
                    FIELD_X + 652, FIELD_TOP - 24, wilds.shrineFlash > 0 ? RED : JADE);
            bar(FIELD_X + 266, FIELD_TOP - 49, 386, 5, wilds.shrine / Wilds.SHRINE_MAX,
                    wilds.shrine < Wilds.SHRINE_MAX * 0.4f ? RED : JADE);
        } else if (wilds.phaseFlash > 0) {
            String caption = switch (wilds.phase) {
                case FIRST_DAY -> "初日  ·  日落前备好营地";
                case FIRST_NIGHT -> "初夜  ·  山彘出没";
                case SECOND_DAY -> "次日  ·  去东北方的祭坛布防";
                case SECOND_NIGHT -> "次夜  ·  祭坛遇袭";
                case DAWN -> "破晓  ·  青丘祭坛苏醒";
            };
            tag(caption, FIELD_X + 457, FIELD_TOP - 25, GOLD);
        }
        drawGuardianStatus();
        boolean interact = wilds.atAltar() && wilds.boss == null
                && (wilds.defending() || wilds.phase == Wilds.Phase.DAWN);
        if (wilds.boss == null && !interact) {
            tag("青丘祭坛", FIELD_X + Wilds.ALTAR_X, FIELD_Y + Wilds.ALTAR_Y - 9,
                    wilds.phase == Wilds.Phase.DAWN ? JADE : MUTE);
        }
        var denNearby = wilds.nearbyDen();
        for (var den : Wilds.Den.values()) {
            if (den != denNearby || placing != null) {
                String denName = wilds.clearedDens.contains(den) ? den.name + "已平息" : den.name;
                tag(denName, FIELD_X + den.x, FIELD_Y + den.y - 6,
                        wilds.clearedDens.contains(den) ? MUTE : denColor(den));
            }
        }
        var nearest = wilds.nearestNode();
        if (placing == null) {
            if (interact) {
                tag(wilds.defending() ? "F  修复祭坛" : "F  献上青玉",
                        FIELD_X + wilds.x, FIELD_Y + wilds.y - 20, GOLD);
            } else if (denNearby != null) {
                tag(denHint(denNearby), FIELD_X + wilds.x, FIELD_Y + wilds.y - 20, denColor(denNearby));
            } else if (nearest != null) {
                tag("E  采集" + nearest.kind.name, FIELD_X + wilds.x, FIELD_Y + wilds.y - 20, PAPER);
            }
        }
        if (wilds.messageTime > 0) {
            tag(wilds.message, FIELD_X + Wilds.WIDTH / 2f, FIELD_Y + 29, PAPER);
        }
        drawSealStatus();
    }

    private String denHint(Wilds.Den den) {
        if (wilds.clearedDens.contains(den)) {
            return den.name + "已平息";
        }
        if (wilds.activeDen != null) {
            return wilds.activeDen == den ? "击败巢穴守兽" : "先击败当前守兽";
        }
        if (wilds.boss != null) {
            return "九尾现身后无法再探巢";
        }
        return wilds.phase.night ? "白天可探查" : "F  探查" + den.name;
    }

    private void drawGuardianStatus() {
        if (wilds.activeDen == null) {
            return;
        }
        for (var enemy : wilds.enemies) {
            if (enemy.den != wilds.activeDen) {
                continue;
            }
            float x = FIELD_X + 16;
            float y = FIELD_TOP - 59;
            rect(x, y, 158, 42, INK, 0.87f);
            Color color = denColor(enemy.den);
            label(small, enemy.den.name + "守兽", x + 10, y + 32, color);
            bar(x + 10, y + 9, 138, 4, enemy.health / enemy.maxHealth, color);
            return;
        }
    }

    private void drawSealStatus() {
        float x = FIELD_X + 15;
        float y = FIELD_Y + 16;
        if (wilds.equippedSeal == null) {
            if (!wilds.phase.night && wilds.boss == null) {
                rect(x, y, 174, 27, INK, 0.80f);
                label(small, "F  探查兽巢习得灵印", x + 10, y + 20, MUTE);
            }
            return;
        }
        var seal = wilds.equippedSeal;
        Color color = denColor(seal);
        rect(x, y, 207, 46, INK, 0.90f);
        sprite(seal == Wilds.Den.STONE ? Icon.SEAL_STONE : Icon.SEAL_WIND, x + 23, y + 7, 32, true, 1, 1);
        label(body, "C  " + seal.seal, x + 46, y + 39, color);
        String status = wilds.sealCooldown > 0 ? (int) Math.ceil(wilds.sealCooldown) + " 秒" : "可用";
        right(small, status, x + 195, y + 37, PAPER);
        bar(x + 46, y + 9, 149, 4, 1 - wilds.sealCooldown / Wilds.SEAL_COOLDOWN, color);
    }

    private void drawJournal() {
        rect(SIDE_X, FIELD_Y, SIDE_W, Wilds.HEIGHT, PANEL, 1);
        frame(SIDE_X, FIELD_Y, SIDE_W, Wilds.HEIGHT, BORDER, 1);
        label(medium, "山野札记", SIDE_X + 17, 635, PAPER);
        right(small, wilds.phase.name, SIDE_X + SIDE_W - 17, 632, GOLD);
        rule(607);

        rect(SIDE_X + 15, 510, SIDE_W - 30, 83, INK, 0.69f);
        rect(SIDE_X + 16, 589, SIDE_W - 32, 1, GOLD, 0.45f);
        if (wilds.boss != null) {
            objective("击败青丘九尾", "红圈和亮线会预告攻击。", "等它露出破绽，再近身连斩。");
        } else if (wilds.phase == Wilds.Phase.DAWN) {
            objective("开启青丘祭坛", "前往东北方的青丘祭坛。", "备好三枚青玉，按 F 召唤九尾。");
        } else if (wilds.defending()) {
            objective("守住青丘祭坛", "山彘近撞，三足乌会射飞羽。", "靠近祭坛按 F，消耗山石修复。");
        } else if (wilds.phase == Wilds.Phase.SECOND_DAY) {
            objective("在祭坛旁布防", "次夜异兽会围攻东北方祭坛。", "先立镇灵灯，再留些山石修复。");
        } else if (wilds.structures(Structure.FIRE) == 0) {
            objective("在山野扎营", "按 E 采集灵木和山石。", "按 1 选篝火，左键选空地。");
        } else {
            objective("度过第一夜", "靠近篝火回血，按 Q 烤朱果。", "挥刀连斩，蓄力时翻滚出红圈。");
        }

        label(body, "行囊", SIDE_X + 17, 491, PAPER);
        inventory(Icon.LOGS, Resource.WOOD, SIDE_X + 15, 438);
        inventory(Icon.STONE, Resource.STONE, SIDE_X + 154, 438);
        inventory(Icon.BERRY, Resource.BERRY, SIDE_X + 15, 397);
        inventory(Icon.JADE, Resource.JADE, SIDE_X + 154, 397);

        label(body, "营地营造", SIDE_X + 17, 377, PAPER);
        recipe(FIRE_CARD, "1", Structure.FIRE, Icon.FIRE, "疗伤 · 烤朱果");
        recipe(WARD_CARD, "2", Structure.WARD, Icon.WARD, "范围攻击");
        rule(226);

        label(body, "修为 " + wilds.level + " 阶", SIDE_X + 17, 211, PAPER);
        right(small, wilds.level >= Wilds.MAX_LEVEL ? "已圆满" : wilds.experience + " / " + wilds.nextLevelXp(),
                SIDE_X + SIDE_W - 17, 208, MUTE);
        bar(SIDE_X + 17, 174, SIDE_W - 34, 5,
                wilds.level >= Wilds.MAX_LEVEL ? 1 : (float) wilds.experience / wilds.nextLevelXp(), JADE);
        label(small, "刀法威力  " + wilds.power(), SIDE_X + 17, 158, MUTE);
        right(small, "击退异兽  " + wilds.kills, SIDE_X + SIDE_W - 17, 158, MUTE);
        rule(129);
        String forge = wilds.tempered ? "青玉刀已淬成  ·  威力 +15" : "R  篝火旁淬刃  ·  石 2  玉 2";
        label(small, forge, SIDE_X + 17, 116, wilds.tempered || wilds.nearFire ? JADE : MUTE);
    }

    private void objective(String title, String lineOne, String lineTwo) {
        label(body, title, SIDE_X + 29, 582, GOLD);
        label(small, lineOne, SIDE_X + 29, 552, PAPER);
        label(small, lineTwo, SIDE_X + 29, 531, MUTE);
    }

    private void rule(int y) {
        rect(SIDE_X + 15, y, SIDE_W - 30, 1, BORDER, 1);
    }

    private void inventory(Icon icon, Resource kind, int x, int y) {
        rect(x, y, 131, 1, BORDER, 0.66f);
        sprite(icon, x + 18, y + 4, 25, true, 1, 1);
        label(small, kind.name, x + 37, y + 25, MUTE);
        right(body, Integer.toString(wilds.count(kind)), x + 119, y + 27, PAPER);
    }

    private void recipe(Button b, String key, Structure kind, Icon icon, String effect) {
        boolean ready = wilds.affordable(kind) && wilds.structures(kind) < kind.limit;
        rect(b.x, b.y, b.w, b.h, b.contains(mouse) ? BORDER : INK, 0.80f);
        frame(b.x, b.y, b.w, b.h, kind == placing ? GOLD : (ready ? JADE : BORDER), 0.86f);
        keycap(key, b.x + 9, b.y + 16, 25);
        sprite(icon, b.x + 55, b.y + 9, 35, true, 1, 1);
        label(body, kind.name, b.x + 80, b.y + 44, PAPER);
        label(small, kind.cost(), b.x + 81, b.y + 20, ready ? JADE : MUTE);
        right(small, effect, b.x + b.w - 8, b.y + 41, MUTE);
    }

    private void drawControls() {
        rect(24, 20, 1232, 51, PANEL, 1);
        frame(24, 20, 1232, 51, BORDER, 1);
        control("WASD", "移动", 42, 58);
        control("左键 / SPACE", "挥刀", 169, 111);
        control("右键 / SHIFT", "翻滚", 345, 111);
        control("E", "采集", 518, 28);
        control("Q", "进食", 612, 28);
        control("1 / 2", "建造", 704, 52);
        control("R", "淬刃", 820, 27);
        control("F", "互动", 916, 27);
        control("ESC", "暂停", 1012, 44);
        control("C / TAB", "灵印", 1128, 62);
    }

    private void control(String key, String action, int x, int width) {
        keycap(key, x, 33, width);
        label(small, action, x + width + 11, 52, PAPER);
    }

    private void keycap(String key, int x, int y, int width) {
        rect(x, y, width, 25, INK, 1);
        frame(x, y, width, 25, BORDER, 1);
        center(small, key, x + width / 2f, y + 19, GOLD);
    }

    private void drawModal() {
        rect(0, 0, WIDTH, HEIGHT, INK, 0.74f);
        rect(394, 177, 492, 441, PANEL, 1);
        frame(394, 177, 492, 441, GOLD, 0.85f);
        frame(402, 185, 476, 425, BORDER, 1);
        corners(394, 177, 492, 441, GOLD);
        boolean won = wilds.result == Wilds.Result.WON;
        Icon icon = paused ? Icon.FIRE : (won ? Icon.ALTAR : Icon.FOX);
        halo(640, 535, 96, won ? JADE : GOLD, 0.55f);
        sprite(icon, 640, 497, 81, true, 1, 1);
        center(large, paused ? "暂歇片刻" : (won ? "青丘重明" : "大荒长夜"), 640, 468, PAPER);
        String detail = paused ? "山风停在此刻。" : (won ? "九尾已伏，山野迎来长明。"
                : (wilds.shrine <= 0 ? "祭坛被异兽攻破了。" : "守夜人倒在了青丘的山风里。"));
        center(body, detail, 640, 395, MUTE);
        String stats = "修为 " + wilds.level + " 阶    ·    击退 " + wilds.kills + " 只异兽";
        center(small, stats, 640, 360, GOLD);
        button(PRIMARY, paused ? "继续守夜  ENTER" : "再次守夜  ENTER", true);
        button(HOME, "返回封面", false);
    }

    private void sprite(Icon icon, float centerX, float bottom, float size, boolean right, float light, float alpha) {
        float scale = size / Math.max(icon.w, icon.h);
        float width = icon.w * scale;
        batch.setColor(light, light, light, alpha);
        batch.draw(right ? icon.front : icon.reverse, centerX - width / 2, bottom, width, icon.h * scale);
        batch.setColor(WHITE);
    }

    private void image(Texture texture, float x, float y, float w, float h, Color tint, float alpha) {
        batch.setColor(tint.r, tint.g, tint.b, alpha);
        batch.draw(texture, x, y, w, h);
        batch.setColor(WHITE);
    }

    private void rotated(Texture texture, float x, float y, float w, float h, float angle,
                         Color tint, float alpha) {
        batch.setColor(tint.r, tint.g, tint.b, alpha);
        batch.draw(texture, x - w / 2, y - h / 2, w / 2, h / 2, w, h, 1, 1, angle,
                0, 0, texture.getWidth(), texture.getHeight(), false, false);
        batch.setColor(WHITE);
    }

    private void line(float x1, float y1, float x2, float y2, float thickness, Color tint, float alpha) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        rotated(pixel, (x1 + x2) / 2, (y1 + y2) / 2, (float) Math.hypot(dx, dy), thickness,
                (float) Math.toDegrees(Math.atan2(dy, dx)), tint, alpha);
    }

    private void rect(float x, float y, float w, float h, Color tint, float alpha) {
        image(pixel, x, y, w, h, tint, alpha);
    }

    private void circle(float x, float y, float radius, Color tint, float opacity) {
        image(ring, x - radius, y - radius, radius * 2, radius * 2, tint, opacity);
    }

    private void halo(float x, float y, float radius, Color tint, float opacity) {
        image(glow, x - radius, y - radius, radius * 2, radius * 2, tint, opacity);
    }

    private void bar(float x, float y, float w, float h, float progress, Color color) {
        rect(x, y, w, h, BORDER, 0.75f);
        if (progress > 0) {
            rect(x, y, w * Math.min(progress, 1), h, color, 1);
        }
    }

    private void frame(float x, float y, float w, float h, Color tint, float alpha) {
        rect(x, y, w, 1, tint, alpha);
        rect(x, y + h - 1, w, 1, tint, alpha);
        rect(x, y, 1, h, tint, alpha);
        rect(x + w - 1, y, 1, h, tint, alpha);
    }

    private void corners(float x, float y, float w, float h, Color tint) {
        int len = 16;
        for (float cx : new float[]{x, x + w - len}) {
            rect(cx, y, len, 2, tint, 0.85f);
            rect(cx, y + h - 2, len, 2, tint, 0.85f);
        }
        for (float cx : new float[]{x, x + w - 2}) {
            rect(cx, y, 2, len, tint, 0.85f);
            rect(cx, y + h - len, 2, len, tint, 0.85f);
        }
    }

    private void label(BitmapFont font, String value, float x, float top, Color color) {
        font.setColor(color);
        font.draw(batch, value, x, top);
    }

    private void center(BitmapFont font, String value, float centerX, float top, Color color) {
        layout.setText(font, value);
        label(font, value, centerX - layout.width / 2, top, color);
    }

    private void right(BitmapFont font, String value, float rightX, float top, Color color) {
        layout.setText(font, value);
        label(font, value, rightX - layout.width, top, color);
    }

    private void tag(String value, float centerX, float top, Color color) {
        layout.setText(small, value);
        float left = centerX - layout.width / 2;
        rect(left - 8, top - 21, layout.width + 16, 25, INK, 0.86f);
        label(small, value, left, top, color);
    }

    private void button(Button b, String text, boolean primary) {
        boolean hover = b.contains(mouse);
        Color background = primary ? (hover ? RED : GOLD) : (hover ? BORDER : INK);
        rect(b.x, b.y, b.w, b.h, background, 1);
        frame(b.x, b.y, b.w, b.h, primary ? PAPER : BORDER, 0.71f);
        center(primary ? body : small, text, b.x + b.w / 2f,
                b.y + b.h / 2f + (primary ? 10 : 8), primary ? INK : PAPER);
    }

    private float wave(float speed) {
        return (float) Math.sin(clock * speed);
    }

    @Override
    public void dispose() {
        batch.dispose();
        for (var font : new BitmapFont[]{small, body, medium, large, headline}) {
            font.dispose();
        }
        for (var texture : new Texture[]{title, valley, atlas, relics, wandererMotion, foxMotion, beastsMotion,
                pixel, glow, ring, shade, vignette, slashArc}) {
            texture.dispose();
        }
        for (Sound sound : sounds.values()) {
            sound.dispose();
        }
    }
}
