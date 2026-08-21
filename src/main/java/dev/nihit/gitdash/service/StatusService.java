package dev.nihit.gitdash.service;

import dev.nihit.gitdash.git.RepositoryInspector;
import dev.nihit.gitdash.model.Repository;
import dev.nihit.gitdash.model.RepositoryStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public final class StatusService {
    private final RepositoryInspector inspector; private final boolean concurrent; private final int maxParallel;
    public StatusService(RepositoryInspector inspector, boolean concurrent, int maxParallel) {
        this.inspector = inspector; this.concurrent = concurrent; this.maxParallel = maxParallel;
    }
    public List<RepositoryStatus> inspect(List<Repository> repositories) throws InterruptedException {
        if (!concurrent || repositories.size() < 2) {
            var out = new ArrayList<RepositoryStatus>(); for (Repository r : repositories) out.add(inspector.inspect(r)); return List.copyOf(out);
        }
        var semaphore = new Semaphore(maxParallel);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = repositories.stream().map(r -> executor.submit(() -> {
                semaphore.acquire(); try { return inspector.inspect(r); } finally { semaphore.release(); }
            })).toList();
            var out = new ArrayList<RepositoryStatus>();
            for (var f : futures) try { out.add(f.get()); } catch (java.util.concurrent.ExecutionException e) { throw new IllegalStateException(e.getCause()); }
            return List.copyOf(out);
        }
    }
}
