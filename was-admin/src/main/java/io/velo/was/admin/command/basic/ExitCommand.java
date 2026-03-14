package io.velo.was.admin.command.basic;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class ExitCommand implements Command {

    private final String commandName;

    public ExitCommand(String commandName) {
        this.commandName = commandName;
    }

    @Override
    public String name() {
        return commandName;
    }

    @Override
    public String description() {
        return "Exit CLI";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.BASIC;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        context.requestExit();
        return CommandResult.empty();
    }
}
