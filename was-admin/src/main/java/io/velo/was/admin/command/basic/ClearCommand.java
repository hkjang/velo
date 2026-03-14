package io.velo.was.admin.command.basic;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class ClearCommand implements Command {

    @Override
    public String name() {
        return "clear";
    }

    @Override
    public String description() {
        return "Clear screen";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.BASIC;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        context.output().print("\033[H\033[2J");
        context.output().flush();
        return CommandResult.empty();
    }
}
