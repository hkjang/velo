package io.velo.was.admin.command.domain;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class RemoveDomainCommand implements Command {

    @Override
    public String name() {
        return "remove-domain";
    }

    @Override
    public String description() {
        return "Remove a domain";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.DOMAIN;
    }

    @Override
    public String usage() {
        return "remove-domain <domain-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().removeDomain(args[0]);
            return CommandResult.ok("Domain '" + args[0] + "' removed successfully.");
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
