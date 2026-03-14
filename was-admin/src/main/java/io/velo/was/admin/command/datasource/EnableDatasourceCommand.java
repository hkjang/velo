package io.velo.was.admin.command.datasource;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class EnableDatasourceCommand implements Command {

    @Override
    public String name() {
        return "enable-datasource";
    }

    @Override
    public String description() {
        return "Enable a datasource";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.DATASOURCE;
    }

    @Override
    public String usage() {
        return "enable-datasource <datasource-name>";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        try {
            context.client().enableDatasource(args[0]);
            return CommandResult.ok("Datasource '" + args[0] + "' enabled.");
        } catch (UnsupportedOperationException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
