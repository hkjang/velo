package io.velo.was.admin.command.thread;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

import java.util.List;

public class ListThreadPoolsCommand implements Command {

    @Override
    public String name() {
        return "list-thread-pools";
    }

    @Override
    public String description() {
        return "List thread pools";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.THREAD;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        List<AdminClient.ThreadPoolSummary> pools = context.client().listThreadPools();
        if (pools.isEmpty()) {
            return CommandResult.ok("No thread pools found.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-15s %-10s %-10s %-10s%n", "NAME", "ACTIVE", "SIZE", "MAX"));
        sb.append("-".repeat(50)).append("\n");
        for (AdminClient.ThreadPoolSummary pool : pools) {
            sb.append(String.format("%-15s %-10d %-10d %-10d%n",
                    pool.name(), pool.activeCount(), pool.poolSize(), pool.maxPoolSize()));
        }
        return CommandResult.ok(sb.toString());
    }
}
