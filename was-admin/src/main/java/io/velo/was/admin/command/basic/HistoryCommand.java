package io.velo.was.admin.command.basic;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class HistoryCommand implements Command {

    @Override
    public String name() {
        return "history";
    }

    @Override
    public String description() {
        return "Show command history";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.BASIC;
    }

    @Override
    public String detailedHelp() {
        return "Show command execution history. Use up/down arrow keys to navigate history.";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        return CommandResult.ok("(History is available via up/down arrow keys in interactive mode)");
    }
}
