package io.velo.was.admin.command.cluster;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class StartClusterCommand implements Command {

    @Override
    public String name() {
        return "start-cluster";
    }

    @Override
    public String description() {
        return "Start a cluster";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.CLUSTER;
    }

    @Override
    public String usage() {
        return "start-cluster <cluster-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().startCluster(args[0]);
            return CommandResult.ok("Cluster '" + args[0] + "' started successfully.");
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
