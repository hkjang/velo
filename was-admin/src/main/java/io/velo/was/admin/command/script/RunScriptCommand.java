package io.velo.was.admin.command.script;

import io.velo.was.admin.cli.CliShell;
import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandRegistry;
import io.velo.was.admin.cli.CommandResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RunScriptCommand implements Command {

    private final CommandRegistry registry;

    public RunScriptCommand(CommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "run-script";
    }

    @Override
    public String description() {
        return "Execute CLI script file";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.SCRIPT;
    }

    @Override
    public String usage() {
        return "run-script <script-file>";
    }

    @Override
    public String detailedHelp() {
        return """
                Execute a CLI script file containing veloadmin commands.
                Lines starting with '#' are treated as comments.
                Empty lines are ignored.

                Usage:
                  run-script <script-file>

                Example:
                  run-script /path/to/deploy-all.velo""";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        Path scriptPath = Path.of(args[0]);
        if (!Files.exists(scriptPath)) {
            return CommandResult.error("Script file not found: " + scriptPath);
        }
        try {
            List<String> lines = Files.readAllLines(scriptPath, StandardCharsets.UTF_8);
            CliShell shell = new CliShell(registry, context, "");
            shell.runScript(lines);
            return CommandResult.ok("Script execution completed: " + scriptPath);
        } catch (IOException e) {
            return CommandResult.error("Failed to read script: " + e.getMessage());
        }
    }
}
