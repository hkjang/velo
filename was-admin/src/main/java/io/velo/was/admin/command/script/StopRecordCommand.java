package io.velo.was.admin.command.script;

import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandResult;

public class StopRecordCommand implements Command {

    @Override
    public String name() {
        return "stop-record";
    }

    @Override
    public String description() {
        return "Stop recording and save script";
    }

    @Override
    public CommandCategory category() {
        return CommandCategory.SCRIPT;
    }

    @Override
    public CommandResult execute(CommandContext context, String[] args) {
        if (!context.isRecording()) {
            return CommandResult.error("Not currently recording. Use 'record-script' first.");
        }
        int commandCount = context.scriptRecorder().commands().size();
        context.stopRecording();
        return CommandResult.ok("Recording stopped. " + commandCount + " command(s) saved.");
    }
}
