package io.velo.was.aiplatform.plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry for AI platform plugins. Manages plugin lifecycle and provides
 * the pre/post processing pipeline for gateway requests.
 */
public class AiPluginRegistry {

    private final ConcurrentMap<String, AiPlugin> plugins = new ConcurrentHashMap<>();

    /**
     * Register a plugin. Calls {@link AiPlugin#initialize()} on first registration.
     */
    public void register(AiPlugin plugin) {
        if (plugin == null || plugin.id() == null || plugin.id().isBlank()) {
            throw new IllegalArgumentException("Plugin must have a non-blank id");
        }
        AiPlugin existing = plugins.putIfAbsent(plugin.id().trim().toLowerCase(), plugin);
        if (existing == null) {
            plugin.initialize();
        }
    }

    /**
     * Unregister a plugin by ID.
     */
    public AiPlugin unregister(String pluginId) {
        if (pluginId == null) {
            return null;
        }
        return plugins.remove(pluginId.trim().toLowerCase());
    }

    /**
     * List all registered plugins, sorted by ID.
     */
    public List<AiPluginInfo> listPlugins() {
        return plugins.values().stream()
                .sorted(Comparator.comparing(AiPlugin::id))
                .map(plugin -> new AiPluginInfo(plugin.id(), plugin.name(), plugin.type(), plugin.isEnabled()))
                .toList();
    }

    /**
     * Run all enabled pre-processing plugins on the context.
     */
    public AiPluginContext runPreProcess(AiPluginContext context) {
        AiPluginContext current = context;
        for (AiPlugin plugin : enabledPlugins()) {
            current = plugin.preProcess(current);
        }
        return current;
    }

    /**
     * Run all enabled post-processing plugins on the context.
     */
    public AiPluginContext runPostProcess(AiPluginContext context) {
        AiPluginContext current = context;
        for (AiPlugin plugin : enabledPlugins()) {
            current = plugin.postProcess(current);
        }
        return current;
    }

    /**
     * Returns the count of registered plugins.
     */
    public int size() {
        return plugins.size();
    }

    private List<AiPlugin> enabledPlugins() {
        List<AiPlugin> enabled = new ArrayList<>();
        for (AiPlugin plugin : plugins.values()) {
            if (plugin.isEnabled()) {
                enabled.add(plugin);
            }
        }
        enabled.sort(Comparator.comparing(AiPlugin::id));
        return enabled;
    }

    /**
     * Immutable snapshot of a registered plugin's metadata.
     */
    public record AiPluginInfo(String id, String name, String type, boolean enabled) {
    }
}
