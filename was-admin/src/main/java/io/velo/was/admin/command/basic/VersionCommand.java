package io.velo.was.admin.command.basic;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class VersionCommand implements Command {

    private final String version;

    public VersionCommand(String version) {
        this.version = version;
    }

    @Override
    public String name() {
        return "version";
    }

    @Override
    public String description() {
        return "Show Velo WAS version";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.BASIC;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        return CommandResult.ok("Velo WAS version " + version);
    }
}
