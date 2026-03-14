package io.velo.was.admin.command.cluster;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class AddServerToClusterCommand implements Command {

    @Override
    public String name() {
        return "add-server-to-cluster";
    }

    @Override
    public String description() {
        return "Add a server to a cluster";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.CLUSTER;
    }

    @Override
    public String usage() {
        return "add-server-to-cluster <cluster-name> <server-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 2) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().addServerToCluster(args[0], args[1]);
            return CommandResult.ok("Server '" + args[1] + "' added to cluster '" + args[0] + "'.");
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
