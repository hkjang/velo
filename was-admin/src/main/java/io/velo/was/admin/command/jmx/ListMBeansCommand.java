package io.velo.was.admin.command.jmx;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;

import java.util.List;

public class ListMBeansCommand implements Command {

    @Override
    public String name() {
        return "list-mbeans";
    }

    @Override
    public String description() {
        return "List MBeans";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.JMX;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        try {
            List<AdminClient.MBeanSummary> mbeans = context.client().listMBeans();
            if (mbeans.isEmpty()) {
                return CommandResult.ok("No MBeans found.");
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-60s %-40s%n", "OBJECT NAME", "CLASS"));
            sb.append("-".repeat(102)).append("\n");
            for (AdminClient.MBeanSummary m : mbeans) {
                String objName = m.objectName().length() > 58
                        ? m.objectName().substring(0, 55) + "..."
                        : m.objectName();
                String className = m.className().length() > 38
                        ? m.className().substring(0, 35) + "..."
                        : m.className();
                sb.append(String.format("%-60s %-40s%n", objName, className));
            }
            sb.append("\nTotal: ").append(mbeans.size()).append(" MBeans");
            return CommandResult.ok(sb.toString());
        } catch (RuntimeException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
