package io.velo.was.deploy;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DeploymentRegistryTest {

    @Test
    void deriveAppNameFromWarFile() {
        assertEquals("myapp", DeploymentRegistry.deriveAppName(Path.of("deploy/myapp.war")));
        assertEquals("ROOT", DeploymentRegistry.deriveAppName(Path.of("deploy/ROOT.war")));
        assertEquals("hello-world", DeploymentRegistry.deriveAppName(Path.of("/opt/deploy/hello-world.war")));
    }

    @Test
    void deriveAppNameFromDirectory() {
        assertEquals("myapp", DeploymentRegistry.deriveAppName(Path.of("deploy/myapp")));
    }

    @Test
    void deriveContextPathFromAppName() {
        assertEquals("/myapp", DeploymentRegistry.deriveContextPath("myapp"));
        assertEquals("/hello-world", DeploymentRegistry.deriveContextPath("hello-world"));
    }

    @Test
    void rootWarMapsToEmptyContextPath() {
        assertEquals("", DeploymentRegistry.deriveContextPath("ROOT"));
        assertEquals("", DeploymentRegistry.deriveContextPath("root"));
    }
}
