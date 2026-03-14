package io.velo.was.admin.command.jmx;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class SetMBeanAttributeCommand implements Command {

    @Override
    public String name() {
        return "set-mbean-attribute";
    }

    @Override
    public String description() {
        return "Set an MBean attribute value";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.JMX;
    }

    @Override
    public String usage() {
        return "set-mbean-attribute <mbean-name> <attribute> <value>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 3) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().setMBeanAttribute(args[0], args[1], args[2]);
            return CommandResult.ok("Attribute '" + args[1] + "' set to '" + args[2] + "'.");
        } catch (RuntimeException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
