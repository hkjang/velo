package io.velo.was.admin.command.datasource;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class DisableDatasourceCommand implements Command {

    @Override
    public String name() {
        return "disable-datasource";
    }

    @Override
    public String description() {
        return "Disable a datasource";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.DATASOURCE;
    }

    @Override
    public String usage() {
        return "disable-datasource <datasource-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().disableDatasource(args[0]);
            return CommandResult.ok("Datasource '" + args[0] + "' disabled.");
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
