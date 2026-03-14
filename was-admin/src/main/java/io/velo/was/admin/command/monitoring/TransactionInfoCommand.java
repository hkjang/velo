package io.velo.was.admin.command.monitoring;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

import java.util.Map;

public class TransactionInfoCommand implements Command {

    @Override
    public String name() {
        return "transaction-info";
    }

    @Override
    public String description() {
        return "Show transaction information";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.MONITORING;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        Map<String, String> info = context.client().transactionInfo();
        return CommandResult.ok(SystemInfoCommand.formatMap(info));
    }
}
