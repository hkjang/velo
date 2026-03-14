package io.velo.was.admin.command.datasource;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

public class DatasourceInfoCommand implements Command {

    @Override
    public String name() {
        return "datasource-info";
    }

    @Override
    public String description() {
        return "Show datasource information";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.DATASOURCE;
    }

    @Override
    public String usage() {
        return "datasource-info <datasource-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            AdminClient.DatasourceStatus status = context.client().datasourceInfo(args[0]);
            StringBuilder sb = new StringBuilder();
            sb.append("Datasource Name     : ").append(status.name()).append("\n");
            sb.append("Type                : ").append(status.type()).append("\n");
            sb.append("URL                 : ").append(status.url()).append("\n");
            sb.append("Status              : ").append(status.status()).append("\n");
            sb.append("Active Connections  : ").append(status.activeConnections()).append("\n");
            sb.append("Idle Connections    : ").append(status.idleConnections()).append("\n");
            sb.append("Max Connections     : ").append(status.maxConnections());
            return CommandResult.ok(sb.toString());
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
