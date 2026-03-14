package io.velo.was.admin.command.script;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

import java.nio.file.Path;

public class RecordScriptCommand implements Command {

    @Override
    public String name() {
        return "record-script";
    }

    @Override
    public String description() {
        return "Start recording commands to script file";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.SCRIPT;
    }

    @Override
    public String usage() {
        return "record-script <output-file>";
    }

    @Override
    public String detailedHelp() {
        return """
                Start recording all executed commands to a script file.
                Use 'stop-record' to stop recording and save the file.

                Usage:
                  record-script <output-file>

                Example:
                  record-script /tmp/my-commands.velo""";
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + usage());
        }
        if (context.isRecording()) {
            return CommandResult.error("Already recording. Use 'stop-record' first.");
        }
        context.startRecording(Path.of(args[0]));
        return CommandResult.ok("Recording started. Commands will be saved to: " + args[0]);
    }
}
