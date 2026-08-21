package dev.nihit.gitdash.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.nihit.gitdash.model.RepositoryStatus;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

public final class Renderers {
    private Renderers() {}
    public static void table(List<RepositoryStatus> statuses, PrintWriter out) {
        if (statuses.isEmpty()) { out.println("No repositories matched."); return; }
        int nw = Math.min(30, Math.max(10, statuses.stream().mapToInt(s -> s.repository().name().length()).max().orElse(10)));
        out.printf("%-"+nw+"s  %-18s  %-10s  %5s  %6s  %12s%n", "REPOSITORY", "BRANCH", "STATE", "AHEAD", "BEHIND", "LAST COMMIT");
        for (var s : statuses) out.printf("%-"+nw+"s  %-18s  %-10s  %5s  %6s  %12s%n",
                clip(s.repository().name(), nw), clip(s.branch(),18), s.state().display(), count(s.ahead(), s.upstream()), count(s.behind(), s.upstream()), ago(s.commitTime()));
        long attention = statuses.stream().filter(s -> s.state() != dev.nihit.gitdash.model.RepositoryState.CLEAN || s.ahead() > 0 || s.behind() > 0).count();
        out.printf("%n%d repositories%n%d require attention%n", statuses.size(), attention);
    }
    public static void porcelain(List<RepositoryStatus> statuses, PrintWriter out) {
        for (var s : statuses) out.printf("%s\t%s\t%s\t%d\t%d\t%s%n", escape(s.repository().path().toString()), escape(s.branch()), s.state().display(), s.ahead(), s.behind(), escape(s.upstream()));
    }
    public static void json(List<RepositoryStatus> statuses, PrintWriter out) throws IOException {
        var rows = statuses.stream().map(s -> {
            var m = new LinkedHashMap<String,Object>(); m.put("name", s.repository().name()); m.put("path", s.repository().path().toString());
            m.put("branch", s.branch()); m.put("detached", s.detached()); m.put("state", s.state().display());
            m.put("modified",s.modified()); m.put("staged",s.staged()); m.put("untracked",s.untracked()); m.put("deleted",s.deleted()); m.put("renamed",s.renamed()); m.put("conflicts",s.conflicts());
            m.put("upstream",s.upstream()); m.put("ahead",s.ahead()); m.put("behind",s.behind()); m.put("diverged",s.diverged());
            m.put("remote",s.remoteName()); m.put("remoteUrl",s.remoteUrl());
            m.put("commitHash",s.commitHash()); m.put("abbreviatedHash",s.abbreviatedHash()); m.put("commitSubject",s.commitSubject()); m.put("commitTime",s.commitTime()); m.put("commitAuthor",s.commitAuthor()); m.put("hasCommits",s.hasCommits()); m.put("error",s.error()); return m;
        }).toList();
        var root = new LinkedHashMap<String,Object>(); root.put("schemaVersion", 1); root.put("generatedAt", Instant.now()); root.put("repositories", rows);
        new ObjectMapper().registerModule(new JavaTimeModule()).enable(SerializationFeature.INDENT_OUTPUT).writeValue(out, root); out.println();
    }
    public static String ago(Instant instant) {
        if (instant == null) return "never"; long seconds = Math.max(0, Duration.between(instant, Instant.now()).getSeconds());
        if (seconds < 60) return seconds + "s ago"; if (seconds < 3600) return seconds/60 + "m ago"; if (seconds < 86400) return seconds/3600 + "h ago"; return seconds/86400 + "d ago";
    }
    private static String count(int n, String upstream) { return upstream.isEmpty() ? "-" : Integer.toString(n); }
    private static String clip(String s, int width) { return s.length() <= width ? s : s.substring(0, width-1) + "…"; }
    private static String escape(String s) { return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n"); }
}
