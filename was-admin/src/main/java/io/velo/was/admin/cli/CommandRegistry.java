package io.velo.was.admin.cli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CommandRegistry {

    private final Map<String, Command> commands = new LinkedHashMap<>();

    public CommandRegistry register(Command command) {
        commands.put(command.name(), command);
        return this;
    }

    public Command find(String name) {
        Command command = commands.get(name);
        if (command != null) {
            return command;
        }
        // Fuzzy match: ignore hyphens (e.g. "listservers" matches "list-servers")
        String normalized = name.replace("-", "").toLowerCase();
        for (Map.Entry<String, Command> entry : commands.entrySet()) {
            if (entry.getKey().replace("-", "").toLowerCase().equals(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public List<Command> all() {
        return List.copyOf(commands.values());
    }

    public List<String> commandNames() {
        return List.copyOf(commands.keySet());
    }

    public Map<CommandCategory, List<Command>> groupedByCategory() {
        Map<CommandCategory, List<Command>> grouped = new EnumMap<>(CommandCategory.class);
        for (Command command : commands.values()) {
            grouped.computeIfAbsent(command.category(), k -> new ArrayList<>()).add(command);
        }
        return Collections.unmodifiableMap(grouped);
    }
}
