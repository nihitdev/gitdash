package dev.nihit.gitdash.cli;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class CompletionScriptsTest {
 @Test void generatesContextualScriptsForEverySupportedShell(){for(String shell:new String[]{"bash","zsh","fish","powershell"})assertThat(CompletionScripts.forShell(shell)).contains("gitdash").contains("repos").contains("completion");}
 @Test void rejectsUnknownShell(){assertThatThrownBy(()->CompletionScripts.forShell("cmd")).isInstanceOf(IllegalArgumentException.class);}
}
