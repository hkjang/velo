package io.velo.was.admin.command.application;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class UndeployCommand implements Command {

    @Override
    public String name() {
        return "undeploy";
    }

    @Override
    public String description() {
        return "Undeploy an application";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.APPLICATION;
    }

    @Override
    public String usage() {
        return "undeploy <app-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().undeploy(args[0]);
            return CommandResult.ok("Application '" + args[0] + "' undeployed successfully.");
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
