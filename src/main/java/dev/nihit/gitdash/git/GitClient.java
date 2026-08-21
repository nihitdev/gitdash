package dev.nihit.gitdash.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public final class GitClient {
    private static final Pattern HTTP_CREDENTIALS = Pattern.compile("(?i)(https?://)[^/@\\s]+@");
    private static final Pattern TOKEN_QUERY = Pattern.compile("(?i)([?&](?:access_token|token|auth|key)=)[^&\\s]+");
    private final Duration timeout;

    public GitClient(Duration timeout) { this.timeout = timeout; }

    public GitResult run(Path directory, String... arguments) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add("git");
        command.add("--no-pager");
        command.add("-c"); command.add("color.ui=false");
        command.addAll(List.of(arguments));
        var builder = new ProcessBuilder(command).directory(directory.toFile());
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        var started = System.nanoTime();
        var process = builder.start();
        try (var readers = Executors.newVirtualThreadPerTaskExecutor()) {
            var stdoutFuture = readers.submit(() -> process.getInputStream().readAllBytes());
            var stderrFuture = readers.submit(() -> process.getErrorStream().readAllBytes());
            boolean completed;
            try { completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS); }
            catch (InterruptedException interrupted) { terminate(process); Thread.currentThread().interrupt(); throw interrupted; }
            if (!completed) terminate(process);
            String stdout = new String(bytes(stdoutFuture), StandardCharsets.UTF_8);
            String stderr = new String(bytes(stderrFuture), StandardCharsets.UTF_8);
            int exit = completed ? process.exitValue() : -1;
            return new GitResult(exit, stdout, redact(stderr), Duration.ofNanos(System.nanoTime() - started), !completed);
        }
    }

    private static byte[] bytes(java.util.concurrent.Future<byte[]> future) throws IOException, InterruptedException {
        try { return future.get(); }
        catch (ExecutionException e) { if (e.getCause() instanceof IOException io) return new byte[0]; throw new IOException("Cannot capture Git output", e.getCause()); }
    }

    private static void terminate(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            process.waitFor(1, TimeUnit.SECONDS);
        }
    }

    public static String redact(String value) {
        if (value == null) return null;
        return TOKEN_QUERY.matcher(HTTP_CREDENTIALS.matcher(value).replaceAll("$1***@")).replaceAll("$1***");
    }
}
