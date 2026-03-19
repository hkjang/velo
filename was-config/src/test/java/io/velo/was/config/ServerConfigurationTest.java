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
}
