package io.velo.was.admin.command.datasource;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

import java.util.List;

public class ListDatasourcesCommand implements Command {

    @Override
    public String name() {
        return "list-datasources";
    }

    @Override
    public String description() {
        return "List all datasources";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.DATASOURCE;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        List<AdminClient.DatasourceSummary> datasources = context.client().listDatasources();
        if (datasources.isEmpty()) {
            return CommandResult.ok("No datasources configured.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-25s %-15s %-10s%n", "NAME", "TYPE", "STATUS"));
        sb.append("-".repeat(55)).append("\n");
        for (AdminClient.DatasourceSummary ds : datasources) {
            sb.append(String.format("%-25s %-15s %-10s%n", ds.name(), ds.type(), ds.status()));
        }
        return CommandResult.ok(sb.toString());
    }
}
