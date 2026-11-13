package shanhai;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Random;

/** The simulation uses field coordinates and has no dependency on the desktop window. */
final class Wilds {
    static final int WIDTH = 914;
    static final int HEIGHT = 562;
    static final float ALTAR_X = 749;
    static final float ALTAR_Y = 394;
    static final float HARVEST_RANGE = 54;
    static final float ALTAR_RANGE = 58;
    static final float BUILD_RANGE = 145;
    static final float WARD_RANGE = 106;
    static final float SNARE_RANGE = 54;
    static final float SNARE_RECHARGE = 5f;
    static final float SNARE_FLASH = 0.42f;
    static final float COUNTER_TIME = 2f;
    static final float DEN_RANGE = 61;
    static final float SEAL_RANGE = 123;
    static final float SEAL_COOLDOWN = 12f;
    static final float SEAL_FLASH = 0.66f;
    static final float POUNCE_TIME = 0.20f;
    static final float SHRINE_MAX = 140;
    static final int REPAIR_STONE = 2;
    static final int TEMPER_STONE = 2;
    static final int TEMPER_JADE = 2;
    static final int OFFERING = 3;
    static final int MAX_LEVEL = 5;
    static final float DODGE_COOLDOWN = 0.98f;
    static final float DODGE_DURATION = 0.19f;
    static final float SLASH_TIME = 0.22f;
    static final float SLASH_WINDUP = 0.055f;
    private static final float INPUT_BUFFER = 0.15f;
    private static final float ATTACK_RANGE = 69;
    private static final float PERFECT_DODGE_WINDOW = 0.12f;
    private static final float SNARE_SLOW_TIME = 2.3f;

    enum Resource {
        WOOD("灵木", 2, 25), STONE("山石", 2, 25), BERRY("朱果", 2, 32), JADE("青玉", 1, 37);

        final String name;
        final int yield;
        final float regrowth;

        Resource(String name, int yield, float regrowth) {
            this.name = name;
            this.yield = yield;
            this.regrowth = regrowth;
        }
    }

    enum Structure {
        FIRE("篝火", 4, 2, 0, 3), WARD("镇灵灯", 2, 4, 1, 4), SNARE("绊灵桩", 3, 2, 0, 4);

        final String name;
        final int wood;
        final int stone;
        final int jade;
        final int limit;

        Structure(String name, int wood, int stone, int jade, int limit) {
            this.name = name;
            this.wood = wood;
            this.stone = stone;
            this.jade = jade;
            this.limit = limit;
        }

        String cost() {
            return "木 " + wood + "   石 " + stone + (jade > 0 ? "   玉 " + jade : "");
        }
    }

    enum Creature {
        BOAR("山彘", 52, 79, 41, 14, 17),
        CROW("三足乌", 43, 90, 158, 11, 21),
        FOX("青丘九尾", 865, 112, 88, 24, 100);

        final String name;
        final int health;
        final float speed;
        final float reach;
        final int damage;
        final int experience;

        Creature(String name, int health, float speed, float reach, int damage, int experience) {
            this.name = name;
            this.health = health;
            this.speed = speed;
            this.reach = reach;
            this.damage = damage;
            this.experience = experience;
        }
    }

    enum Phase {
        FIRST_DAY("初日", 50, false), FIRST_NIGHT("初夜", 89, true),
        SECOND_DAY("次日", 134, false), SECOND_NIGHT("次夜", 176, true),
        DAWN("破晓", Float.POSITIVE_INFINITY, false);

        final String name;
        final float end;
        final boolean night;

        Phase(String name, float end, boolean night) {
            this.name = name;
            this.end = end;
            this.night = night;
        }
    }

    enum Result { ALIVE, LOST, WON }

    enum Cue { HARVEST, BUILD, EAT, SWING, HURT, LEVEL, GONG }

    enum Strike { LUNGE, BOLT, POUNCE, FAN }

    enum Den {
        STONE("山彘巢", "镇山印", 205, 110, Creature.BOAR),
        WIND("三足乌巢", "御风印", 354, 417, Creature.CROW);

        final String name;
        final String seal;
        final float x;
        final float y;
        final Creature guardian;

        Den(String name, String seal, float x, float y, Creature guardian) {
            this.name = name;
            this.seal = seal;
            this.x = x;
            this.y = y;
            this.guardian = guardian;
        }
    }

    static final class Node {
        final Resource kind;
        final float x;
        final float y;
        float regrow;

        Node(Resource kind, float x, float y) {
            this.kind = kind;
            this.x = x;
            this.y = y;
        }
    }

    static final class Building {
        final Structure kind;
        final float x;
        final float y;
        float pulse;

        Building(Structure kind, float x, float y) {
            this.kind = kind;
            this.x = x;
            this.y = y;
        }
    }

    static final class Enemy {
        final Creature kind;
        final float maxHealth;
        float health;
        float x;
        float y;
        float cooldown = 0.35f;
        float windup;
        float windupLength;
        float gait;
        float leapTime;
        float targetX;
        float targetY;
        float hitFlash;
        float strikeFlash;
        float snared;
        Strike strike = Strike.LUNGE;
        boolean attacksShrine;
        int attacks;
        boolean facesRight;
        boolean moving;
        Den den;

        Enemy(Creature kind, float x, float y, float strength) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            maxHealth = kind.health * strength;
            health = maxHealth;
        }

        boolean boss() {
            return kind == Creature.FOX;
        }

        boolean enraged() {
            return boss() && health <= maxHealth * 0.48f;
        }

        boolean exposed() {
            return boss() && attacks > 0 && windup <= 0 && leapTime <= 0 && cooldown > 0;
        }

        float dangerRadius() {
            return strike == Strike.POUNCE ? (enraged() ? 76 : 62) : kind.reach + 4;
        }
    }

    static final class Bolt {
        float x;
        float y;
        final float vx;
        final float vy;
        final int damage;
        final boolean targetsShrine;
        final boolean foxFire;
        float life = 2f;

        Bolt(float x, float y, float vx, float vy, int damage, boolean targetsShrine, boolean foxFire) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.damage = damage;
            this.targetsShrine = targetsShrine;
            this.foxFire = foxFire;
        }
    }

    static final class Feedback {
        final String text;
        final float x;
        final float y;
        final boolean damage;
        float life = 0.85f;

        Feedback(String text, float x, float y, boolean damage) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.damage = damage;
        }
    }

    private final Random random;
    private final int[] bag = new int[Resource.values().length];
    final ArrayList<Node> nodes = new ArrayList<>();
    final ArrayList<Building> buildings = new ArrayList<>();
    final ArrayList<Enemy> enemies = new ArrayList<>();
    final ArrayList<Bolt> bolts = new ArrayList<>();
    final ArrayList<Feedback> feedback = new ArrayList<>();
    final EnumSet<Cue> cues = EnumSet.noneOf(Cue.class);
    final EnumSet<Den> clearedDens = EnumSet.noneOf(Den.class);

    Phase phase = Phase.FIRST_DAY;
    Result result = Result.ALIVE;
    Enemy boss;
    Den activeDen;
    Den equippedSeal;
    Den lastSeal;
    float sealCooldown;
    float sealFlash;
    float stoneProtection;
    float sealX;
    float sealY;
    float elapsed;
    float phaseFlash = 4;
    float spawnTimer;
    float x = 456;
    float y = 235;
    float dx;
    float dy = 1;
    float vx;
    float vy;
    float gait;
    float health = 100;
    float fullness = 100;
    float shrine = SHRINE_MAX;
    float shrineFlash;
    float attackCooldown;
    float slash;
    float slashAngle;
    private float impactDelay;
    int combo;
    float comboTime;
    private float attackBuffer;
    private float attackX;
    private float attackY;
    private boolean mouseAttack;
    private float dodgeBuffer;
    float dodgeCooldown;
    float dodgeTime;
    float dodgeX;
    float dodgeY;
    private float dodgeStartX;
    private float dodgeStartY;
    private float perfectDodgeWindow;
    float counterTime;
    boolean counterSlash;
    float invulnerable;
    float hurtFlash;
    float messageTime;
    String message = "";
    boolean facesRight = true;
    boolean moving;
    boolean nearFire;
    boolean tempered;
    private boolean reinforcements;
    int experience;
    int level = 1;
    int kills;

    Wilds(long seed) {
        random = new Random(seed);
        putNodes(Resource.WOOD, 355, 245, 433, 313, 170, 144, 115, 220, 214, 315,
                122, 414, 275, 424, 425, 456, 574, 413, 675, 312, 809, 244, 750, 134, 610, 128);
        putNodes(Resource.STONE, 500, 188, 553, 262, 209, 196, 304, 139, 327, 351,
                575, 346, 655, 168, 811, 332, 598, 461, 172, 369);
        putNodes(Resource.BERRY, 518, 306, 401, 163, 262, 251, 186, 294, 333, 455,
                479, 395, 643, 254, 737, 287, 806, 178, 118, 326);
        putNodes(Resource.JADE, 633, 390, 730, 217, 285, 353, 792, 414, 206, 421,
                673, 453, 695, 124);
        enemies.add(new Enemy(Creature.BOAR, 728, 175, 1));
        enemies.add(new Enemy(Creature.BOAR, 201, 355, 1));
    }

    private void putNodes(Resource kind, int... positions) {
        for (int i = 0; i < positions.length; i += 2) {
            nodes.add(new Node(kind, positions[i], positions[i + 1]));
        }
    }

    int count(Resource kind) {
        return bag[kind.ordinal()];
    }

    int structures(Structure kind) {
        int count = 0;
        for (Building building : buildings) {
            if (building.kind == kind) {
                count++;
            }
        }
        return count;
    }

    boolean affordable(Structure kind) {
        return count(Resource.WOOD) >= kind.wood && count(Resource.STONE) >= kind.stone
                && count(Resource.JADE) >= kind.jade;
    }

    int maxHealth() {
        return 100 + (level - 1) * 15;
    }

    int power() {
        return 23 + (level - 1) * 7 + (tempered ? 15 : 0);
    }

    int counterBonus() {
        return Math.round(power() * 0.50f);
    }

    int nextLevelXp() {
        return 31 + (level - 1) * 29;
    }

    boolean atAltar() {
        return within(x, y, ALTAR_X, ALTAR_Y, ALTAR_RANGE);
    }

    boolean defending() {
        return phase == Phase.SECOND_NIGHT;
    }

    Node nearestNode() {
        Node nearest = null;
        float distance = HARVEST_RANGE * HARVEST_RANGE;
        for (Node node : nodes) {
            float candidate = distanceSquared(x, y, node.x, node.y);
            if (node.regrow <= 0 && candidate < distance) {
                nearest = node;
                distance = candidate;
            }
        }
        return nearest;
    }

    void harvest(boolean showMissing) {
        Node node = nearestNode();
        if (node == null) {
            if (showMissing) {
                tell("靠近资源后，按住 E 采集");
            }
            return;
        }
        bag[node.kind.ordinal()] += node.kind.yield;
        node.regrow = node.kind.regrowth;
        feedback.add(new Feedback("+" + node.kind.yield + " " + node.kind.name, node.x, node.y + 30, false));
        cues.add(Cue.HARVEST);
    }

    void build(Structure kind, float buildX, float buildY) {
        if (structures(kind) >= kind.limit) {
            tell(kind.name + "已达建造上限");
            return;
        }
        if (!affordable(kind)) {
            tell("材料不足  ·  " + kind.cost());
            return;
        }
        if (!canBuildAt(buildX, buildY)) {
            tell("选择身边的空地建造");
            return;
        }
        bag[Resource.WOOD.ordinal()] -= kind.wood;
        bag[Resource.STONE.ordinal()] -= kind.stone;
        bag[Resource.JADE.ordinal()] -= kind.jade;
        buildings.add(new Building(kind, buildX, buildY));
        cues.add(Cue.BUILD);
        tell(switch (kind) {
            case FIRE -> "篝火已点燃  ·  靠近可疗伤、烤熟朱果";
            case WARD -> "镇灵灯已立起  ·  自动攻击附近异兽";
            case SNARE -> "绊灵桩已设好  ·  山彘经过时会被减速";
        });
    }

    boolean canBuildAt(float buildX, float buildY) {
        if (buildX < 67 || buildX > WIDTH - 67 || buildY < 74 || buildY > HEIGHT - 79
                || !within(x, y, buildX, buildY, BUILD_RANGE)
                || within(buildX, buildY, ALTAR_X, ALTAR_Y, 42)
                || within(buildX, buildY, x, y, 20)) {
            return false;
        }
        for (Node node : nodes) {
            if (within(buildX, buildY, node.x, node.y, 24)) {
                return false;
            }
        }
        for (Building building : buildings) {
            if (within(buildX, buildY, building.x, building.y, 35)) {
                return false;
            }
        }
        for (Den den : Den.values()) {
            if (within(buildX, buildY, den.x, den.y, 38)) {
                return false;
            }
        }
        return true;
    }

    void temper() {
        if (tempered) {
            tell("青玉刀已经淬成");
        } else if (!besideFire()) {
            tell("靠近篝火后按 R 淬刃");
        } else if (count(Resource.STONE) < TEMPER_STONE || count(Resource.JADE) < TEMPER_JADE) {
            tell("淬刃需要山石 " + TEMPER_STONE + "、青玉 " + TEMPER_JADE);
        } else {
            bag[Resource.STONE.ordinal()] -= TEMPER_STONE;
            bag[Resource.JADE.ordinal()] -= TEMPER_JADE;
            tempered = true;
            tell("青玉刀已淬成  ·  刀法威力 +15");
            cues.add(Cue.LEVEL);
        }
    }

    Den nearbyDen() {
        for (Den den : Den.values()) {
            if (within(x, y, den.x, den.y, DEN_RANGE)) {
                return den;
            }
        }
        return null;
    }

    void interact() {
        if (atAltar()) {
            invokeAltar();
        } else {
            Den den = nearbyDen();
            if (den == null) {
                tell("靠近兽巢或青丘祭坛后按 F");
            } else {
                enterDen(den);
            }
        }
    }

    private void enterDen(Den den) {
        if (clearedDens.contains(den)) {
            tell(den.name + "已经平息");
        } else if (activeDen != null) {
            tell("先击败已经引出的巢穴守兽");
        } else if (boss != null) {
            tell("九尾现身后无法再探巢");
        } else if (phase.night) {
            tell("白天再探查兽巢");
        } else {
            activeDen = den;
            var guardian = new Enemy(den.guardian, den.x + 31, den.y - 23, den == Den.STONE ? 3.8f : 3.15f);
            guardian.den = den;
            enemies.add(guardian);
            tell(den.name + "守兽现身  ·  击败它可习得" + den.seal);
            cues.add(Cue.GONG);
        }
    }

    void switchSeal() {
        if (clearedDens.size() < 2) {
            tell(equippedSeal == null ? "白天探查兽巢，击败守兽可解锁灵印" : "另一枚灵印还在兽巢中");
            return;
        }
        equippedSeal = equippedSeal == Den.STONE ? Den.WIND : Den.STONE;
        tell("已切换至" + equippedSeal.seal);
    }

    void castSeal() {
        if (equippedSeal == null) {
            tell("先去兽巢习得灵印");
            return;
        }
        if (sealCooldown > 0) {
            tell("灵印尚在恢复");
            return;
        }
        sealCooldown = SEAL_COOLDOWN;
        sealFlash = SEAL_FLASH;
        sealX = x;
        sealY = y;
        lastSeal = equippedSeal;
        cues.add(Cue.BUILD);
        if (lastSeal == Den.STONE) {
            stoneProtection = 4.0f;
            tell("镇山印  ·  阵中人物和祭坛受到的伤害降低");
        } else {
            stoneProtection = 0;
            for (Enemy enemy : enemies) {
                if (enemy.health > 0 && within(x, y, enemy.x, enemy.y, SEAL_RANGE)) {
                    strike(enemy, 39, 73);
                }
            }
            bolts.removeIf(bolt -> within(x, y, bolt.x, bolt.y, SEAL_RANGE));
            removeDefeated();
            tell("御风印  ·  击退周围异兽，吹散飞羽和狐火");
        }
    }

    void eat() {
        if (count(Resource.BERRY) == 0) {
            tell("没有朱果  ·  靠近朱果丛按 E 采集");
            return;
        }
        boolean cooked = besideFire();
        if (fullness >= 99 && (!cooked || health >= maxHealth() - 1)) {
            tell("你现在很饱");
            return;
        }
        bag[Resource.BERRY.ordinal()]--;
        fullness = Math.min(100, fullness + (cooked ? 43 : 27));
        if (cooked) {
            health = Math.min(maxHealth(), health + 13);
        }
        cues.add(Cue.EAT);
        tell(cooked ? "吃下烤朱果  ·  饱食 +43，气血 +13" : "吃下朱果  ·  饱食 +27");
    }

    void invokeAltar() {
        if (!atAltar()) {
            tell("走到东北方的青丘祭坛，再按 F");
        } else if (defending()) {
            repairAltar();
        } else if (boss != null) {
            tell("击败九尾，封住青丘地脉");
        } else if (phase != Phase.DAWN) {
            tell("祭坛要到第二夜后的破晓才会苏醒");
        } else if (count(Resource.JADE) < OFFERING) {
            tell("还缺 " + (OFFERING - count(Resource.JADE)) + " 枚青玉");
        } else {
            bag[Resource.JADE.ordinal()] -= OFFERING;
            boss = new Enemy(Creature.FOX, ALTAR_X - 33, ALTAR_Y + 40, 1);
            enemies.add(boss);
            tell("青丘九尾现身  ·  扑击落地或施法后会露出破绽");
            cues.add(Cue.GONG);
        }
    }

    private void repairAltar() {
        if (shrine >= SHRINE_MAX) {
            tell("祭坛完好");
        } else if (count(Resource.STONE) < REPAIR_STONE) {
            tell("修复祭坛需要山石 " + REPAIR_STONE);
        } else {
            shrine = Math.min(SHRINE_MAX, shrine + 38);
            bag[Resource.STONE.ordinal()] -= REPAIR_STONE;
            feedback.add(new Feedback("+38", ALTAR_X, ALTAR_Y + 77, false));
            cues.add(Cue.BUILD);
        }
    }

    void requestAttack(boolean aimed, float targetX, float targetY) {
        attackBuffer = INPUT_BUFFER;
        mouseAttack = aimed;
        attackX = targetX;
        attackY = targetY;
    }

    private void attack() {
        attackBuffer = 0;
        combo = comboTime > 0 && combo < 3 ? combo + 1 : 1;
        comboTime = 0.71f;
        boolean finisher = combo == 3;
        attackCooldown = finisher ? 0.43f : 0.25f;
        slash = SLASH_TIME;
        counterSlash = false;
        float aimX = dx;
        float aimY = dy;
        if (mouseAttack && !within(x, y, attackX, attackY, 4)) {
            aimX = attackX - x;
            aimY = attackY - y;
        } else if (!mouseAttack) {
            Enemy closest = nearestEnemy(ATTACK_RANGE + 36);
            if (closest != null) {
                aimX = closest.x - x;
                aimY = closest.y - y;
            }
        }
        slashAngle = (float) Math.toDegrees(Math.atan2(aimY, aimX));
        if (Math.abs(aimX) > 0.05f) {
            facesRight = aimX > 0;
        }
        impactDelay = SLASH_WINDUP;
    }

    private void landAttack() {
        double angle = Math.toRadians(slashAngle);
        float aimX = (float) Math.cos(angle);
        float aimY = (float) Math.sin(angle);
        boolean finisher = combo == 3;
        int damage = finisher ? Math.round(power() * 1.60f) : power();
        boolean counter = counterTime > 0;
        if (counter) {
            damage += counterBonus();
        }
        cues.add(Cue.SWING);
        for (Enemy enemy : enemies) {
            float range = ATTACK_RANGE + (finisher ? 15 : 0) + (enemy.boss() ? 14 : 0);
            if (enemy.health > 0 && within(x, y, enemy.x, enemy.y, range)) {
                float dot = (enemy.x - x) * aimX + (enemy.y - y) * aimY;
                float distance = (float) Math.hypot(enemy.x - x, enemy.y - y);
                if (distance < 13 || dot >= (finisher ? -0.34f : 0.20f) * distance) {
                    strike(enemy, damage, finisher ? 24 : 9);
                    counterSlash = counter;
                }
            }
        }
        if (counterSlash) {
            counterTime = 0;
        }
        removeDefeated();
    }

    private Enemy nearestEnemy(float range) {
        Enemy closest = null;
        float best = range * range;
        for (Enemy enemy : enemies) {
            float distance = distanceSquared(x, y, enemy.x, enemy.y);
            if (enemy.health > 0 && distance < best) {
                best = distance;
                closest = enemy;
            }
        }
        return closest;
    }

    void requestDodge() {
        dodgeBuffer = INPUT_BUFFER;
    }

    private void dodge(float moveX, float moveY) {
        dodgeBuffer = 0;
        float length = (float) Math.hypot(moveX, moveY);
        dodgeX = length > 0 ? moveX / length : dx;
        dodgeY = length > 0 ? moveY / length : dy;
        dodgeTime = DODGE_DURATION;
        perfectDodgeWindow = invulnerable <= 0 ? PERFECT_DODGE_WINDOW : 0;
        dodgeStartX = x;
        dodgeStartY = y;
        dodgeCooldown = DODGE_COOLDOWN;
        invulnerable = Math.max(invulnerable, 0.26f);
        attackCooldown = Math.min(attackCooldown, 0.13f);
        slash = 0;
        impactDelay = 0;
    }

    void step(float dt, float moveX, float moveY) {
        if (result != Result.ALIVE) {
            return;
        }
        elapsed += dt;
        advancePhase();
        phaseFlash = down(phaseFlash, dt);
        messageTime = down(messageTime, dt);
        attackCooldown = down(attackCooldown, dt);
        slash = down(slash, dt);
        dodgeCooldown = down(dodgeCooldown, dt);
        counterTime = down(counterTime, dt);
        invulnerable = down(invulnerable, dt);
        hurtFlash = down(hurtFlash, dt);
        shrineFlash = down(shrineFlash, dt);
        comboTime = down(comboTime, dt);
        sealCooldown = down(sealCooldown, dt);
        sealFlash = down(sealFlash, dt);
        stoneProtection = down(stoneProtection, dt);
        if (dodgeBuffer > 0 && dodgeCooldown <= 0) {
            dodge(moveX, moveY);
        }
        dodgeBuffer = down(dodgeBuffer, dt);
        updatePlayer(dt, moveX, moveY);
        if (impactDelay > 0) {
            impactDelay = down(impactDelay, dt);
            if (impactDelay <= 0) {
                landAttack();
            }
        }
        if (attackBuffer > 0 && attackCooldown <= 0 && dodgeTime <= 0) {
            attack();
        }
        attackBuffer = down(attackBuffer, dt);
        if (result != Result.ALIVE) {
            return;
        }
        updateSpawns(dt);
        for (Node node : nodes) {
            node.regrow = down(node.regrow, dt);
        }
        updateBuildings(dt);
        for (Enemy enemy : enemies) {
            if (enemy.health > 0) {
                updateEnemy(enemy, dt);
            }
        }
        updateBolts(dt);
        perfectDodgeWindow = down(perfectDodgeWindow, dt);
        if (boss != null && boss.health > 0 && boss.enraged() && !reinforcements) {
            reinforcements = true;
            enemies.add(new Enemy(Creature.CROW, 805, 292, 1));
            enemies.add(new Enemy(Creature.CROW, 660, 454, 1));
            tell("九尾引来三足乌  ·  先避开飞羽");
            cues.add(Cue.GONG);
        }
        removeDefeated();
        for (Feedback item : feedback) {
            item.life -= dt;
        }
        feedback.removeIf(item -> item.life <= 0);
        if ((health <= 0 || shrine <= 0) && result == Result.ALIVE) {
            result = Result.LOST;
            cues.add(Cue.GONG);
        }
    }

    private void advancePhase() {
        if (elapsed < phase.end) {
            return;
        }
        phase = Phase.values()[phase.ordinal() + 1];
        phaseFlash = 4;
        spawnTimer = phase.night ? 1.0f : 9.0f;
        cues.add(Cue.GONG);
        if (phase == Phase.DAWN) {
            tell("两夜已过  ·  带三枚青玉前往青丘祭坛");
        } else if (phase == Phase.SECOND_DAY) {
            tell("次夜异兽会围攻祭坛  ·  趁白天在东北方布防");
        } else if (defending()) {
            tell("守住祭坛  ·  靠近后按 F 可消耗山石修复");
        }
    }

    private void updatePlayer(float dt, float moveX, float moveY) {
        float length = (float) Math.hypot(moveX, moveY);
        if (length > 0 && dodgeTime <= 0) {
            dx = moveX / length;
            dy = moveY / length;
        }
        if (dodgeTime > 0) {
            dx = dodgeX;
            dy = dodgeY;
            float speed = 530 * (0.74f + dodgeTime / 0.73f);
            vx = dx * speed;
            vy = dy * speed;
        } else {
            float targetX = length > 0 ? dx * 188 : 0;
            float targetY = length > 0 ? dy * 188 : 0;
            float change = (length > 0 ? 5750 : 7600) * dt;
            vx += clamp(targetX - vx, -change, change);
            vy += clamp(targetY - vy, -change, change);
        }
        if (Math.abs(dx) > 0.05f && slash <= 0) {
            facesRight = dx > 0;
        }
        x = clamp(x + vx * dt, 59, WIDTH - 57);
        y = clamp(y + vy * dt, 65, HEIGHT - 76);
        moving = Math.abs(vx) + Math.abs(vy) > 2;
        gait += (float) Math.hypot(vx, vy) * dt;
        dodgeTime = down(dodgeTime, dt);

        fullness = down(fullness, dt * (phase.night ? 0.68f : 0.48f));
        nearFire = besideFire();
        if (fullness <= 0) {
            health = down(health, 4.0f * dt);
        } else if (nearFire && hurtFlash <= 0 && fullness > 20) {
            health = Math.min(maxHealth(), health + dt * (phase.night ? 2.1f : 3.2f));
        }
    }

    private boolean besideFire() {
        for (Building building : buildings) {
            if (building.kind == Structure.FIRE && within(x, y, building.x, building.y, 72)) {
                return true;
            }
        }
        return false;
    }

    private void updateSpawns(float dt) {
        spawnTimer -= dt;
        if (spawnTimer > 0 || boss != null) {
            return;
        }
        if (phase.night) {
            boolean secondNight = phase == Phase.SECOND_NIGHT;
            if (enemies.size() < (secondNight ? 9 : 6)) {
                Creature kind = secondNight && random.nextFloat() < 0.49f ? Creature.CROW : Creature.BOAR;
                spawn(kind, secondNight ? 1.16f : 1);
            }
            spawnTimer = secondNight ? 3.3f : 5.4f;
        } else if (phase != Phase.FIRST_DAY) {
            if (enemies.size() < 2) {
                spawn(Creature.BOAR, 1);
            }
            spawnTimer = 12;
        }
    }

    private void spawn(Creature kind, float strength) {
        float sx;
        float sy;
        if (defending() && random.nextBoolean()) {
            sx = WIDTH - 66;
            sy = 120 + random.nextFloat() * (HEIGHT - 215);
        } else if (random.nextBoolean()) {
            sx = x > WIDTH / 2f ? 67 : WIDTH - 67;
            sy = 88 + random.nextFloat() * (HEIGHT - 178);
        } else {
            sx = 85 + random.nextFloat() * (WIDTH - 170);
            sy = y > HEIGHT / 2f ? 76 : HEIGHT - 81;
        }
        enemies.add(new Enemy(kind, sx, sy, strength));
    }

    private void updateBuildings(float dt) {
        for (Building building : buildings) {
            building.pulse = down(building.pulse, dt);
            if (building.kind == Structure.FIRE || building.pulse > 0) {
                continue;
            }
            if (building.kind == Structure.SNARE) {
                triggerSnare(building);
                continue;
            }
            for (Enemy enemy : enemies) {
                if (enemy.health > 0 && within(building.x, building.y, enemy.x, enemy.y, WARD_RANGE)) {
                    strike(enemy, enemy.boss() ? 8 : 13, 0);
                    building.pulse = 0.9f;
                }
            }
        }
    }

    private void triggerSnare(Building building) {
        boolean caught = false;
        for (Enemy enemy : enemies) {
            if (enemy.kind == Creature.BOAR && enemy.health > 0 && enemy.snared <= 0
                    && within(building.x, building.y, enemy.x, enemy.y, SNARE_RANGE)) {
                enemy.snared = SNARE_SLOW_TIME;
                caught = true;
            }
        }
        if (caught) {
            building.pulse = SNARE_RECHARGE;
            cues.add(Cue.BUILD);
        }
    }

    private void updateEnemy(Enemy enemy, float dt) {
        enemy.moving = false;
        enemy.cooldown = down(enemy.cooldown, dt);
        enemy.hitFlash = down(enemy.hitFlash, dt);
        enemy.strikeFlash = down(enemy.strikeFlash, dt);
        enemy.snared = down(enemy.snared, dt);

        if (enemy.leapTime > 0) {
            float part = Math.min(1, dt / enemy.leapTime);
            enemy.x += (enemy.targetX - enemy.x) * part;
            enemy.y += (enemy.targetY - enemy.y) * part;
            enemy.leapTime = down(enemy.leapTime, dt);
            if (enemy.leapTime <= 0) {
                finishStrike(enemy);
            }
            return;
        }
        if (enemy.windup > 0) {
            enemy.windup = down(enemy.windup, dt);
            if (enemy.windup <= 0) {
                if (enemy.strike == Strike.POUNCE) {
                    enemy.leapTime = POUNCE_TIME;
                } else {
                    finishStrike(enemy);
                }
            }
            return;
        }

        float playerDistance = (float) Math.hypot(x - enemy.x, y - enemy.y);
        boolean siege = defending() && !enemy.boss() && enemy.den == null
                && playerDistance > (enemy.kind == Creature.CROW ? 116 : 100);
        float targetX = siege ? ALTAR_X : x;
        float targetY = siege ? ALTAR_Y : y;
        float offsetX = targetX - enemy.x;
        float offsetY = targetY - enemy.y;
        float distance = (float) Math.hypot(offsetX, offsetY);
        if (!phase.night && !enemy.boss() && enemy.den == null && boss == null && distance > 156) {
            return;
        }
        if (Math.abs(offsetX) > 1) {
            enemy.facesRight = offsetX > 0;
        }
        Strike nextStrike = enemy.kind == Creature.CROW ? Strike.BOLT : Strike.LUNGE;
        if (enemy.boss()) {
            int interval = enemy.enraged() ? 2 : 3;
            nextStrike = enemy.attacks % interval == interval - 1 ? Strike.FAN : Strike.POUNCE;
        }
        float triggerRange = nextStrike == Strike.FAN ? 213 : enemy.kind.reach;
        if (distance <= triggerRange && enemy.cooldown <= 0) {
            enemy.strike = nextStrike;
            enemy.windupLength = switch (nextStrike) {
                case LUNGE -> 0.46f;
                case BOLT -> 0.62f;
                case POUNCE -> enemy.enraged() ? 0.56f : 0.72f;
                case FAN -> enemy.enraged() ? 0.63f : 0.81f;
            };
            enemy.windup = enemy.windupLength;
            enemy.targetX = targetX;
            enemy.targetY = nextStrike == Strike.POUNCE ? Math.min(targetY, HEIGHT - 126) : targetY;
            enemy.attacksShrine = siege;
            return;
        }
        float stop = switch (enemy.kind) {
            case BOAR -> 30;
            case CROW -> 112;
            case FOX -> 58;
        };
        if (distance > stop) {
            float speed = enemy.kind.speed * (enemy.enraged() || enemy.den != null ? 1.21f : 1);
            if (enemy.snared > 0) {
                speed *= 0.45f;
            }
            float step = Math.min(distance - stop, speed * dt);
            enemy.x += step * offsetX / distance;
            enemy.y += step * offsetY / distance;
            enemy.gait += step;
            enemy.moving = step > 0.12f;
        } else if (enemy.kind == Creature.CROW && !siege && distance > 1 && distance < 59) {
            float step = 67 * dt / distance;
            enemy.x = clamp(enemy.x - step * offsetX, 58, WIDTH - 57);
            enemy.y = clamp(enemy.y - step * offsetY, 65, HEIGHT - 76);
            enemy.gait += 67 * dt;
            enemy.moving = true;
        }
        if (enemy.boss()) {
            enemy.y = Math.min(enemy.y, HEIGHT - 126);
        } else {
            separateEnemies(enemy, dt);
        }
    }

    private void separateEnemies(Enemy enemy, float dt) {
        for (Enemy other : enemies) {
            if (other == enemy || other.health <= 0) {
                continue;
            }
            float offsetX = enemy.x - other.x;
            float offsetY = enemy.y - other.y;
            float distance = (float) Math.hypot(offsetX, offsetY);
            if (distance > 0.1f && distance < 23) {
                float push = 42 * dt * (1 - distance / 23);
                enemy.x = clamp(enemy.x + offsetX / distance * push, 58, WIDTH - 57);
                enemy.y = clamp(enemy.y + offsetY / distance * push, 65, HEIGHT - 76);
            }
        }
    }

    private void finishStrike(Enemy enemy) {
        enemy.strikeFlash = 0.25f;
        enemy.attacks++;
        enemy.cooldown = switch (enemy.kind) {
            case BOAR -> 0.87f;
            case CROW -> 1.38f;
            case FOX -> enemy.enraged() ? 0.68f : 0.94f;
        };
        switch (enemy.strike) {
            case BOLT -> {
                fireBolt(enemy, 0, false);
                if (enemy.den != null) {
                    fireBolt(enemy, -0.28f, false);
                    fireBolt(enemy, 0.28f, false);
                }
            }
            case FAN -> {
                int spread = enemy.enraged() ? 2 : 1;
                for (int i = -spread; i <= spread; i++) {
                    fireBolt(enemy, i * 0.30f, true);
                }
            }
            case LUNGE, POUNCE -> {
                float radius = enemy.dangerRadius();
                boolean hit = within(x, y, enemy.targetX, enemy.targetY, radius);
                if (perfectDodgeWindow > 0 && (hit || within(dodgeStartX, dodgeStartY, enemy.targetX, enemy.targetY, radius))) {
                    rewardDodge();
                } else if (hit) {
                    hurtPlayer(enemy.kind.damage + (enemy.enraged() ? 7 : 0) + (enemy.den != null ? 7 : 0));
                }
                if (enemy.attacksShrine) {
                    hurtShrine(enemy.kind.damage);
                }
            }
        }
    }

    private void fireBolt(Enemy enemy, float spread, boolean foxFire) {
        double angle = Math.atan2(enemy.targetY - enemy.y, enemy.targetX - enemy.x) + spread;
        float speed = foxFire ? 292 : 252;
        float ux = (float) Math.cos(angle);
        float uy = (float) Math.sin(angle);
        bolts.add(new Bolt(enemy.x + ux * 15, enemy.y + uy * 15, ux * speed, uy * speed,
                foxFire ? 17 : enemy.kind.damage + (enemy.den != null ? 4 : 0), enemy.attacksShrine, foxFire));
    }

    private void updateBolts(float dt) {
        bolts.removeIf(bolt -> {
            bolt.x += bolt.vx * dt;
            bolt.y += bolt.vy * dt;
            bolt.life -= dt;
            if (bolt.life <= 0 || bolt.x < 32 || bolt.y < 40 || bolt.x > WIDTH - 32 || bolt.y > HEIGHT - 38) {
                return true;
            }
            float radius = bolt.foxFire ? 19 : 15;
            boolean hit = within(x, y, bolt.x, bolt.y, radius);
            if (perfectDodgeWindow > 0 && (hit || within(dodgeStartX, dodgeStartY, bolt.x, bolt.y, radius))) {
                rewardDodge();
            }
            if (hit && invulnerable <= 0) {
                hurtPlayer(bolt.damage);
                return true;
            }
            if (bolt.targetsShrine && defending() && within(ALTAR_X, ALTAR_Y, bolt.x, bolt.y, 23)) {
                hurtShrine(bolt.damage);
                return true;
            }
            return false;
        });
    }

    private void rewardDodge() {
        perfectDodgeWindow = 0;
        counterTime = COUNTER_TIME;
        feedback.add(new Feedback("完美闪避", x, y + 62, false));
        cues.add(Cue.LEVEL);
    }

    private void hurtShrine(int damage) {
        if (defending()) {
            damage = wardedDamage(damage, ALTAR_X, ALTAR_Y);
            shrine = down(shrine, damage);
            shrineFlash = 0.36f;
            feedback.add(new Feedback("-" + damage, ALTAR_X, ALTAR_Y + 78, true));
            cues.add(Cue.HURT);
        }
    }

    private void hurtPlayer(int damage) {
        if (invulnerable <= 0) {
            damage = wardedDamage(damage, x, y);
            health = down(health, damage);
            invulnerable = 0.62f;
            hurtFlash = 0.32f;
            feedback.add(new Feedback("-" + damage, x, y + 48, true));
            cues.add(Cue.HURT);
        }
    }

    private int wardedDamage(int damage, float targetX, float targetY) {
        if (stoneProtection > 0 && within(sealX, sealY, targetX, targetY, SEAL_RANGE)) {
            return Math.max(1, Math.round(damage * 0.42f));
        }
        return damage;
    }

    private void strike(Enemy enemy, int damage, float knockback) {
        if (enemy.boss() && !enemy.exposed()) {
            damage = Math.max(1, Math.round(damage * 0.27f));
        }
        enemy.health = down(enemy.health, damage);
        enemy.hitFlash = 0.13f;
        feedback.add(new Feedback("-" + damage, enemy.x, enemy.y + (enemy.boss() ? 105 : 47), false));
        if (!enemy.boss() && knockback > 0) {
            float ox = enemy.x - x;
            float oy = enemy.y - y;
            float length = (float) Math.hypot(ox, oy);
            if (length > 1) {
                enemy.x = clamp(enemy.x + ox / length * knockback, 58, WIDTH - 57);
                enemy.y = clamp(enemy.y + oy / length * knockback, 65, HEIGHT - 76);
            }
        }
    }

    private void removeDefeated() {
        enemies.removeIf(enemy -> {
            if (enemy.health > 0) {
                return false;
            }
            kills++;
            gainExperience(enemy.kind.experience + (enemy.den == null ? 0 : 32));
            if (enemy.den != null) {
                clearedDens.add(enemy.den);
                activeDen = null;
                if (equippedSeal == null) {
                    equippedSeal = enemy.den;
                }
                tell("习得" + enemy.den.seal + "  ·  C 施放，Tab 切换");
                cues.add(Cue.LEVEL);
            }
            if (enemy.boss()) {
                result = Result.WON;
                cues.add(Cue.GONG);
            }
            return true;
        });
    }

    private void gainExperience(int amount) {
        if (level >= MAX_LEVEL) {
            return;
        }
        experience += amount;
        while (level < MAX_LEVEL && experience >= nextLevelXp()) {
            experience -= nextLevelXp();
            level++;
            health = Math.min(maxHealth(), health + 36);
            cues.add(Cue.LEVEL);
            tell("修为提升至 " + level + " 阶  ·  气血上限与刀法威力提升");
        }
    }

    private void tell(String text) {
        message = text;
        messageTime = 3.4f;
    }

    static boolean within(float x1, float y1, float x2, float y2, float radius) {
        return distanceSquared(x1, y1, x2, y2) <= radius * radius;
    }

    private static float distanceSquared(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private static float down(float value, float by) {
        return Math.max(0, value - by);
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }
}
