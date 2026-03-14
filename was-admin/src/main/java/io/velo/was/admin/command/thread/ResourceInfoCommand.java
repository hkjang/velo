package io.velo.was.admin.command.thread;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.command.monitoring.SystemInfoCommand;

import java.util.Map;

public class ResourceInfoCommand implements Command {

    @Override
    public String name() {
        return "resource-info";
    }

    @Override
    public String description() {
        return "Show resource status overview";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.THREAD;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        Map<String, String> info = context.client().resourceInfo();
        return CommandResult.ok(SystemInfoCommand.formatMap(info));
    }
}
