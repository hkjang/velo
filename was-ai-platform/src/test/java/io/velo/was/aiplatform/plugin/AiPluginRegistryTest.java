package io.velo.was.aiplatform.plugin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPluginRegistryTest {

    @Test
    void registerAndListPlugins() {
        AiPluginRegistry registry = new AiPluginRegistry();
        registry.register(new AiContentFilterPlugin());

        List<AiPluginRegistry.AiPluginInfo> plugins = registry.listPlugins();
        assertEquals(1, plugins.size());
        assertEquals("content-filter", plugins.get(0).id());
        assertEquals("Content Filter", plugins.get(0).name());
        assertEquals("preprocessor", plugins.get(0).type());
        assertTrue(plugins.get(0).enabled());
    }

    @Test
    void preProcessModifiesContext() {
        AiPluginRegistry registry = new AiPluginRegistry();
        registry.register(new AiContentFilterPlugin());

        AiPluginContext context = new AiPluginContext("  Hello World  ", "CHAT", "test-session");
        AiPluginContext result = registry.runPreProcess(context);

        assertEquals("Hello World", result.getPrompt());
        assertEquals(true, result.getAttribute("contentFilterApplied"));
    }

    @Test
    void postProcessAddsVerification() {
        AiPluginRegistry registry = new AiPluginRegistry();
        registry.register(new AiContentFilterPlugin());

        AiPluginContext context = new AiPluginContext("Hello", "CHAT", "test-session");
        context.setOutputText("Response text");
        AiPluginContext result = registry.runPostProcess(context);

        assertEquals(true, result.getAttribute("contentFilterVerified"));
    }

    @Test
    void unregisterRemovesPlugin() {
        AiPluginRegistry registry = new AiPluginRegistry();
        registry.register(new AiContentFilterPlugin());
        assertEquals(1, registry.size());

        AiPlugin removed = registry.unregister("content-filter");
        assertNotNull(removed);
        assertEquals(0, registry.size());
        assertTrue(registry.listPlugins().isEmpty());
    }

    @Test
    void emptyRegistryDoesNotModifyContext() {
        AiPluginRegistry registry = new AiPluginRegistry();
        AiPluginContext context = new AiPluginContext("Hello", "CHAT", "test-session");

        AiPluginContext preResult = registry.runPreProcess(context);
        assertEquals("Hello", preResult.getPrompt());

        AiPluginContext postResult = registry.runPostProcess(context);
        assertFalse(postResult.getAttributes().containsKey("contentFilterApplied"));
    }
}
