package dev.nihit.gitdash.git;

import java.time.Duration;

public record GitResult(int exitCode, String stdout, String stderr, Duration duration, boolean timedOut) {
    public boolean successful() { return exitCode == 0 && !timedOut; }
}
