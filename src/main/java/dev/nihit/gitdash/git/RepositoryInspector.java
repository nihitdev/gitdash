package dev.nihit.gitdash.git;

import dev.nihit.gitdash.model.Repository;
import dev.nihit.gitdash.model.RepositoryState;
import dev.nihit.gitdash.model.RepositoryStatus;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

public final class RepositoryInspector {
    private final GitClient git;
    public RepositoryInspector(GitClient git) { this.git = git; }

    public RepositoryStatus inspect(Repository repository) {
        try {
            GitResult status = git.run(repository.path(), "status", "--porcelain=v2", "--branch", "-z", "--untracked-files=normal");
            if (!status.successful()) return invalid(repository, message(status));
            var p = parseStatus(status.stdout());
            var commit = git.run(repository.path(), "log", "-1", "--format=%H%x00%h%x00%s%x00%cI%x00%an");
            boolean hasCommits = commit.successful() && !commit.stdout().isBlank();
            String[] c = hasCommits ? commit.stdout().strip().split("\u0000", -1) : new String[0];
            String remoteName = remoteFromUpstream(p.upstream);
            String remoteUrl = "";
            if (!remoteName.isEmpty()) {
                var url = git.run(repository.path(), "remote", "get-url", remoteName);
                if (url.successful()) remoteUrl = GitClient.redact(url.stdout().strip());
            }
            RepositoryState state = p.conflicts > 0 ? RepositoryState.CONFLICT : p.dirty() ? RepositoryState.DIRTY : RepositoryState.CLEAN;
            return new RepositoryStatus(repository, state, p.branch, p.detached, p.modified, p.staged, p.untracked,
                    p.deleted, p.renamed, p.conflicts, p.upstream, p.ahead, p.behind, remoteName, remoteUrl,
                    value(c, 0), value(c, 1), value(c, 2), instant(value(c, 3)), value(c, 4), hasCommits, null);
        } catch (IOException e) { return invalid(repository, e.getMessage()); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return invalid(repository, "inspection interrupted"); }
        catch (RuntimeException e) { return invalid(repository, "Malformed Git output: " + e.getMessage()); }
    }

    private static Parsed parseStatus(String output) {
        var p = new Parsed(); String[] records = output.split("\u0000", -1);
        for (int i = 0; i < records.length; i++) {
            String line = records[i];
            if (line.startsWith("# branch.head ")) { p.branch = line.substring(14); p.detached = "(detached)".equals(p.branch); }
            else if (line.startsWith("# branch.upstream ")) p.upstream = line.substring(18);
            else if (line.startsWith("# branch.ab ")) { String[] ab = line.substring(12).split(" "); p.ahead = number(ab[0]); p.behind = number(ab[1]); }
            else if (line.startsWith("1 ") || line.startsWith("2 ") || line.startsWith("u ")) {
                String xy = line.substring(2, 4); if (line.startsWith("u ") || isConflict(xy)) p.conflicts++;
                if (xy.charAt(0) != '.') p.staged++;
                if (xy.charAt(1) != '.') p.modified++;
                if (xy.indexOf('D') >= 0) p.deleted++;
                if (xy.indexOf('R') >= 0) p.renamed++;
                if (line.startsWith("2 ") && i + 1 < records.length) i++;
            } else if (line.startsWith("? ")) p.untracked++;
        }
        if (p.detached) p.branch = "(detached)";
        return p;
    }
    private static boolean isConflict(String xy) { return "DD AU UD UA DU AA UU".contains(xy); }
    private static int number(String s) { return Integer.parseInt(s.substring(1)); }
    private static String remoteFromUpstream(String u) { int slash = u.indexOf('/'); return slash < 1 ? "" : u.substring(0, slash); }
    private static String value(String[] values, int i) { return i < values.length ? values[i] : ""; }
    private static Instant instant(String s) { try { return s.isEmpty() ? null : Instant.parse(s); } catch (DateTimeParseException e) { return null; } }
    private static String message(GitResult r) { return r.timedOut() ? "Git command timed out" : r.stderr().strip(); }
    private static RepositoryStatus invalid(Repository r, String error) {
        return new RepositoryStatus(r, RepositoryState.INVALID, "-", false, 0,0,0,0,0,0,"",0,0,"","","","","",null,"",false,error);
    }
    private static final class Parsed {
        String branch = "(unknown)", upstream = ""; boolean detached; int modified, staged, untracked, deleted, renamed, conflicts, ahead, behind;
        boolean dirty() { return modified + staged + untracked + deleted + renamed + conflicts > 0; }
    }
}
