package io.velo.was.admin.command.cluster;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

public class ClusterInfoCommand implements Command {

    @Override
    public String name() {
        return "cluster-info";
    }

    @Override
    public String description() {
        return "Show cluster information";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.CLUSTER;
    }

    @Override
    public String usage() {
        return "cluster-info <cluster-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            AdminClient.ClusterStatus status = context.client().clusterInfo(args[0]);
            StringBuilder sb = new StringBuilder();
            sb.append("Cluster Name  : ").append(status.name()).append("\n");
            sb.append("Status        : ").append(status.status()).append("\n");
            sb.append("Members       : ").append(String.join(", ", status.members()));
            return CommandResult.ok(sb.toString());
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
