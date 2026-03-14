package io.velo.was.admin.command.domain;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

import java.util.List;

public class ListDomainsCommand implements Command {

    @Override
    public String name() {
        return "list-domains";
    }

    @Override
    public String description() {
        return "List all domains";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.DOMAIN;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        List<AdminClient.DomainSummary> domains = context.client().listDomains();
        if (domains.isEmpty()) {
            return CommandResult.ok("No domains configured.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-20s %-10s%n", "NAME", "STATUS"));
        sb.append("-".repeat(32)).append("\n");
        for (AdminClient.DomainSummary d : domains) {
            sb.append(String.format("%-20s %-10s%n", d.name(), d.status()));
        }
        return CommandResult.ok(sb.toString().stripTrailing());
    }
}
