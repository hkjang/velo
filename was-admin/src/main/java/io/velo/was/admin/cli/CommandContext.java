package io.velo.was.admin.cli;

import io.velo.was.admin.client.AdminClient;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Objects;

public class CommandContext {

    private final AdminClient client;
    private final PrintWriter output;
    private final Path configPath;
    private boolean exitRequested;
    private ScriptRecorder scriptRecorder;

    public CommandContext(AdminClient client, PrintWriter output, Path configPath) {
        this.client = Objects.requireNonNull(client);
        this.output = Objects.requireNonNull(output);
        this.configPath = configPath;
    }

    public AdminClient client() {
        return client;
    }

    public PrintWriter output() {
        return output;
    }

    public Path configPath() {
        return configPath;
    }

    public boolean isExitRequested() {
        return exitRequested;
    }

    public void requestExit() {
        this.exitRequested = true;
    }

    public ScriptRecorder scriptRecorder() {
        return scriptRecorder;
    }

    public void startRecording(Path scriptFile) {
        this.scriptRecorder = new ScriptRecorder(scriptFile);
    }

    public void stopRecording() {
        if (scriptRecorder != null) {
            scriptRecorder.close();
            scriptRecorder = null;
        }
    }

    public boolean isRecording() {
        return scriptRecorder != null;
    }
}
