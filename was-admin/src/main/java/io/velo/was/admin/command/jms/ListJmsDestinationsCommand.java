package io.velo.was.admin.command.jms;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

import java.util.List;

public class ListJmsDestinationsCommand implements Command {

    @Override
    public String name() {
        return "list-jms-destinations";
    }

    @Override
    public String description() {
        return "List JMS destinations";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.JMS;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        List<AdminClient.JmsDestinationSummary> destinations = context.client().listJmsDestinations();
        if (destinations.isEmpty()) {
            return CommandResult.ok("No JMS destinations configured.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-20s %-10s %-10s%n", "NAME", "TYPE", "MESSAGES"));
        sb.append("-".repeat(42)).append("\n");
        for (AdminClient.JmsDestinationSummary d : destinations) {
            sb.append(String.format("%-20s %-10s %-10d%n", d.name(), d.type(), d.messageCount()));
        }
        return CommandResult.ok(sb.toString().stripTrailing());
    }
}
