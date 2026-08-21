package dev.nihit.gitdash.repository;

import dev.nihit.gitdash.model.Repository;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RepositoryScanner {
    public record ScanResult(List<Repository> repositories, List<String> warnings) {}

    public ScanResult scan(Path root, int maxDepth, Set<String> exclusions, boolean followSymlinks, boolean nested) throws IOException {
        Path absolute = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute)) throw new IOException("Scan root is not a directory: " + absolute);
        var found = new ArrayList<Repository>(); var warnings = new ArrayList<String>(); var keys = new HashSet<Object>();
        var options = followSymlinks ? Set.of(FileVisitOption.FOLLOW_LINKS) : Set.<FileVisitOption>of();
        Files.walkFileTree(absolute, options, maxDepth + 1, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(absolute) && exclusions.contains(dir.getFileName().toString())) return FileVisitResult.SKIP_SUBTREE;
                if (followSymlinks && attrs.fileKey() != null && !keys.add(attrs.fileKey())) return FileVisitResult.SKIP_SUBTREE;
                Path marker = dir.resolve(".git");
                if (Files.isDirectory(marker) || Files.isRegularFile(marker)) {
                    found.add(new Repository(dir.getFileName().toString(), dir, Instant.now()));
                    return nested ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path file, IOException exc) {
                warnings.add(file + ": " + exc.getMessage()); return FileVisitResult.SKIP_SUBTREE;
            }
        });
        found.sort(java.util.Comparator.comparing(r -> r.path().toString()));
        return new ScanResult(List.copyOf(found), List.copyOf(warnings));
    }
}
