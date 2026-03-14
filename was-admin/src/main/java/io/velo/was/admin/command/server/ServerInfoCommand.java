package io.velo.was.admin.command.server;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

public class ServerInfoCommand implements Command {

    @Override
    public String name() {
        return "server-info";
    }

    @Override
    public String description() {
        return "Show server status";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.SERVER;
    }

    @Override
    public String usage() {
        return "server-info [server-name]";
    }

    @Override
    public String detailedHelp() {
        return """
                Show detailed server status information.

                Usage:
                  server-info               - Show info for default server
                  server-info <server-name> - Show info for specific server""";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        String serverName = args.length > 0 ? args[0] : null;
        AdminClient.ServerStatus status = context.client().serverInfo(serverName);

        long uptimeSec = status.uptimeMillis() / 1000;
        long hours = uptimeSec / 3600;
        long minutes = (uptimeSec % 3600) / 60;
        long seconds = uptimeSec % 60;

        StringBuilder sb = new StringBuilder();
        sb.append("Server Name       : ").append(status.name()).append("\n");
        sb.append("Node ID           : ").append(status.nodeId()).append("\n");
        sb.append("Status            : ").append(status.status()).append("\n");
        sb.append("Host              : ").append(status.host()).append("\n");
        sb.append("Port              : ").append(status.port()).append("\n");
        sb.append("Transport         : ").append(status.transport()).append("\n");
        sb.append("TLS Enabled       : ").append(status.tlsEnabled()).append("\n");
        sb.append("Uptime            : ").append("%dh %dm %ds".formatted(hours, minutes, seconds)).append("\n");
        sb.append("Boss Threads      : ").append(status.bossThreads()).append("\n");
        sb.append("Worker Threads    : ").append(status.workerThreads() == 0 ? "auto" : status.workerThreads()).append("\n");
        sb.append("Business Threads  : ").append(status.businessThreads());
        return CommandResult.ok(sb.toString());
    }
}
