package dev.nihit.gitdash.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record Config(Path configDir, Path stateDir, Path cacheDir, int maxDepth,
        boolean followSymlinks, List<String> exclusions, int staleDays,
        String color, boolean unicode, boolean concurrencyEnabled, int maxParallel,
        Map<String, String> aliases, Map<String, List<String>> groups) {

    public static Config defaults() {
        Path home = Path.of(System.getProperty("user.home"));
        Path config = xdg("XDG_CONFIG_HOME", home.resolve(".config")).resolve("gitdash");
        Path state = xdg("XDG_STATE_HOME", home.resolve(".local/state")).resolve("gitdash");
        Path cache = xdg("XDG_CACHE_HOME", home.resolve(".cache")).resolve("gitdash");
        return new Config(config, state, cache, 8, false,
                List.of("node_modules", "target", "build", ".cache", ".gradle", ".idea", "vendor"),
                30, "auto", true, true, 32, Map.of(), Map.of());
    }

    private static Path xdg(String variable, Path fallback) {
        String value = System.getenv(variable);
        return value == null || value.isBlank() ? fallback : Path.of(value);
    }
}
