package io.velo.was.admin.command.cluster;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class RestartClusterCommand implements Command {

    @Override
    public String name() {
        return "restart-cluster";
    }

    @Override
    public String description() {
        return "Restart a cluster";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.CLUSTER;
    }

    @Override
    public String usage() {
        return "restart-cluster <cluster-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().restartCluster(args[0]);
            return CommandResult.ok("Cluster '" + args[0] + "' restarted successfully.");
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
