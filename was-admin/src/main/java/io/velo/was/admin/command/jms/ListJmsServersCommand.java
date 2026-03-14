package io.velo.was.admin.command.jms;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

import java.util.List;

public class ListJmsServersCommand implements Command {

    @Override
    public String name() {
        return "list-jms-servers";
    }

    @Override
    public String description() {
        return "List JMS servers";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.JMS;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        List<AdminClient.JmsServerSummary> servers = context.client().listJmsServers();
        if (servers.isEmpty()) {
            return CommandResult.ok("No JMS servers configured.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-20s %-10s%n", "NAME", "STATUS"));
        sb.append("-".repeat(32)).append("\n");
        for (AdminClient.JmsServerSummary s : servers) {
            sb.append(String.format("%-20s %-10s%n", s.name(), s.status()));
        }
        return CommandResult.ok(sb.toString().stripTrailing());
    }
}
