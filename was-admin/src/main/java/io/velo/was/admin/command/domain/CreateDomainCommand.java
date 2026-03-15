package io.velo.was.admin.command.domain;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class CreateDomainCommand implements Command {

    @Override
    public String name() {
        return "create-domain";
    }

    @Override
    public String description() {
        return "Create a domain";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.DOMAIN;
    }

    @Override
    public String usage() {
        return "create-domain <domain-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().createDomain(args[0]);
            return CommandResult.ok("Domain '" + args[0] + "' created successfully.");
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
