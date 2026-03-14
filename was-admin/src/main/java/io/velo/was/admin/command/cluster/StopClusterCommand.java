package io.velo.was.admin.command.cluster;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class StopClusterCommand implements Command {

    @Override
    public String name() {
        return "stop-cluster";
    }

    @Override
    public String description() {
        return "Stop a cluster";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.CLUSTER;
    }

    @Override
    public String usage() {
        return "stop-cluster <cluster-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().stopCluster(args[0]);
            return CommandResult.ok("Cluster '" + args[0] + "' stopped successfully.");
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
