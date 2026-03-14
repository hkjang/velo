package io.velo.was.admin.command.cluster;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class RemoveServerFromClusterCommand implements Command {

    @Override
    public String name() {
        return "remove-server-from-cluster";
    }

    @Override
    public String description() {
        return "Remove a server from a cluster";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.CLUSTER;
    }

    @Override
    public String usage() {
        return "remove-server-from-cluster <cluster-name> <server-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 2) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().removeServerFromCluster(args[0], args[1]);
            return CommandResult.ok("Server '" + args[1] + "' removed from cluster '" + args[0] + "'.");
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
