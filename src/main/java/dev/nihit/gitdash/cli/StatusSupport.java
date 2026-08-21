package dev.nihit.gitdash.cli;

import dev.nihit.gitdash.model.Repository;
import dev.nihit.gitdash.model.RepositoryStatus;
import dev.nihit.gitdash.output.Renderers;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

final class StatusSupport {
    private StatusSupport() {}
    static List<RepositoryStatus> select(AppContext context, StatusOptions o) throws Exception {
        List<RepositoryStatus> all = context.statuses().inspect(context.registry().load());
        var selected = new ArrayList<RepositoryStatus>();
        var group = o.group == null ? null : context.config().groups().get(o.group);
        if (o.group != null && group == null) throw new IllegalArgumentException("Unknown group: " + o.group);
        var members = group == null ? null : new HashSet<>(group);
        for (var s : all) {
            if (o.dirty && !s.dirty() || o.clean && s.dirty() || o.ahead && s.ahead() <= 0 || o.behind && s.behind() <= 0 || o.conflicts && s.conflicts() <= 0) continue;
            if (o.detached && !s.detached() || o.noUpstream && !s.upstream().isEmpty()) continue;
            if (o.noRemote && !s.remoteName().isEmpty() || o.invalid && s.state() != dev.nihit.gitdash.model.RepositoryState.INVALID) continue;
            int staleDays = o.staleDays == null ? context.config().staleDays() : o.staleDays;
            if (staleDays < 1) throw new IllegalArgumentException("--stale-days must be positive");
            if (o.stale && !s.stale(staleDays, Instant.now())) continue;
            if (o.branch != null && !o.branch.equals(s.branch())) continue;
            if (o.remote != null && !(s.remoteName() + " " + s.remoteUrl()).toLowerCase(Locale.ROOT).contains(o.remote.toLowerCase(Locale.ROOT))) continue;
            if (members != null && !members.contains(s.repository().name()) && !members.contains(s.repository().path().toString())) continue;
            selected.add(s);
        }
        Comparator<RepositoryStatus> comparator = switch (o.sort) {
            case "name" -> Comparator.comparing(s -> s.repository().name(), String.CASE_INSENSITIVE_ORDER);
            case "path" -> Comparator.comparing(s -> s.repository().path().toString());
            case "status" -> Comparator.comparing(s -> s.state().ordinal());
            case "branch" -> Comparator.comparing(RepositoryStatus::branch, String.CASE_INSENSITIVE_ORDER);
            case "commit" -> Comparator.comparing(RepositoryStatus::commitTime, Comparator.nullsFirst(Comparator.naturalOrder()));
            case "ahead" -> Comparator.comparingInt(RepositoryStatus::ahead);
            case "behind" -> Comparator.comparingInt(RepositoryStatus::behind);
            default -> throw new IllegalArgumentException("Unknown sort: " + o.sort);
        };
        comparator = comparator.thenComparing(s -> s.repository().path().toString()); if (o.reverse) comparator = comparator.reversed();
        selected.sort(comparator);
        if (o.limit != null) {
            if (o.limit < 0) throw new IllegalArgumentException("--limit must not be negative");
            if (selected.size() > o.limit) selected.subList(o.limit, selected.size()).clear();
        }
        return List.copyOf(selected);
    }
    static int render(AppContext context, StatusOptions o, PrintWriter out) throws Exception {
        if (o.json && o.porcelain) throw new IllegalArgumentException("--json and --porcelain are mutually exclusive");
        var selected = select(context, o); if (o.json) Renderers.json(selected, out); else if (o.porcelain) Renderers.porcelain(selected, out); else Renderers.table(selected, out); return 0;
    }
    static Repository resolve(AppContext context, String id) throws Exception {
        String aliased = context.config().aliases().getOrDefault(id, id); var exact = new ArrayList<Repository>();
        for (Repository r : context.registry().load()) if (r.name().equals(aliased) || r.path().toString().equals(aliased) || r.path().equals(java.nio.file.Path.of(aliased).toAbsolutePath().normalize())) exact.add(r);
        if (exact.isEmpty()) throw new IllegalArgumentException("Repository not found: " + id);
        if (exact.size() > 1) throw new IllegalArgumentException("Ambiguous repository name '" + id + "': " + exact.stream().map(r -> r.path().toString()).toList());
        return exact.getFirst();
    }
}
