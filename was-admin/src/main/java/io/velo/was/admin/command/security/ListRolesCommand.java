package io.velo.was.admin.command.security;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

import java.util.List;

public class ListRolesCommand implements Command {

    @Override
    public String name() {
        return "list-roles";
    }

    @Override
    public String description() {
        return "List all roles";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.SECURITY;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        List<String> roles = context.client().listRoles();
        if (roles.isEmpty()) {
            return CommandResult.ok("No roles found.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Roles:\n");
        for (String role : roles) {
            sb.append("  - ").append(role).append("\n");
        }
        return CommandResult.ok(sb.toString().stripTrailing());
    }
}
