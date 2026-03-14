package io.velo.was.admin.command.domain;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class GetDomainPropertyCommand implements Command {

    @Override
    public String name() {
        return "get-domain-property";
    }

    @Override
    public String description() {
        return "Get a domain property";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.DOMAIN;
    }

    @Override
    public String usage() {
        return "get-domain-property <domain-name> <key>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 2) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            String value = context.client().getDomainProperty(args[0], args[1]);
            return CommandResult.ok(args[1] + " = " + value);
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
