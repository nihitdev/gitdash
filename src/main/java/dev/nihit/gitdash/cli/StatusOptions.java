package dev.nihit.gitdash.cli;

import picocli.CommandLine.Option;

public final class StatusOptions {
    @Option(names="--dirty") boolean dirty;
    @Option(names="--clean") boolean clean;
    @Option(names="--ahead") boolean ahead;
    @Option(names="--behind") boolean behind;
    @Option(names="--conflicts") boolean conflicts;
    @Option(names="--detached") boolean detached;
    @Option(names="--no-upstream") boolean noUpstream;
    @Option(names="--no-remote") boolean noRemote;
    @Option(names="--invalid") boolean invalid;
    @Option(names="--stale") boolean stale;
    @Option(names="--stale-days", paramLabel="DAYS") Integer staleDays;
    @Option(names="--branch", paramLabel="BRANCH") String branch;
    @Option(names="--remote", paramLabel="TEXT") String remote;
    @Option(names="--group", paramLabel="GROUP") String group;
    @Option(names="--sort", defaultValue="name", description="name, path, status, branch, commit, ahead, behind") String sort;
    @Option(names="--reverse") boolean reverse;
    @Option(names="--limit", paramLabel="N") Integer limit;
    @Option(names="--json") boolean json;
    @Option(names="--porcelain") boolean porcelain;
}
