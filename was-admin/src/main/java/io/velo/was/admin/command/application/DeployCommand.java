package io.velo.was.admin.command.application;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class DeployCommand implements Command {

    @Override
    public String name() {
        return "deploy";
    }

    @Override
    public String description() {
        return "Deploy an application";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.APPLICATION;
    }

    @Override
    public String usage() {
        return "deploy <war-path> [context-path]";
    }

    @Override
    public String detailedHelp() {
        return """
                Deploy a web application.

                Usage:
                  deploy <war-path>                - Deploy with default context path
                  deploy <war-path> <context-path> - Deploy with specified context path

                Examples:
                  deploy /path/to/app.war
                  deploy /path/to/app.war /myapp""";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        String warPath = args[0];
        String contextPath = args.length > 1 ? args[1] : null;
        try {
            context.client().deploy(warPath, contextPath);
            return CommandResult.ok("Application deployed successfully: " + warPath);
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
