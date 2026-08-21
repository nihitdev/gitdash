package dev.nihit.gitdash.cli;

import java.util.Locale;

final class CompletionScripts {
    private CompletionScripts() {}
    private static final String COMMANDS = "scan status summary dirty clean ahead behind conflicts stale repos show remove prune fetch doctor config cache completion benchmark update";
    private static final String STATUS_OPTIONS = "--dirty --clean --ahead --behind --conflicts --detached --no-upstream --no-remote --invalid --stale --stale-days --branch --remote --group --sort --reverse --limit --json --porcelain";

    static String forShell(String shell) {
        return switch (shell.toLowerCase(Locale.ROOT)) {
            case "bash" -> bash(); case "zsh" -> zsh(); case "fish" -> fish();
            case "powershell", "pwsh" -> powershell();
            default -> throw new IllegalArgumentException("Supported shells: bash, zsh, fish, powershell");
        };
    }
    private static String bash() { return """
            _gitdash() {
              local current previous command
              COMPREPLY=()
              current="${COMP_WORDS[COMP_CWORD]}"
              previous="${COMP_WORDS[COMP_CWORD-1]}"
              command="${COMP_WORDS[1]}"
              if (( COMP_CWORD == 1 )); then
                COMPREPLY=( $(compgen -W 'COMMANDS' -- "$current") ); return
              fi
              case "$command" in
                status|dirty|clean|ahead|behind|conflicts) COMPREPLY=( $(compgen -W 'STATUS_OPTIONS' -- "$current") ) ;;
                show|remove) COMPREPLY=( $(compgen -W "$(gitdash repos --names 2>/dev/null)" -- "$current") ) ;;
                fetch) COMPREPLY=( $(compgen -W "--all --group $(gitdash repos --names 2>/dev/null)" -- "$current") ) ;;
                repos) COMPREPLY=( $(compgen -W '--search --missing --json --names rename export import' -- "$current") ) ;;
                config) COMPREPLY=( $(compgen -W 'alias unalias' -- "$current") ) ;;
                completion) COMPREPLY=( $(compgen -W 'bash zsh fish powershell' -- "$current") ) ;;
              esac
            }
            complete -F _gitdash gitdash
            """.replace("COMMANDS", COMMANDS).replace("STATUS_OPTIONS", STATUS_OPTIONS); }
    private static String zsh() { return """
            #compdef gitdash
            _gitdash() {
              local -a commands repos status_options
              commands=(COMMANDS)
              status_options=(STATUS_OPTIONS)
              repos=( ${(f)"$(gitdash repos --names 2>/dev/null)"} )
              if (( CURRENT == 2 )); then _describe 'command' commands; return; fi
              case $words[2] in
                status|dirty|clean|ahead|behind|conflicts) _describe 'option' status_options ;;
                show|remove) _describe 'repository' repos ;;
                fetch) _arguments '--all[fetch all]' '--group=[group]' '1:repository:($repos)' ;;
                repos) _values 'action' rename export import --search --missing --json --names ;;
                config) _values 'action' alias unalias ;;
                completion) _values 'shell' bash zsh fish powershell ;;
              esac
            }
            compdef _gitdash gitdash
            """.replace("COMMANDS", COMMANDS).replace("STATUS_OPTIONS", STATUS_OPTIONS); }
    private static String fish() {
        var b = new StringBuilder("complete -c gitdash -f\n");
        for (String command : COMMANDS.split(" ")) b.append("complete -c gitdash -n '__fish_use_subcommand' -a '").append(command).append("'\n");
        b.append("complete -c gitdash -n '__fish_seen_subcommand_from show remove fetch' -a '(gitdash repos --names 2>/dev/null)'\n");
        b.append("complete -c gitdash -n '__fish_seen_subcommand_from completion' -a 'bash zsh fish powershell'\n");
        b.append("complete -c gitdash -n '__fish_seen_subcommand_from repos' -a 'rename export import'\n");
        b.append("complete -c gitdash -n '__fish_seen_subcommand_from config' -a 'alias unalias'\n");
        return b.toString();
    }
    private static String powershell() { return """
            Register-ArgumentCompleter -Native -CommandName gitdash -ScriptBlock {
              param($wordToComplete, $commandAst, $cursorPosition)
              $words = @($commandAst.CommandElements | ForEach-Object { $_.Extent.Text })
              $candidates = if ($words.Count -le 1) {
                'COMMANDS' -split ' '
              } else {
                switch ($words[1]) {
                  { $_ -in @('show','remove') } { @(gitdash repos --names 2>$null); break }
                  'fetch' { @('--all','--group') + @(gitdash repos --names 2>$null); break }
                  { $_ -in @('status','dirty','clean','ahead','behind','conflicts') } { 'STATUS_OPTIONS' -split ' '; break }
                  'repos' { @('--search','--missing','--json','--names','rename','export','import'); break }
                  'config' { @('alias','unalias'); break }
                  'completion' { @('bash','zsh','fish','powershell'); break }
                  default { @() }
                }
              }
              $candidates | Where-Object { $_ -like "$wordToComplete*" } | ForEach-Object {
                [System.Management.Automation.CompletionResult]::new($_, $_, 'ParameterValue', $_)
              }
            }
            """.replace("COMMANDS", COMMANDS).replace("STATUS_OPTIONS", STATUS_OPTIONS); }
}
