package io.velo.was.admin.command.domain;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class SetDomainPropertyCommand implements Command {

    @Override
    public String name() {
        return "set-domain-property";
    }

    @Override
    public String description() {
        return "Set a domain property";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.DOMAIN;
    }

    @Override
    public String usage() {
        return "set-domain-property <domain-name> <key> <value>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 3) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().setDomainProperty(args[0], args[1], args[2]);
            return CommandResult.ok("Property '" + args[1] + "' set to '" + args[2] + "' on domain '" + args[0] + "'.");
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
