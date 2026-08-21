package dev.nihit.gitdash.config;

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class ConfigLoader {
    public Config load() throws IOException {
        return load(Config.defaults());
    }

    Config load(Config d) throws IOException {
        Path file = d.configDir().resolve("config.toml");
        if (!Files.exists(file)) return d;
        TomlParseResult t = Toml.parse(file);
        if (t.hasErrors()) throw new IOException("Invalid configuration " + file + ": " + t.errors());
        int depth = integer(t, "scan.max_depth", d.maxDepth(), 0, 100);
        int stale = integer(t, "status.stale_days", d.staleDays(), 1, 36500);
        int parallel = integer(t, "concurrency.max_parallel", d.maxParallel(), 1, 256);
        List<String> excludes = t.getArray("scan.exclude") != null ? strings(t, "scan.exclude", d.exclusions()) : strings(t, "exclude", d.exclusions());
        var aliases = new LinkedHashMap<String, String>();
        if (t.getTable("aliases") != null) for (String k : t.getTable("aliases").keySet()) aliases.put(k, t.getString("aliases." + k));
        var groups = new LinkedHashMap<String, List<String>>();
        if (t.getTable("groups") != null) for (String k : t.getTable("groups").keySet()) groups.put(k, strings(t, "groups." + k, List.of()));
        String color = t.getString("ui.color", () -> d.color());
        if (!List.of("auto", "always", "never").contains(color)) throw new IOException("ui.color must be auto, always, or never");
        return new Config(d.configDir(), d.stateDir(), d.cacheDir(), depth,
                t.getBoolean("scan.follow_symlinks", () -> d.followSymlinks()), excludes, stale,
                color, t.getBoolean("ui.unicode", () -> d.unicode()),
                t.getBoolean("concurrency.enabled", () -> d.concurrencyEnabled()), parallel,
                java.util.Map.copyOf(aliases), java.util.Map.copyOf(groups));
    }

    private static int integer(TomlParseResult t, String key, int fallback, int min, int max) throws IOException {
        long v = t.getLong(key, () -> (long) fallback);
        if (v < min || v > max) throw new IOException(key + " must be between " + min + " and " + max);
        return (int) v;
    }
    private static List<String> strings(TomlParseResult t, String key, List<String> fallback) throws IOException {
        var a = t.getArray(key); if (a == null) return fallback;
        var out = new ArrayList<String>();
        for (int i = 0; i < a.size(); i++) { if (!(a.get(i) instanceof String s)) throw new IOException(key + " must contain strings"); out.add(s); }
        return List.copyOf(out);
    }
}
