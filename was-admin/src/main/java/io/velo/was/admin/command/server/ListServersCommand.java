package io.velo.was.admin.command.server;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

import java.util.List;

public class ListServersCommand implements Command {

    @Override
    public String name() {
        return "list-servers";
    }

    @Override
    public String description() {
        return "List all servers";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.SERVER;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        List<AdminClient.ServerSummary> servers = context.client().listServers();
        if (servers.isEmpty()) {
            return CommandResult.ok("No servers found.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-20s %-15s %-10s%n", "NAME", "NODE ID", "STATUS"));
        sb.append("-".repeat(50)).append("\n");
        for (AdminClient.ServerSummary server : servers) {
            sb.append(String.format("%-20s %-15s %-10s%n", server.name(), server.nodeId(), server.status()));
        }
        return CommandResult.ok(sb.toString());
    }
}
