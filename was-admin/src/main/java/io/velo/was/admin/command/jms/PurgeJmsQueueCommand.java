package io.velo.was.admin.command.jms;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class PurgeJmsQueueCommand implements Command {

    @Override
    public String name() {
        return "purge-jms-queue";
    }

    @Override
    public String description() {
        return "Purge all messages from a JMS queue";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.JMS;
    }

    @Override
    public String usage() {
        return "purge-jms-queue <queue-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().purgeJmsQueue(args[0]);
            return CommandResult.ok("JMS queue '" + args[0] + "' purged.");
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
