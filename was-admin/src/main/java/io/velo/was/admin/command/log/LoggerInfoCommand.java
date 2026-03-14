package io.velo.was.admin.command.log;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

public class LoggerInfoCommand implements Command {

    @Override
    public String name() {
        return "logger-info";
    }

    @Override
    public String description() {
        return "Show logger information";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.LOG;
    }

    @Override
    public String usage() {
        return "logger-info <logger-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            AdminClient.LoggerStatus status = context.client().loggerInfo(args[0]);
            StringBuilder sb = new StringBuilder();
            sb.append("Logger Name       : ").append(status.name()).append("\n");
            sb.append("Level             : ").append(status.level()).append("\n");
            sb.append("Effective Level   : ").append(status.effectiveLevel()).append("\n");
            sb.append("Additivity        : ").append(status.additivity()).append("\n");
            sb.append("Handlers          : ").append(String.join(", ", status.handlers()));
            return CommandResult.ok(sb.toString());
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
