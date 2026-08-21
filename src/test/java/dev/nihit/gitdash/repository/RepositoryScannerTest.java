package dev.nihit.gitdash.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryScannerTest {
    @TempDir Path temp;
    @Test void discoversDirectoriesAndWorktreeFilesAndSkipsNestedByDefault() throws Exception {
        Files.createDirectories(temp.resolve("one/.git")); Files.createDirectories(temp.resolve("one/nested/.git"));
        Files.createDirectories(temp.resolve("two")); Files.writeString(temp.resolve("two/.git"), "gitdir: ../somewhere");
        var result=new RepositoryScanner().scan(temp,8,Set.of(),false,false);
        assertThat(result.repositories()).extracting(r->r.name()).containsExactly("one","two");
    }
    @Test void nestedModeFindsNestedRepository() throws Exception {
        Files.createDirectories(temp.resolve("one/.git")); Files.createDirectories(temp.resolve("one/nested/.git"));
        assertThat(new RepositoryScanner().scan(temp,8,Set.of(),false,true).repositories()).hasSize(2);
    }
    @Test void honorsExclusionsAndDepth() throws Exception {
        Files.createDirectories(temp.resolve("node_modules/no/.git")); Files.createDirectories(temp.resolve("a/b/c/.git"));
        assertThat(new RepositoryScanner().scan(temp,2,Set.of("node_modules"),false,false).repositories()).isEmpty();
        assertThat(new RepositoryScanner().scan(temp,3,Set.of("node_modules"),false,false).repositories()).hasSize(1);
    }
    @Test void doesNotFollowSymlinksByDefault() throws Exception {
        Path outside=Files.createDirectory(temp.resolve("outside"));Files.createDirectory(outside.resolve(".git"));Files.createSymbolicLink(temp.resolve("link"),outside);
        assertThat(new RepositoryScanner().scan(temp.resolve("link").getParent(),3,Set.of("outside"),false,false).repositories()).isEmpty();
    }
}
