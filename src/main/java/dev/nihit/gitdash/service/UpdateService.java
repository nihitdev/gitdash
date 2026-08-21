package dev.nihit.gitdash.service;

import dev.nihit.gitdash.GitDash;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Locale;

public final class UpdateService {
    public int update(Path requestedPrefix) throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path prefix = requestedPrefix == null ? inferPrefix(windows) : requestedPrefix.toAbsolutePath().normalize();
        Path directory = Files.createTempDirectory("gitdash-update-");
        Path installer = directory.resolve(windows ? "install.ps1" : "install.sh");
        URI uri = URI.create("https://github.com/nihitdev/gitdash/releases/latest/download/" + installer.getFileName());
        try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(15)).build()) {
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("User-Agent", "GitDash/0.2.0").build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofFile(installer));
            if (response.statusCode() != 200) throw new IllegalStateException("Cannot download updater: HTTP " + response.statusCode());
        }
        var command = new ArrayList<String>();
        if (windows) { command.add("powershell.exe"); command.add("-NoProfile"); command.add("-ExecutionPolicy"); command.add("Bypass"); command.add("-File"); command.add(installer.toString()); command.add("-Prefix"); command.add(prefix.toString()); command.add("-WaitForProcessId"); command.add(Long.toString(ProcessHandle.current().pid())); }
        else { command.add("sh"); command.add(installer.toString()); }
        var process = new ProcessBuilder(command).inheritIO();
        if (!windows) process.environment().put("PREFIX", prefix.toString());
        var running = process.start();
        if (windows) { System.out.println("GitDash update scheduled; installation will continue after this process exits."); return 0; }
        int exit = running.waitFor();
        try { Files.deleteIfExists(installer); Files.deleteIfExists(directory); } catch (java.io.IOException ignored) { }
        return exit;
    }
    static Path inferPrefix(boolean windows) {
        try {
            Path location = Path.of(GitDash.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
            Path lib = Files.isDirectory(location) ? location : location.getParent();
            if (lib != null && lib.getFileName().toString().equals("lib") && lib.getParent() != null && lib.getParent().getFileName().toString().equals("gitdash")) return lib.getParent().getParent().getParent();
        } catch (Exception ignored) { }
        if (windows) return Path.of(System.getenv().getOrDefault("LOCALAPPDATA", System.getProperty("user.home")), "Programs", "GitDash");
        return Path.of(System.getProperty("user.home"), ".local");
    }
}
