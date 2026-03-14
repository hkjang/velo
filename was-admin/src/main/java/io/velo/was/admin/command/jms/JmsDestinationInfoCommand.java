package io.velo.was.admin.command.jms;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

public class JmsDestinationInfoCommand implements Command {

    @Override
    public String name() {
        return "jms-destination-info";
    }

    @Override
    public String description() {
        return "Show JMS destination information";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.JMS;
    }

    @Override
    public String usage() {
        return "jms-destination-info <destination-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            AdminClient.JmsDestinationStatus status = context.client().jmsDestinationInfo(args[0]);
            StringBuilder sb = new StringBuilder();
            sb.append("Destination Name    : ").append(status.name()).append("\n");
            sb.append("Type                : ").append(status.type()).append("\n");
            sb.append("Message Count       : ").append(status.messageCount()).append("\n");
            sb.append("Consumer Count      : ").append(status.consumerCount()).append("\n");
            sb.append("Bytes Used          : ").append(status.bytesUsed());
            return CommandResult.ok(sb.toString());
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
