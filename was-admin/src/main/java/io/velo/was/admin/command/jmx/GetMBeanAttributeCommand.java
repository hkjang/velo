package io.velo.was.admin.command.jmx;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class GetMBeanAttributeCommand implements Command {

    @Override
    public String name() {
        return "get-mbean-attribute";
    }

    @Override
    public String description() {
        return "Get an MBean attribute value";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.JMX;
    }

    @Override
    public String usage() {
        return "get-mbean-attribute <mbean-name> <attribute>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 2) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            String value = context.client().getMBeanAttribute(args[0], args[1]);
            return CommandResult.ok(args[1] + " = " + value);
        } catch (RuntimeException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
