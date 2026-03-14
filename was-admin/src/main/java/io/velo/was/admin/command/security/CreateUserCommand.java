package io.velo.was.admin.command.security;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class CreateUserCommand implements Command {

    @Override
    public String name() {
        return "create-user";
    }

    @Override
    public String description() {
        return "Create a new user";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.SECURITY;
    }

    @Override
    public String usage() {
        return "create-user <username> <password>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 2) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().createUser(args[0], args[1]);
            return CommandResult.ok("User '" + args[0] + "' created successfully.");
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
