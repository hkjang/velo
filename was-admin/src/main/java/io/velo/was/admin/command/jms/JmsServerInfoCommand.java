package io.velo.was.admin.command.jms;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

public class JmsServerInfoCommand implements Command {

    @Override
    public String name() {
        return "jms-server-info";
    }

    @Override
    public String description() {
        return "Show JMS server information";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.JMS;
    }

    @Override
    public String usage() {
        return "jms-server-info <server-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            AdminClient.JmsServerStatus status = context.client().jmsServerInfo(args[0]);
            StringBuilder sb = new StringBuilder();
            sb.append("JMS Server Name     : ").append(status.name()).append("\n");
            sb.append("Status              : ").append(status.status()).append("\n");
            sb.append("Type                : ").append(status.type()).append("\n");
            sb.append("Destination Count   : ").append(status.destinationCount());
            return CommandResult.ok(sb.toString());
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
