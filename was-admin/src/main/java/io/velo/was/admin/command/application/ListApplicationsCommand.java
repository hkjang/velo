package io.velo.was.admin.command.application;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

import java.util.List;

public class ListApplicationsCommand implements Command {

    @Override
    public String name() {
        return "list-applications";
    }

    @Override
    public String description() {
        return "List deployed applications";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.APPLICATION;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        List<AdminClient.AppSummary> apps = context.client().listApplications();
        if (apps.isEmpty()) {
            return CommandResult.ok("No applications deployed.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-25s %-20s %-10s%n", "NAME", "CONTEXT PATH", "STATUS"));
        sb.append("-".repeat(60)).append("\n");
        for (AdminClient.AppSummary app : apps) {
            sb.append(String.format("%-25s %-20s %-10s%n", app.name(), app.contextPath(), app.status()));
        }
        return CommandResult.ok(sb.toString());
    }
}
