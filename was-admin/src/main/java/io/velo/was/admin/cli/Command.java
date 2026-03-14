package io.velo.was.admin.cli;

public interface Command {

    String name();

    String description();

    CommandCategory category();

    CommandResult execute(CommandContext context, String[] args);

    default String usage() {
        return name();
    }

    default String detailedHelp() {
        return description();
    }
}
