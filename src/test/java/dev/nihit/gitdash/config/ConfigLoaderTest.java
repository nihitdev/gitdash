package dev.nihit.gitdash.config;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import java.nio.file.Files;import java.nio.file.Path;import static org.assertj.core.api.Assertions.*;
class ConfigLoaderTest{@TempDir Path temp;@Test void parsesSettingsAliasesAndGroups()throws Exception{Path dir=temp.resolve("config");Files.createDirectories(dir);Files.writeString(dir.resolve("config.toml"),"""
[scan]
max_depth = 4
follow_symlinks = true
exclude = ["ignored"]
[status]
stale_days = 12
[concurrency]
max_parallel = 7
[aliases]
demo = "/tmp/demo"
[groups]
work = ["demo"]
""");var c=new ConfigLoader().load(defaults(dir));assertThat(c.maxDepth()).isEqualTo(4);assertThat(c.followSymlinks()).isTrue();assertThat(c.aliases()).containsEntry("demo","/tmp/demo");assertThat(c.groups().get("work")).containsExactly("demo");}@Test void rejectsMalformedConfiguration()throws Exception{Path dir=temp.resolve("config");Files.createDirectories(dir);Files.writeString(dir.resolve("config.toml"),"[scan\n");assertThatThrownBy(()->new ConfigLoader().load(defaults(dir))).hasMessageContaining("Invalid configuration");}private Config defaults(Path dir){var d=Config.defaults();return new Config(dir,temp.resolve("state"),temp.resolve("cache"),d.maxDepth(),d.followSymlinks(),d.exclusions(),d.staleDays(),d.color(),d.unicode(),d.concurrencyEnabled(),d.maxParallel(),d.aliases(),d.groups());}}
