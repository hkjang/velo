package io.velo.was.admin.command.server;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class SuspendServerCommand implements Command {

    @Override
    public String name() {
        return "suspend-server";
    }

    @Override
    public String description() {
        return "Suspend a server";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.SERVER;
    }

    @Override
    public String usage() {
        return "suspend-server <server-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().suspendServer(args[0]);
            return CommandResult.ok("Server '" + args[0] + "' suspended successfully.");
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
