package dev.nihit.gitdash.git;

import dev.nihit.gitdash.model.Repository;
import dev.nihit.gitdash.model.RepositoryState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryInspectorTest {
    @TempDir Path temp; RepositoryInspector inspector;
    @BeforeEach void setup(){inspector=new RepositoryInspector(new GitClient(Duration.ofSeconds(5)));}
    @Test void handlesNoCommitsRemoteOrUpstream()throws Exception{Path p=init("empty");var s=inspect(p);assertThat(s.hasCommits()).isFalse();assertThat(s.remoteName()).isEmpty();assertThat(s.upstream()).isEmpty();assertThat(s.state()).isEqualTo(RepositoryState.CLEAN);}
    @Test void countsModifiedStagedUntrackedAndDeleted()throws Exception{Path p=init("work");write(p,"tracked","one");commit(p,"initial");write(p,"tracked","two");write(p,"staged","yes");git(p,"add","staged");write(p,"loose","yes");var s=inspect(p);assertThat(s.modified()).isEqualTo(1);assertThat(s.staged()).isEqualTo(1);assertThat(s.untracked()).isEqualTo(1);assertThat(s.state()).isEqualTo(RepositoryState.DIRTY);Files.delete(p.resolve("tracked"));assertThat(inspect(p).deleted()).isEqualTo(1);}
    @Test void detectsDetachedHead()throws Exception{Path p=init("detached");write(p,"a","a");commit(p,"one");git(p,"checkout","--detach","--quiet");assertThat(inspect(p).detached()).isTrue();}
    @Test void detectsAheadBehindAndDivergence()throws Exception{Path bare=temp.resolve("origin.git");git(temp,"init","--bare","--initial-branch=master",bare.toString());Path a=temp.resolve("a"),b=temp.resolve("b");git(temp,"clone","--quiet",bare.toString(),a.toString());config(a);write(a,"x","base");commit(a,"base");git(a,"push","-u","origin","HEAD");git(temp,"clone","--quiet",bare.toString(),b.toString());config(b);
        write(a,"ahead","a");commit(a,"ahead");assertThat(inspect(a).ahead()).isEqualTo(1);
        write(b,"behind","b");commit(b,"remote");git(b,"push");git(a,"fetch","--quiet");var diverged=inspect(a);assertThat(diverged.ahead()).isEqualTo(1);assertThat(diverged.behind()).isEqualTo(1);assertThat(diverged.diverged()).isTrue();git(a,"reset","--hard","origin/master");assertThat(inspect(a).behind()).isZero();
    }
    @Test void detectsMergeConflict()throws Exception{Path p=init("conflict");write(p,"x","base");commit(p,"base");git(p,"checkout","-b","other","--quiet");write(p,"x","other");commit(p,"other");git(p,"checkout","master","--quiet");write(p,"x","main");commit(p,"main");runAllowFailure(p,"merge","other");var s=inspect(p);assertThat(s.conflicts()).isPositive();assertThat(s.state()).isEqualTo(RepositoryState.CONFLICT);}
    private Path init(String n)throws Exception{Path p=Files.createDirectory(temp.resolve(n));git(p,"init","--quiet","--initial-branch=master");config(p);return p;}private void config(Path p)throws Exception{git(p,"config","user.email","test@example.com");git(p,"config","user.name","Test User");}
    private void write(Path p,String f,String v)throws Exception{Files.writeString(p.resolve(f),v);}private void commit(Path p,String m)throws Exception{git(p,"add","-A");git(p,"commit","--quiet","-m",m);}private dev.nihit.gitdash.model.RepositoryStatus inspect(Path p){return inspector.inspect(new Repository(p.getFileName().toString(),p,Instant.now()));}
    private static void git(Path p,String...args)throws Exception{var all=new java.util.ArrayList<String>();all.add("git");all.addAll(java.util.List.of(args));var process=new ProcessBuilder(all).directory(p.toFile()).redirectErrorStream(true).start();String out=new String(process.getInputStream().readAllBytes());if(process.waitFor()!=0)throw new AssertionError(out);}private static void runAllowFailure(Path p,String...args)throws Exception{var all=new java.util.ArrayList<String>();all.add("git");all.addAll(java.util.List.of(args));new ProcessBuilder(all).directory(p.toFile()).redirectErrorStream(true).start().waitFor();}
}
