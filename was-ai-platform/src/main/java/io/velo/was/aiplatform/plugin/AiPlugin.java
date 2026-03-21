package io.velo.was.aiplatform.plugin;

/**
 * Extension point for AI platform plugins. Plugins can intercept and transform
 * requests/responses at various stages of the inference pipeline.
 *
 * <p>Lifecycle: {@code initialize()} is called once at registration time.
 * {@code preProcess()} runs before routing, {@code postProcess()} runs after
 * inference. Both receive a mutable context that travels through the pipeline.</p>
 */
public interface AiPlugin {

    /** Unique identifier for this plugin. */
    String id();

    /** Human-readable name. */
    String name();

    /** Plugin type: "preprocessor", "postprocessor", "transformer", or "custom". */
    String type();

    /** Called once when the plugin is registered. */
    default void initialize() {
    }

    /** Called before routing — can modify the prompt, request type, or session. */
    default AiPluginContext preProcess(AiPluginContext context) {
        return context;
    }

    /** Called after inference — can modify the output text or metadata. */
    default AiPluginContext postProcess(AiPluginContext context) {
        return context;
    }

    /** Whether this plugin is currently active. */
    default boolean isEnabled() {
        return true;
    }
}
