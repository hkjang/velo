package io.velo.was.admin.command.jdbc;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

import java.util.List;

public class ListJdbcResourcesCommand implements Command {

    @Override
    public String name() {
        return "list-jdbc-resources";
    }

    @Override
    public String description() {
        return "List JDBC resources";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.JDBC;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        List<AdminClient.JdbcResourceSummary> resources = context.client().listJdbcResources();
        if (resources.isEmpty()) {
            return CommandResult.ok("No JDBC resources configured.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-20s %-20s %-10s%n", "NAME", "POOL", "TYPE"));
        sb.append("-".repeat(52)).append("\n");
        for (AdminClient.JdbcResourceSummary r : resources) {
            sb.append(String.format("%-20s %-20s %-10s%n", r.name(), r.poolName(), r.type()));
        }
        return CommandResult.ok(sb.toString().stripTrailing());
    }
}
