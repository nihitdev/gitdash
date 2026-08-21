package dev.nihit.gitdash.git;

import dev.nihit.gitdash.model.Repository;
import dev.nihit.gitdash.model.RepositoryState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledOnOs(OS.WINDOWS)
class WindowsGitIntegrationTest {
    @TempDir Path temp;

    @Test void inspectsCleanDirtyAndDetachedRepositoriesWithGitForWindows() throws Exception {
        Path repository = Files.createDirectory(temp.resolve("windows-repo"));
        git(repository,"init","--quiet","--initial-branch=main"); git(repository,"config","user.name","Windows Test"); git(repository,"config","user.email","windows@example.com");
        Files.writeString(repository.resolve("tracked.txt"),"clean\n"); git(repository,"add","tracked.txt"); git(repository,"commit","--quiet","-m","initial");
        var inspector = new RepositoryInspector(new GitClient(Duration.ofSeconds(10)));
        var registered = new Repository("windows-repo",repository,Instant.now());
        assertThat(inspector.inspect(registered).state()).isEqualTo(RepositoryState.CLEAN);
        Files.writeString(repository.resolve("tracked.txt"),"dirty\n"); Files.writeString(repository.resolve("untracked.txt"),"new\n");
        var dirty=inspector.inspect(registered); assertThat(dirty.state()).isEqualTo(RepositoryState.DIRTY); assertThat(dirty.modified()).isEqualTo(1); assertThat(dirty.untracked()).isEqualTo(1);
        git(repository,"checkout","--detach","--quiet"); assertThat(inspector.inspect(registered).detached()).isTrue();
    }
    private static void git(Path directory,String... arguments)throws Exception{var command=new ArrayList<String>();command.add("git.exe");command.addAll(List.of(arguments));var process=new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();String output=new String(process.getInputStream().readAllBytes());if(process.waitFor()!=0)throw new AssertionError(output);}
}
