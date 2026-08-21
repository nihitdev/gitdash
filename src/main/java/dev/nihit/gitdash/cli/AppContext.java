package dev.nihit.gitdash.cli;

import dev.nihit.gitdash.config.Config;
import dev.nihit.gitdash.git.GitClient;
import dev.nihit.gitdash.git.RepositoryInspector;
import dev.nihit.gitdash.repository.RepositoryRegistry;
import dev.nihit.gitdash.service.StatusService;

import java.time.Duration;

public record AppContext(Config config, GitClient git, RepositoryRegistry registry, StatusService statuses) {
    public static AppContext create(Config config) {
        var git = new GitClient(Duration.ofSeconds(20));
        return new AppContext(config, git, new RepositoryRegistry(config.stateDir()),
                new StatusService(new RepositoryInspector(git), config.concurrencyEnabled(), config.maxParallel()));
    }
}
