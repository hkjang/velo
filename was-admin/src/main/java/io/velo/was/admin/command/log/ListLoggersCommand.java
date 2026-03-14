package io.velo.was.admin.command.log;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

import java.util.List;

public class ListLoggersCommand implements Command {

    @Override
    public String name() {
        return "list-loggers";
    }

    @Override
    public String description() {
        return "List all loggers";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.LOG;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        List<AdminClient.LoggerSummary> loggers = context.client().listLoggers();
        if (loggers.isEmpty()) {
            return CommandResult.ok("No loggers found.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-40s %-10s%n", "LOGGER", "LEVEL"));
        sb.append("-".repeat(55)).append("\n");
        for (AdminClient.LoggerSummary logger : loggers) {
            sb.append(String.format("%-40s %-10s%n", logger.name(), logger.level()));
        }
        return CommandResult.ok(sb.toString());
    }
}
