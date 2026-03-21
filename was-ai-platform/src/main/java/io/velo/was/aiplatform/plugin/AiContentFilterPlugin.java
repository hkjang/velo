package io.velo.was.aiplatform.plugin;

/**
 * Built-in content filter plugin that demonstrates the plugin framework.
 * Normalizes prompts by trimming whitespace and adding metadata attributes.
 */
public class AiContentFilterPlugin implements AiPlugin {

    @Override
    public String id() {
        return "content-filter";
    }

    @Override
    public String name() {
        return "Content Filter";
    }

    @Override
    public String type() {
        return "preprocessor";
    }

    @Override
    public AiPluginContext preProcess(AiPluginContext context) {
        String prompt = context.getPrompt();
        if (prompt != null) {
            context.setPrompt(prompt.strip());
            context.setAttribute("originalPromptLength", prompt.length());
            context.setAttribute("normalizedPromptLength", prompt.strip().length());
        }
        context.setAttribute("contentFilterApplied", true);
        return context;
    }

    @Override
    public AiPluginContext postProcess(AiPluginContext context) {
        context.setAttribute("contentFilterVerified", true);
        return context;
    }
}
