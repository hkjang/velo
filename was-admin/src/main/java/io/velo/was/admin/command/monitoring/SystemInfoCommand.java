package io.velo.was.admin.command.monitoring;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

import java.util.Map;

public class SystemInfoCommand implements Command {

    @Override
    public String name() {
        return "system-info";
    }

    @Override
    public String description() {
        return "Show system information";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.MONITORING;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        Map<String, String> info = context.client().systemInfo();
        return CommandResult.ok(formatMap(info));
    }

    public static String formatMap(Map<String, String> map) {
        int maxKeyLen = map.keySet().stream().mapToInt(String::length).max().orElse(0);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            sb.append(String.format("%-" + (maxKeyLen + 2) + "s : %s%n", entry.getKey(), entry.getValue()));
        }
        return sb.toString().stripTrailing();
    }
}
