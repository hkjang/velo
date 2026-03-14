package io.velo.was.admin.command.security;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class RemoveUserCommand implements Command {

    @Override
    public String name() {
        return "remove-user";
    }

    @Override
    public String description() {
        return "Remove a user";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.SECURITY;
    }

    @Override
    public String usage() {
        return "remove-user <username>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().removeUser(args[0]);
            return CommandResult.ok("User '" + args[0] + "' removed successfully.");
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
