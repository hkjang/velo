package io.velo.was.admin.command.log;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class GetLogLevelCommand implements Command {

    @Override
    public String name() {
        return "get-log-level";
    }

    @Override
    public String description() {
        return "Get logger level";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.LOG;
    }

    @Override
    public String usage() {
        return "get-log-level <logger-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            String level = context.client().getLogLevel(args[0]);
            return CommandResult.ok("Logger '" + args[0] + "' level: " + level);
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
