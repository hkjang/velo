package io.velo.was.admin.command.domain;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

import java.util.Map;

public class DomainInfoCommand implements Command {

    @Override
    public String name() {
        return "domain-info";
    }

    @Override
    public String description() {
        return "Show domain information";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.DOMAIN;
    }

    @Override
    public String usage() {
        return "domain-info [domain-name]";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        String domainName = args.length > 0 ? args[0] : "default";
        try {
            AdminClient.DomainStatus status = context.client().domainInfo(domainName);
            StringBuilder sb = new StringBuilder();
            sb.append("Domain Name        : ").append(status.name()).append("\n");
            sb.append("Status             : ").append(status.status()).append("\n");
            sb.append("Admin Server       : ").append(status.adminServerName()).append("\n");
            sb.append("Server Count       : ").append(status.serverCount()).append("\n");
            if (!status.properties().isEmpty()) {
                sb.append("Properties         :\n");
                for (Map.Entry<String, String> e : status.properties().entrySet()) {
                    sb.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append("\n");
                }
            }
            return CommandResult.ok(sb.toString().stripTrailing());
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
