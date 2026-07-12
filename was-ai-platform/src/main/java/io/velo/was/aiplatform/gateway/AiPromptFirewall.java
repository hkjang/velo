package io.velo.was.aiplatform.gateway;

import io.velo.was.config.ServerConfiguration;

import java.util.Locale;

public final class AiPromptFirewall {

    private AiPromptFirewall() {
    }

    public static Decision inspect(String prompt, ServerConfiguration.Advanced advanced) {
        if (advanced == null || !advanced.isPromptFirewallEnabled()) {
            return Decision.allow();
        }
        String value = prompt == null ? "" : prompt;
        if (value.length() > advanced.getPromptFirewallMaxChars()) {
            return Decision.block("Prompt exceeds max allowed length", "");
        }
        String lowered = value.toLowerCase(Locale.ROOT);
        for (String term : advanced.getPromptFirewallBlockedTerms()) {
            if (term != null && !term.isBlank() && lowered.contains(term.toLowerCase(Locale.ROOT))) {
                return Decision.block("Prompt matched blocked firewall term", term);
            }
        }
        return Decision.allow();
    }

    public record Decision(boolean allowed, String reason, String matchedTerm) {
        private static Decision allow() {
            return new Decision(true, "", "");
        }

        private static Decision block(String reason, String matchedTerm) {
            return new Decision(false, reason, matchedTerm == null ? "" : matchedTerm);
        }
    }
}
