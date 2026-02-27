package com.jrs.gl.util;

import java.nio.file.Path;

public class IOUtil {
    public static Path defaultShaderDir() {
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();

        System.out.println("OS: " + os);

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                return Path.of(appData, "com", "jrs", "shaders");
            }
            return Path.of(home, "AppData", "Roaming", "com", "jrs", "shaders");
        }

        // mac + linux
        return Path.of(home, ".com", "jrs", "shaders");
    }
}
