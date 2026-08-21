package dev.nihit.gitdash.git;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
class GitClientTest {@TempDir Path temp;
 @Test void redactsCredentialsAndTokens(){assertThat(GitClient.redact("https://user:secret@example.com/x.git?access_token=abc&x=1")).isEqualTo("https://***@example.com/x.git?access_token=***&x=1");}
 @Test void timesOutAndTerminatesProcess()throws Exception{var result=new GitClient(Duration.ofMillis(100)).run(temp,"hash-object","--stdin");assertThat(result.timedOut()).isTrue();assertThat(result.exitCode()).isEqualTo(-1);}
}
