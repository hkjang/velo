package io.velo.was.admin.command.basic;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandRegistry;
import io.velo.was.admin.cli.CommandResult;

import java.util.List;
import java.util.Map;

public class HelpCommand implements Command {

    private final CommandRegistry registry;

    public HelpCommand(CommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "Show available commands";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.BASIC;
    }

    @Override
    public String usage() {
        return "help [command]";
    }

    @Override
    public String detailedHelp() {
        return """
                Show available commands or detailed help for a specific command.

                Usage:
                  help            - Show all available commands grouped by category
                  help <command>  - Show detailed help for a specific command""";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length > 0) {
            return showCommandHelp(args[0]);
        }
        return showAllCommands();
    }

    private CommandResult showCommandHelp(String commandName) {
        Command command = registry.find(commandName);
        if (command == null) {
            return CommandResult.error("Unknown command: " + commandName);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Command    : ").append(command.name()).append("\n");
        sb.append("Category   : ").append(command.category().displayName()).append("\n");
        sb.append("Usage      : ").append(command.usage()).append("\n");
        sb.append("\n");
        sb.append(command.detailedHelp());
        return CommandResult.ok(sb.toString());
    }

    private CommandResult showAllCommands() {
        StringBuilder sb = new StringBuilder();
        sb.append("Available commands:\n");

        Map<CommandCategory, List<Command>> grouped = registry.groupedByCategory();
        for (Map.Entry<CommandCategory, List<Command>> entry : grouped.entrySet()) {
            sb.append("\n[").append(entry.getKey().displayName()).append("]\n");
            for (Command command : entry.getValue()) {
                sb.append(String.format("  %-28s %s%n", command.name(), command.description()));
            }
        }
        return CommandResult.ok(sb.toString());
    }
}
