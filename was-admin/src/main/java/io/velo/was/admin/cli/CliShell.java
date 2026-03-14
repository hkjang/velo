package io.velo.was.admin.cli;

import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.EndOfFileException;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.io.IOException;
import java.io.PrintWriter;

public class CliShell {

    private static final String PROMPT = "velo> ";
    private static final String BANNER = """
            =========================================================
              Velo WAS Administration Console  v%s
            =========================================================
            """;

    private final CommandRegistry registry;
    private final CommandContext context;
    private final String version;

    public CliShell(CommandRegistry registry, CommandContext context, String version) {
        this.registry = registry;
        this.context = context;
        this.version = version;
    }

    public void run() throws IOException {
        try (Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build()) {

            PrintWriter writer = terminal.writer();
            writer.println(BANNER.formatted(version));
            writer.println("Type 'help' for available commands.");
            writer.println();
            writer.flush();

            Completer completer = new StringsCompleter(registry.commandNames());
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(completer)
                    .history(new DefaultHistory())
                    .variable(LineReader.HISTORY_SIZE, 500)
                    .build();

            while (!context.isExitRequested()) {
                String line;
                try {
                    line = reader.readLine(PROMPT);
                } catch (UserInterruptException e) {
                    continue;
                } catch (EndOfFileException e) {
                    break;
                }

                if (line == null || line.isBlank()) {
                    continue;
                }

                String trimmed = line.trim();
                if (context.isRecording()) {
                    context.scriptRecorder().record(trimmed);
                }

                String[] tokens = trimmed.split("\\s+");
                String commandName = tokens[0];
                String[] args = new String[tokens.length - 1];
                System.arraycopy(tokens, 1, args, 0, args.length);

                Command command = registry.find(commandName);
                if (command == null) {
                    writer.println("Unknown command: " + commandName);
                    writer.println("Type 'help' for available commands.");
                    writer.flush();
                    continue;
                }

                try {
                    CommandResult result = command.execute(context, args);
                    if (result.message() != null && !result.message().isEmpty()) {
                        writer.println(result.message());
                    }
                    if (!result.success()) {
                        writer.println("[ERROR] Command failed.");
                    }
                } catch (Exception e) {
                    writer.println("[ERROR] " + e.getMessage());
                }
                writer.flush();
            }

            writer.println("Bye.");
            writer.flush();
        }
    }

    public void runScript(java.util.List<String> commands) {
        PrintWriter writer = context.output();
        for (String line : commands) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String trimmed = line.trim();
            writer.println(PROMPT + trimmed);

            String[] tokens = trimmed.split("\\s+");
            String commandName = tokens[0];
            String[] args = new String[tokens.length - 1];
            System.arraycopy(tokens, 1, args, 0, args.length);

            Command command = registry.find(commandName);
            if (command == null) {
                writer.println("Unknown command: " + commandName);
                continue;
            }

            try {
                CommandResult result = command.execute(context, args);
                if (result.message() != null && !result.message().isEmpty()) {
                    writer.println(result.message());
                }
            } catch (Exception e) {
                writer.println("[ERROR] " + e.getMessage());
            }
            writer.flush();

            if (context.isExitRequested()) {
                break;
            }
        }
    }
}
