package io.velo.was.admin.command.monitoring;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

import java.util.Map;

public class MemoryInfoCommand implements Command {

    @Override
    public String name() {
        return "memory-info";
    }

    @Override
    public String description() {
        return "Show memory status";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.MONITORING;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        Map<String, String> info = context.client().memoryInfo();
        return CommandResult.ok(SystemInfoCommand.formatMap(info));
    }
}
