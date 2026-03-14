package io.velo.was.admin.command.jmx;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class InvokeMBeanOperationCommand implements Command {

    @Override
    public String name() {
        return "invoke-mbean-operation";
    }

    @Override
    public String description() {
        return "Invoke an MBean operation";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.JMX;
    }

    @Override
    public String usage() {
        return "invoke-mbean-operation <mbean-name> <operation> [params...]";
    }

    @Override
    public String detailedHelp() {
        return """
                Invoke an operation on a JMX MBean.

                Usage:
                  invoke-mbean-operation <mbean-name> <operation> [param1] [param2] ...

                Parameters are passed as strings. The MBean server will attempt
                type conversion based on the operation signature.""";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 2) {
            return CommandResult.error("Usage: " + usage());
        }
        String mbeanName = args[0];
        String operation = args[1];
        String[] params = new String[args.length - 2];
        System.arraycopy(args, 2, params, 0, params.length);

        try {
            String result = context.client().invokeMBeanOperation(mbeanName, operation, params);
            return CommandResult.ok("Result: " + result);
        } catch (RuntimeException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
