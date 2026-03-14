package io.velo.was.admin.command.jdbc;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class FlushConnectionPoolCommand implements Command {

    @Override
    public String name() {
        return "flush-connection-pool";
    }

    @Override
    public String description() {
        return "Flush a connection pool";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.JDBC;
    }

    @Override
    public String usage() {
        return "flush-connection-pool <pool-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().flushConnectionPool(args[0]);
            return CommandResult.ok("Connection pool '" + args[0] + "' flushed successfully.");
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
