package io.velo.was.admin.command.thread;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

public class ThreadPoolInfoCommand implements Command {

    @Override
    public String name() {
        return "thread-pool-info";
    }

    @Override
    public String description() {
        return "Show thread pool information";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.THREAD;
    }

    @Override
    public String usage() {
        return "thread-pool-info <pool-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            AdminClient.ThreadPoolStatus status = context.client().threadPoolInfo(args[0]);
            StringBuilder sb = new StringBuilder();
            sb.append("Pool Name           : ").append(status.name()).append("\n");
            sb.append("Active Count        : ").append(status.activeCount()).append("\n");
            sb.append("Pool Size           : ").append(status.poolSize()).append("\n");
            sb.append("Max Pool Size       : ").append(status.maxPoolSize()).append("\n");
            sb.append("Completed Tasks     : ").append(status.completedTaskCount()).append("\n");
            sb.append("Queue Size          : ").append(status.queueSize());
            return CommandResult.ok(sb.toString());
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
