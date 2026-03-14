package io.velo.was.admin.command.application;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

public class ApplicationInfoCommand implements Command {

    @Override
    public String name() {
        return "application-info";
    }

    @Override
    public String description() {
        return "Show application information";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.APPLICATION;
    }

    @Override
    public String usage() {
        return "application-info <app-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            AdminClient.AppStatus status = context.client().applicationInfo(args[0]);
            StringBuilder sb = new StringBuilder();
            sb.append("Application Name  : ").append(status.name()).append("\n");
            sb.append("Context Path      : ").append(status.contextPath()).append("\n");
            sb.append("Status            : ").append(status.status()).append("\n");
            sb.append("Servlet Count     : ").append(status.servletCount()).append("\n");
            sb.append("Filter Count      : ").append(status.filterCount());
            return CommandResult.ok(sb.toString());
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
