package io.velo.was.admin.command.cluster;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

import java.util.List;

public class ListClustersCommand implements Command {

    @Override
    public String name() {
        return "list-clusters";
    }

    @Override
    public String description() {
        return "List all clusters";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.CLUSTER;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        List<AdminClient.ClusterSummary> clusters = context.client().listClusters();
        if (clusters.isEmpty()) {
            return CommandResult.ok("No clusters configured.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-20s %-10s %-10s%n", "NAME", "MEMBERS", "STATUS"));
        sb.append("-".repeat(45)).append("\n");
        for (AdminClient.ClusterSummary cluster : clusters) {
            sb.append(String.format("%-20s %-10d %-10s%n", cluster.name(), cluster.memberCount(), cluster.status()));
        }
        return CommandResult.ok(sb.toString());
    }
}
