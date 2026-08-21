package dev.nihit.gitdash.model;

import java.nio.file.Path;
import java.time.Instant;

public record Repository(String name, Path path, Instant discoveredAt) {
    public Repository {
        path = path.toAbsolutePath().normalize();
    }
}
