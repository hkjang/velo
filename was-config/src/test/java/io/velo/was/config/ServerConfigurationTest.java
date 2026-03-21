package io.velo.was.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerConfigurationTest {

    @Test
    void validatesAndNormalizesServletMappingStrategy() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.getServer().getServlet().setMappingStrategy("tomcat_compat");

        configuration.validate();

        assertEquals("TOMCAT_COMPAT", configuration.getServer().getServlet().getMappingStrategy());
    }

    @Test
    void rejectsUnknownServletMappingStrategy() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.getServer().getServlet().setMappingStrategy("custom");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, configuration::validate);

        assertEquals("server.servlet.mappingStrategy must be VELO or TOMCAT_COMPAT", error.getMessage());
    }

    @Test
    void validatesAndNormalizesAiPlatformSettings() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.getServer().getAiPlatform().setMode("saas");
        configuration.getServer().getAiPlatform().getServing().setDefaultStrategy("latency_first");
        configuration.getServer().getAiPlatform().getPlatform().setVersioningStrategy("blue_green");
        configuration.getServer().getAiPlatform().getAdvanced().setPromptRoutingMode("classifier");

        configuration.validate();

        assertEquals("SAAS", configuration.getServer().getAiPlatform().getMode());
        assertEquals("LATENCY_FIRST", configuration.getServer().getAiPlatform().getServing().getDefaultStrategy());
        assertEquals("BLUE_GREEN", configuration.getServer().getAiPlatform().getPlatform().getVersioningStrategy());
        assertEquals("CLASSIFIER", configuration.getServer().getAiPlatform().getAdvanced().getPromptRoutingMode());
    }

    @Test
    void rejectsInvalidAiPlatformRoadmapStage() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.getServer().getAiPlatform().getRoadmap().setCurrentStage(6);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, configuration::validate);

        assertEquals("server.aiPlatform.roadmap.currentStage must be between 1 and 5", error.getMessage());
    }
}
