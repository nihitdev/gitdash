package dev.nihit.gitdash.model;

import java.time.Instant;
import java.util.Optional;

public record RepositoryStatus(
        Repository repository, RepositoryState state, String branch, boolean detached,
        int modified, int staged, int untracked, int deleted, int renamed, int conflicts,
        String upstream, int ahead, int behind, String remoteName, String remoteUrl,
        String commitHash, String abbreviatedHash, String commitSubject, Instant commitTime,
        String commitAuthor, boolean hasCommits, String error) {

    public boolean dirty() { return modified + staged + untracked + deleted + renamed + conflicts > 0; }
    public boolean diverged() { return ahead > 0 && behind > 0; }
    public boolean stale(int days, Instant now) {
        return hasCommits && commitTime != null && commitTime.isBefore(now.minusSeconds(days * 86_400L));
    }
    public Optional<String> errorOptional() { return Optional.ofNullable(error); }
}
