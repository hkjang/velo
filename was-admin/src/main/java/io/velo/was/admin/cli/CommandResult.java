package io.velo.was.admin.cli;

public record CommandResult(boolean success, String message) {

    public static CommandResult ok(String message) {
        return new CommandResult(true, message);
    }

    public static CommandResult error(String message) {
        return new CommandResult(false, message);
    }

    public static CommandResult empty() {
        return new CommandResult(true, "");
    }
}
