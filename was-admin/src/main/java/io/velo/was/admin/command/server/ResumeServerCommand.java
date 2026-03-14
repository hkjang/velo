package io.velo.was.admin.command.server;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class ResumeServerCommand implements Command {

    @Override
    public String name() {
        return "resume-server";
    }

    @Override
    public String description() {
        return "Resume a suspended server";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.SERVER;
    }

    @Override
    public String usage() {
        return "resume-server <server-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().resumeServer(args[0]);
            return CommandResult.ok("Server '" + args[0] + "' resumed successfully.");
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
