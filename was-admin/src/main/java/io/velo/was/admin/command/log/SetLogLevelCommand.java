package io.velo.was.admin.command.log;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class SetLogLevelCommand implements Command {

    @Override
    public String name() {
        return "set-log-level";
    }

    @Override
    public String description() {
        return "Set logger level";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.LOG;
    }

    @Override
    public String usage() {
        return "set-log-level <logger-name> <level>";
    }

    @Override
    public String detailedHelp() {
        return """
                Change the log level for a specific logger.

                Usage:
                  set-log-level <logger-name> <level>

                Supported levels:
                  TRACE, DEBUG, INFO, WARN, ERROR, OFF

                Examples:
                  set-log-level io.velo.was DEBUG
                  set-log-level ROOT WARN""";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 2) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().setLogLevel(args[0], args[1]);
            return CommandResult.ok("Logger '" + args[0] + "' level changed to " + args[1].toUpperCase());
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
