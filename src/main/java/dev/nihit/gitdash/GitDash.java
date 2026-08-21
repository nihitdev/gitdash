package dev.nihit.gitdash;

import dev.nihit.gitdash.cli.AppContext;
import dev.nihit.gitdash.cli.RootCommand;
import dev.nihit.gitdash.config.ConfigLoader;
import picocli.CommandLine;

public final class GitDash {
    private GitDash() {}
    public static void main(String[] args) {
        int code;
        try {
            var command = new CommandLine(new RootCommand(AppContext.create(new ConfigLoader().load())));
            command.setExecutionExceptionHandler((error, cmd, parse) -> {
                cmd.getErr().println("gitdash: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
                if (cmd.getParseResult() != null && cmd.getParseResult().hasMatchedOption("--debug")) error.printStackTrace(cmd.getErr());
                return cmd.getCommandSpec().exitCodeOnExecutionException();
            });
            code = command.execute(args);
        } catch (Exception e) {
            System.err.println("gitdash: " + e.getMessage()); code = 1;
        }
        System.exit(code);
    }
}
