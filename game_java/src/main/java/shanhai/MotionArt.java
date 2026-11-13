package shanhai;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** Each frame is registered at the creature's ground contact point. */
final class MotionArt {
    // Rectangle edges followed by the ground contact point, in atlas pixels.
    private static final int[][] WANDERER = {
            {68, 34, 270, 299, 172, 290}, {374, 29, 591, 299, 482, 290},
            {691, 24, 907, 299, 792, 290}, {1001, 32, 1197, 299, 1103, 291},
            {51, 349, 290, 600, 169, 592}, {388, 349, 585, 600, 482, 593},
            {676, 349, 904, 594, 785, 587}, {1016, 357, 1211, 600, 1103, 593},
            {20, 661, 264, 913, 143, 904}, {330, 665, 627, 911, 465, 903},
            {627, 662, 938, 913, 766, 904}, {995, 655, 1202, 914, 1086, 906},
            {38, 1025, 277, 1225, 148, 1218}, {359, 1006, 573, 1202, 474, 1195},
            {657, 1027, 920, 1226, 778, 1218}, {1009, 971, 1207, 1227, 1101, 1220}
    };

    private static final int[][] FOX = {
            {40, 38, 273, 270, 179, 262}, {348, 32, 586, 269, 489, 262},
            {659, 42, 893, 269, 798, 262}, {963, 33, 1213, 269, 1111, 262},
            {23, 345, 290, 569, 177, 560}, {320, 338, 611, 545, 486, 539},
            {639, 347, 922, 574, 797, 565}, {957, 358, 1227, 574, 1100, 566},
            {20, 665, 265, 882, 162, 874}, {318, 635, 618, 825, 478, 817},
            {637, 645, 929, 889, 799, 880}, {951, 661, 1220, 886, 1096, 879},
            {27, 968, 283, 1204, 174, 1196}, {324, 945, 588, 1204, 491, 1196},
            {632, 968, 927, 1202, 795, 1194}, {963, 968, 1215, 1204, 1109, 1196}
    };

    private static final int[][] BEASTS = {
            {48, 42, 340, 256, 193, 246}, {410, 41, 698, 238, 548, 229},
            {763, 43, 1056, 253, 907, 245}, {1136, 39, 1407, 247, 1270, 238},
            {52, 306, 322, 507, 191, 499}, {400, 309, 698, 509, 548, 500},
            {751, 296, 1069, 497, 909, 488}, {1124, 300, 1411, 506, 1265, 498},
            {86, 544, 295, 773, 188, 765}, {410, 585, 679, 771, 552, 763},
            {768, 610, 1041, 770, 897, 761}, {1135, 562, 1390, 768, 1265, 760},
            {38, 819, 327, 1041, 185, 1031}, {403, 849, 688, 1037, 548, 1028},
            {776, 849, 1024, 1035, 904, 1026}, {1139, 847, 1379, 1039, 1254, 1030}
    };

    record Frame(TextureRegion right, TextureRegion left, int anchorX, int anchorBottom, float scale) {
        void draw(SpriteBatch batch, float x, float y, boolean faceRight, float light, float opacity) {
            int width = right.getRegionWidth();
            float anchor = faceRight ? anchorX : width - anchorX;
            batch.setColor(light, light, light, opacity);
            batch.draw(faceRight ? right : left, x - anchor * scale, y - anchorBottom * scale,
                    width * scale, right.getRegionHeight() * scale);
            batch.setColor(1, 1, 1, 1);
        }
    }

    private final Frame[] wanderer;
    private final Frame[] fox;
    private final Frame[] beasts;
    private final Frame[] guardians;

    MotionArt(Texture wandererTexture, Texture foxTexture, Texture beastsTexture) {
        wanderer = load(wandererTexture, WANDERER, 0.302f);
        fox = load(foxTexture, FOX, 0.526f);
        beasts = load(beastsTexture, BEASTS, 0.247f);
        guardians = load(beastsTexture, BEASTS, 0.314f);
    }

    private static Frame[] load(Texture texture, int[][] bounds, float scale) {
        var frames = new Frame[bounds.length];
        for (int i = 0; i < bounds.length; i++) {
            int[] box = bounds[i];
            var front = new TextureRegion(texture, box[0], box[1], box[2] - box[0], box[3] - box[1]);
            var reverse = new TextureRegion(front);
            reverse.flip(true, false);
            frames[i] = new Frame(front, reverse, box[4] - box[0], box[3] - box[5], scale);
        }
        return frames;
    }

    Frame wanderer(Wilds world) {
        if (world.dodgeTime > 0) {
            return wanderer[12 + frameAt(1 - world.dodgeTime / Wilds.DODGE_DURATION)];
        }
        if (world.slash > 0) {
            return wanderer[8 + frameAt(1 - world.slash / Wilds.SLASH_TIME)];
        }
        if (world.moving) {
            return wanderer[4 + (int) (world.gait / 15f) % 4];
        }
        return wanderer[(int) (world.elapsed * 3.3f) % 4];
    }

    Frame enemy(Wilds.Enemy enemy, float elapsed) {
        if (enemy.boss()) {
            return fox(enemy, elapsed);
        }
        Frame[] frames = enemy.den == null ? beasts : guardians;
        boolean crow = enemy.kind == Wilds.Creature.CROW;
        int action = crow ? 12 : 4;
        if (enemy.windup > 0) {
            return frames[action + (enemy.windup < enemy.windupLength * 0.45f ? 1 : 0)];
        }
        if (enemy.strikeFlash > 0) {
            return frames[action + (enemy.strikeFlash > 0.14f ? 2 : 3)];
        }
        if (crow) {
            return frames[8 + (int) (crowWingPhase(enemy, elapsed) * (2 / Math.PI)) % 4];
        }
        return enemy.moving ? frames[(int) (enemy.gait / 10f) % 4] : frames[7];
    }

    static float crowWingPhase(Wilds.Enemy enemy, float elapsed) {
        return (elapsed * 2.75f + enemy.x * 0.002f) * (float) (2 * Math.PI);
    }

    private Frame fox(Wilds.Enemy enemy, float elapsed) {
        int action = enemy.strike == Wilds.Strike.FAN ? 12 : 8;
        if (enemy.leapTime > 0) {
            return fox[enemy.leapTime > Wilds.POUNCE_TIME * 0.36f ? 9 : 10];
        }
        if (enemy.windup > 0) {
            return fox[action + (enemy.windup < enemy.windupLength * 0.28f ? 1 : 0)];
        }
        if (enemy.strikeFlash > 0) {
            return fox[action + (enemy.strikeFlash > 0.12f ? 2 : 3)];
        }
        if (enemy.moving) {
            return fox[4 + (int) (enemy.gait / 13f) % 4];
        }
        return fox[(int) (elapsed * 3.0f) % 4];
    }

    private static int frameAt(float fraction) {
        return Math.max(0, Math.min(3, (int) (fraction * 4)));
    }
}
