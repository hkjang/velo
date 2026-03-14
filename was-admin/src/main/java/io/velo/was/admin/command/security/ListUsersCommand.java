package io.velo.was.admin.command.security;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

import java.util.List;

public class ListUsersCommand implements Command {

    @Override
    public String name() {
        return "list-users";
    }

    @Override
    public String description() {
        return "List all users";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.SECURITY;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        List<String> users = context.client().listUsers();
        if (users.isEmpty()) {
            return CommandResult.ok("No users found.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Users:\n");
        for (String user : users) {
            sb.append("  - ").append(user).append("\n");
        }
        return CommandResult.ok(sb.toString().stripTrailing());
    }
}
