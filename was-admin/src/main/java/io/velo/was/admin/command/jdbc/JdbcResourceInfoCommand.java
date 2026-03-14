package io.velo.was.admin.command.jdbc;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

public class JdbcResourceInfoCommand implements Command {

    @Override
    public String name() {
        return "jdbc-resource-info";
    }

    @Override
    public String description() {
        return "Show JDBC resource information";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.JDBC;
    }

    @Override
    public String usage() {
        return "jdbc-resource-info <resource-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            AdminClient.JdbcResourceStatus status = context.client().jdbcResourceInfo(args[0]);
            StringBuilder sb = new StringBuilder();
            sb.append("Resource Name       : ").append(status.name()).append("\n");
            sb.append("Pool Name           : ").append(status.poolName()).append("\n");
            sb.append("Driver Class        : ").append(status.driverClass()).append("\n");
            sb.append("URL                 : ").append(status.url()).append("\n");
            sb.append("Active Connections  : ").append(status.activeConnections()).append("\n");
            sb.append("Idle Connections    : ").append(status.idleConnections()).append("\n");
            sb.append("Max Pool Size       : ").append(status.maxPoolSize());
            return CommandResult.ok(sb.toString());
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
