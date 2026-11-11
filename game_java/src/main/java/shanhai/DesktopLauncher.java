package shanhai;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public final class DesktopLauncher {
    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        var configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("山海 · 守夜");
        configuration.setWindowedMode(1280, 800);
        configuration.setWindowSizeLimits(960, 600, -1, -1);
        configuration.useVsync(true);
        configuration.setForegroundFPS(120);
        configuration.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4);
        new Lwjgl3Application(new Nightwatch(), configuration);
    }
}
