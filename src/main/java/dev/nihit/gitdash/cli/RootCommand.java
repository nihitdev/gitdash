package dev.nihit.gitdash.cli;

import dev.nihit.gitdash.git.RepositoryInspector;
import dev.nihit.gitdash.model.Repository;
import dev.nihit.gitdash.model.RepositoryState;
import dev.nihit.gitdash.output.Renderers;
import dev.nihit.gitdash.repository.RepositoryScanner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Command(name="gitdash", mixinStandardHelpOptions=true, versionProvider=RootCommand.Version.class,
        description="Fast terminal dashboard for local Git repositories.",
        subcommands={RootCommand.Scan.class, RootCommand.Status.class, RootCommand.Summary.class,
                RootCommand.Dirty.class, RootCommand.Clean.class, RootCommand.Ahead.class, RootCommand.Behind.class,
                RootCommand.Conflicts.class, RootCommand.Stale.class, RootCommand.Repos.class, RootCommand.Show.class,
                RootCommand.Remove.class, RootCommand.Prune.class,
                RootCommand.Doctor.class, RootCommand.Fetch.class, RootCommand.ConfigCommand.class,
                RootCommand.Cache.class, RootCommand.Completion.class, RootCommand.Benchmark.class,
                RootCommand.Update.class})
public final class RootCommand implements Runnable {
    final AppContext context;
    @Option(names="--debug", description="Print diagnostic details to stderr") boolean debug;
    @Option(names="--no-color", description="Disable colored output") boolean noColor;
    public RootCommand(AppContext context) { this.context = context; }
    @Override public void run() { CommandLine.usage(this, System.out); }
    static final class Version implements CommandLine.IVersionProvider { public String[] getVersion() { return new String[]{"GitDash 0.2.0", "Java " + Runtime.version().feature()}; } }

    @Command(name="scan", description="Discover and register Git repositories.")
    static final class Scan implements Callable<Integer> {
        @ParentCommand RootCommand root; @Parameters(index="0", paramLabel="PATH") Path path;
        @Option(names="--max-depth") Integer maxDepth; @Option(names="--exclude") List<String> excludes = new ArrayList<>();
        @Option(names="--follow-symlinks") boolean follow; @Option(names="--nested", description="Discover nested repositories") boolean nested;
        public Integer call() throws Exception {
            var c=root.context.config(); var all=new HashSet<>(c.exclusions()); all.addAll(excludes);
            var result=new RepositoryScanner().scan(path,maxDepth==null?c.maxDepth():maxDepth,all,follow||c.followSymlinks(),nested);
            root.context.registry().merge(result.repositories());
            for(var r:result.repositories()) System.out.println("+ " + r.name() + "  " + r.path());
            for(var w:result.warnings()) System.err.println("warning: " + w);
            System.out.printf("%nDiscovered %d repositories; registry: %s%n",result.repositories().size(),root.context.registry().file()); return 0;
        }
    }
    @Command(name="status", description="Show registered repository status.")
    static final class Status implements Callable<Integer> { @ParentCommand RootCommand root; @Mixin StatusOptions options; public Integer call() throws Exception{return StatusSupport.render(root.context,options,new PrintWriter(System.out,true));} }
    abstract static class View implements Callable<Integer> { @ParentCommand RootCommand root; @Mixin StatusOptions options; abstract void set(); public Integer call() throws Exception {set();return StatusSupport.render(root.context,options,new PrintWriter(System.out,true));} }
    @Command(name="dirty",description="Show dirty repositories.") static final class Dirty extends View {void set(){options.dirty=true;}}
    @Command(name="clean",description="Show clean repositories.") static final class Clean extends View {void set(){options.clean=true;}}
    @Command(name="ahead",description="Show repositories ahead of upstream.") static final class Ahead extends View {void set(){options.ahead=true;}}
    @Command(name="behind",description="Show repositories behind upstream.") static final class Behind extends View {void set(){options.behind=true;}}
    @Command(name="conflicts",description="Show repositories with conflicts.") static final class Conflicts extends View {void set(){options.conflicts=true;}}

    @Command(name="summary", description="Summarize repository health.")
    static final class Summary implements Callable<Integer> { @ParentCommand RootCommand root; public Integer call() throws Exception {
        var s=root.context.statuses().inspect(root.context.registry().load()); int days=root.context.config().staleDays(); Instant now=Instant.now();
        System.out.println("GitDash Summary\n"); row("Repositories",s.size()); row("Clean",count(s,x->x.state()==RepositoryState.CLEAN)); row("Dirty",count(s,x->x.dirty()));
        row("Ahead",count(s,x->x.ahead()>0));row("Behind",count(s,x->x.behind()>0));row("Diverged",count(s,x->x.diverged()));row("Conflicts",count(s,x->x.conflicts()>0));
        row("Detached HEAD",count(s,x->x.detached()));row("No upstream",count(s,x->x.upstream().isEmpty()));row("No remote",count(s,x->x.remoteName().isEmpty()));row("Stale",count(s,x->x.stale(days,now))); return 0; }
        static long count(List<dev.nihit.gitdash.model.RepositoryStatus>s,java.util.function.Predicate<dev.nihit.gitdash.model.RepositoryStatus>p){return s.stream().filter(p).count();} static void row(String n,long v){System.out.printf("%-18s %6d%n",n,v);}
    }
    @Command(name="stale",description="Show repositories whose last commit exceeds the threshold.")
    static final class Stale implements Callable<Integer>{@ParentCommand RootCommand root;@Option(names="--days")Integer days; public Integer call()throws Exception{int d=days==null?root.context.config().staleDays():days;if(d<1)throw new IllegalArgumentException("--days must be positive");var all=root.context.statuses().inspect(root.context.registry().load());var s=all.stream().filter(x->x.stale(d,Instant.now())).toList();Renderers.table(s,new PrintWriter(System.out,true));return 0;}}
    @Command(name="repos",description="List and manage registered repositories.",subcommands={Repos.Rename.class,Repos.Export.class,Repos.Import.class})
    static final class Repos implements Callable<Integer>{@ParentCommand RootCommand root;@Option(names="--search")String search;@Option(names="--missing")boolean missing;@Option(names="--json")boolean json;@Option(names="--names",description="Print names only")boolean names;public Integer call()throws Exception{var selected=root.context.registry().load().stream().filter(r->!missing||!Files.isDirectory(r.path())).filter(r->search==null||(r.name()+" "+r.path()).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))).toList();if(json){var rows=selected.stream().map(r->java.util.Map.of("name",r.name(),"path",r.path().toString(),"exists",Files.isDirectory(r.path()),"discoveredAt",r.discoveredAt().toString())).toList();new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(System.out,rows);System.out.println();}else if(names){for(var r:selected)System.out.println(r.name());}else for(var r:selected)System.out.printf("%-24s %s%s%n",r.name(),r.path(),Files.isDirectory(r.path())?"":"  [missing]");return 0;}
        @Command(name="rename",description="Rename a registry entry without renaming its directory.")static final class Rename implements Callable<Integer>{@ParentCommand Repos parent;@Parameters(index="0",paramLabel="REPO")String id;@Parameters(index="1",paramLabel="NEW_NAME")String name;public Integer call()throws Exception{var repository=StatusSupport.resolve(parent.root.context,id);var renamed=parent.root.context.registry().rename(repository.path(),name);System.out.printf("Renamed %s to %s.%n",repository.name(),renamed.name());return 0;}}
        @Command(name="export",description="Export the repository registry as JSON.")static final class Export implements Callable<Integer>{@ParentCommand Repos parent;@Parameters(index="0",paramLabel="FILE")Path file;public Integer call()throws Exception{parent.root.context.registry().exportTo(file);System.out.println("Registry exported to "+file.toAbsolutePath().normalize());return 0;}}
        @Command(name="import",description="Import repositories from an exported JSON registry.")static final class Import implements Callable<Integer>{@ParentCommand Repos parent;@Parameters(index="0",paramLabel="FILE")Path file;@Option(names="--replace",description="Replace instead of merge")boolean replace;public Integer call()throws Exception{var repositories=parent.root.context.registry().importFrom(file,replace);System.out.printf("Registry %s: %d repositories.%n",replace?"replaced":"merged",repositories.size());return 0;}}
    }
    @Command(name="remove",description="Remove a repository from the registry; never deletes files.")
    static final class Remove implements Callable<Integer>{@ParentCommand RootCommand root;@Parameters(index="0")String id;public Integer call()throws Exception{var repository=StatusSupport.resolve(root.context,id);if(!root.context.registry().remove(repository.path()))throw new IllegalArgumentException("Repository is not registered: "+id);System.out.println("Removed from registry: "+repository.path());return 0;}}
    @Command(name="prune",description="Remove missing paths from the registry; never deletes repositories.")
    static final class Prune implements Callable<Integer>{@ParentCommand RootCommand root;@Option(names="--dry-run")boolean dryRun;public Integer call()throws Exception{var missing=root.context.registry().load().stream().filter(r->!Files.isDirectory(r.path())).toList();for(var r:missing)System.out.printf("%s %s%n",dryRun?"Would remove":"Removed",r.path());if(!dryRun)root.context.registry().removeMissing();System.out.printf("%d missing repositories %s.%n",missing.size(),dryRun?"found":"pruned");return 0;}}
    @Command(name="show",description="Show detailed repository information.")
    static final class Show implements Callable<Integer>{@ParentCommand RootCommand root;@Parameters(index="0")String id;public Integer call(){try{var s=new RepositoryInspector(root.context.git()).inspect(StatusSupport.resolve(root.context,id));
        System.out.printf("Repository%n  Name          %s%n  Path          %s%n  Branch        %s%n  State         %s%n%n",s.repository().name(),s.repository().path(),s.branch(),s.state().display());
        System.out.printf("Working Tree%n  Modified      %d%n  Staged        %d%n  Untracked     %d%n  Deleted       %d%n  Renamed       %d%n  Conflicts     %d%n%n",s.modified(),s.staged(),s.untracked(),s.deleted(),s.renamed(),s.conflicts());
        System.out.printf("Remote%n  Upstream      %s%n  Ahead         %d%n  Behind        %d%n  URL           %s%n%n",empty(s.upstream()),s.ahead(),s.behind(),empty(s.remoteUrl()));
        System.out.printf("Last Commit%n  Hash          %s%n  Author        %s%n  Time          %s%n  Subject       %s%n",empty(s.abbreviatedHash()),empty(s.commitAuthor()),Renderers.ago(s.commitTime()),empty(s.commitSubject()));return s.state()==RepositoryState.INVALID?2:0;}catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}}static String empty(String s){return s==null||s.isEmpty()?"-":s;}}

    @Command(name="doctor",description="Diagnose repositories that require attention.")
    static final class Doctor implements Callable<Integer>{@ParentCommand RootCommand root;public Integer call()throws Exception{var statuses=root.context.statuses().inspect(root.context.registry().load());int problems=0;System.out.println("GitDash Doctor\n");for(var s:statuses){var issues=new ArrayList<String>();
        if(!Files.exists(s.repository().path()))issues.add("registered path is missing");else if(s.state()==RepositoryState.INVALID)issues.add("invalid repository: "+s.error());
        if(s.conflicts()>0)issues.add(s.conflicts()+" unresolved conflict(s)");if(s.staged()>0)issues.add(s.staged()+" staged change(s)");if(s.modified()+s.untracked()>0)issues.add((s.modified()+s.untracked())+" uncommitted file(s)");
        if(s.detached())issues.add("detached HEAD");if(s.upstream().isEmpty())issues.add("no upstream");if(s.remoteName().isEmpty())issues.add("no remote");if(s.diverged())issues.add("diverged (ahead "+s.ahead()+", behind "+s.behind()+")");else{if(s.ahead()>0)issues.add("ahead by "+s.ahead());if(s.behind()>0)issues.add("behind by "+s.behind());}if(s.stale(root.context.config().staleDays(),Instant.now()))issues.add("stale");
        String mark=issues.isEmpty()?"✓":s.conflicts()>0||s.state()==RepositoryState.INVALID?"✗":"!";System.out.printf("%s %-18s %s%n",mark,s.repository().name(),issues.isEmpty()?"healthy":String.join(", ",issues));if(!issues.isEmpty())problems++;}
        System.out.printf("%n%d repositories require attention.%n",problems);return problems==0?0:2;}}

    @Command(name="fetch",description="Fetch remote metadata without changing working trees.")
    static final class Fetch implements Callable<Integer>{@ParentCommand RootCommand root;@Parameters(arity="0..1")String id;@Option(names="--all")boolean all;@Option(names="--group")String group;
        public Integer call()throws Exception{if(id==null&&!all&&group==null)throw new IllegalArgumentException("Specify a repository, --group, or --all");List<Repository> repos;
            if(id!=null)repos=List.of(StatusSupport.resolve(root.context,id));else if(group!=null){var names=root.context.config().groups().get(group);if(names==null)throw new IllegalArgumentException("Unknown group: "+group);repos=root.context.registry().load().stream().filter(r->names.contains(r.name())||names.contains(r.path().toString())).toList();}else repos=root.context.registry().load();
            var semaphore=new Semaphore(root.context.config().maxParallel());try(var executor=Executors.newVirtualThreadPerTaskExecutor()){var futures=repos.stream().map(r->executor.submit(()->{semaphore.acquire();try{return new FetchResult(r,root.context.git().run(r.path(),"fetch","--prune"));}finally{semaphore.release();}})).toList();int failures=0;for(var f:futures){try{var result=f.get();if(result.result.successful())System.out.printf("✓ %-20s fetched (%d ms)%n",result.repository.name(),result.result.duration().toMillis());else{System.out.printf("✗ %-20s %s%n",result.repository.name(),result.result.timedOut()?"timed out":result.result.stderr().strip());failures++;}}catch(java.util.concurrent.ExecutionException e){System.out.println("✗ fetch failed: "+e.getCause().getMessage());failures++;}}return failures==0?0:3;}}
        record FetchResult(Repository repository,dev.nihit.gitdash.git.GitResult result){}
    }

    @Command(name="config",description="Show configuration or manage aliases.",subcommands={ConfigCommand.Alias.class,ConfigCommand.Unalias.class})
    static final class ConfigCommand implements Runnable{@ParentCommand RootCommand root;public void run(){var c=root.context.config();System.out.printf("Config file     %s%nState directory %s%nCache directory %s%nMax depth       %d%nStale days      %d%nMax parallel    %d%n",c.configDir().resolve("config.toml"),c.stateDir(),c.cacheDir(),c.maxDepth(),c.staleDays(),c.maxParallel());}
        @Command(name="alias",description="Add or update a repository alias.")static final class Alias implements Callable<Integer>{@ParentCommand ConfigCommand parent;@Parameters(index="0")String alias;@Parameters(index="1")Path path;public Integer call()throws Exception{Path config=parent.root.context.config().configDir().resolve("config.toml");dev.nihit.gitdash.config.ConfigEditor.putAlias(config,alias,path);System.out.println("Alias saved. It will be active on the next invocation.");return 0;}}
        @Command(name="unalias",description="Remove a repository alias.")static final class Unalias implements Callable<Integer>{@ParentCommand ConfigCommand parent;@Parameters(index="0")String alias;public Integer call()throws Exception{Path config=parent.root.context.config().configDir().resolve("config.toml");if(!dev.nihit.gitdash.config.ConfigEditor.removeAlias(config,alias))throw new IllegalArgumentException("Alias not found: "+alias);System.out.println("Alias removed. The change will be active on the next invocation.");return 0;}}
    }

    @Command(name="cache",description="Manage cache.",subcommands={Cache.Clear.class})
    static final class Cache implements Runnable{@ParentCommand RootCommand root;public void run(){System.out.println("GitDash does not cache working-tree status.");}@Command(name="clear")static final class Clear implements Callable<Integer>{@ParentCommand Cache parent;public Integer call()throws Exception{Path dir=parent.root.context.config().cacheDir();if(Files.exists(dir))try(var paths=Files.walk(dir)){for(Path p:paths.sorted(java.util.Comparator.reverseOrder()).toList())if(!p.equals(dir))Files.deleteIfExists(p);}System.out.println("Cache cleared.");return 0;}}}

    @Command(name="completion",description="Generate contextual shell completion.")
    static final class Completion implements Callable<Integer>{@Parameters(index="0",completionCandidates=Shells.class)String shell;public Integer call(){System.out.print(CompletionScripts.forShell(shell));return 0;}static final class Shells implements Iterable<String>{public java.util.Iterator<String> iterator(){return List.of("bash","zsh","fish","powershell").iterator();}}}

    @Command(name="benchmark",description="Compare sequential and concurrent inspection using temporary repositories.")
    static final class Benchmark implements Callable<Integer>{@ParentCommand RootCommand root;@Option(names="--repositories",defaultValue="20")int count;public Integer call()throws Exception{if(count<1||count>200)throw new IllegalArgumentException("--repositories must be 1..200");Path temp=Files.createTempDirectory("gitdash-benchmark-");var repos=new ArrayList<Repository>();try{for(int i=0;i<count;i++){Path p=Files.createDirectory(temp.resolve("repo-"+i));var r=root.context.git().run(p,"init","--quiet");if(!r.successful())throw new IOException(r.stderr());repos.add(new Repository(p.getFileName().toString(),p,Instant.now()));}
            var inspector=new RepositoryInspector(root.context.git());long start=System.nanoTime();for(var r:repos)inspector.inspect(r);long sequential=System.nanoTime()-start;start=System.nanoTime();try(var exec=Executors.newVirtualThreadPerTaskExecutor()){for(var f:repos.stream().map(r->exec.submit(()->inspector.inspect(r))).toList())f.get();}long concurrent=System.nanoTime()-start;
            report(count,"sequential",sequential);report(count,"concurrent",concurrent);return 0;}finally{try(var paths=Files.walk(temp)){for(Path p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}}static void report(int n,String mode,long nanos){double sec=nanos/1_000_000_000.0;System.out.printf("Repositories       %d%nMode               %s%nElapsed            %d ms%nThroughput         %.1f repos/sec%n%n",n,mode,nanos/1_000_000,n/sec);}}
    @Command(name="update",description="Update GitDash from the latest verified GitHub release.")
    static final class Update implements Callable<Integer>{@Option(names="--prefix",paramLabel="PATH",description="Installation prefix")Path prefix;public Integer call()throws Exception{return new dev.nihit.gitdash.service.UpdateService().update(prefix);}}
}
