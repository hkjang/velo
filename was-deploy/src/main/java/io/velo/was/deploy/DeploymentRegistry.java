package io.velo.was.deploy;

import io.velo.was.servlet.SimpleServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks deployed WAR applications and coordinates deployment lifecycle
 * between {@link WarDeployer} and {@link SimpleServletContainer}.
 */
public class DeploymentRegistry {

    private static final Logger log = LoggerFactory.getLogger(DeploymentRegistry.class);

    private final WarDeployer deployer;
    private final SimpleServletContainer container;
    private final Map<String, WarDeployer.DeploymentResult> deployments = new ConcurrentHashMap<>();

    public DeploymentRegistry(WarDeployer deployer, SimpleServletContainer container) {
        this.deployer = deployer;
        this.container = container;
    }

    /**
     * Deploys a WAR file or exploded directory.
     * Context path is derived from the filename (e.g., myapp.war → /myapp, ROOT.war → "").
     */
    public void deploy(Path warPath) {
        String appName = deriveAppName(warPath);
        String contextPath = deriveContextPath(appName);

        try {
            WarDeployer.DeploymentResult result = deployer.deploy(warPath, contextPath);
            container.deploy(result.application());
            deployments.put(appName, result);
            log.info("Deployed application: name={} contextPath={} source={}", appName, contextPath, warPath);
        } catch (Exception e) {
            log.error("Failed to deploy {}: {}", warPath, e.getMessage(), e);
        }
    }

    /**
     * Undeploys an application by name.
     */
    public void undeploy(String appName) {
        WarDeployer.DeploymentResult result = deployments.remove(appName);
        if (result == null) {
            log.warn("No deployment found for: {}", appName);
            return;
        }

        try {
            container.undeploy(result.application().contextPath());
            deployer.cleanup(result);
            log.info("Undeployed application: {}", appName);
        } catch (Exception e) {
            log.error("Failed to undeploy {}: {}", appName, e.getMessage(), e);
        }
    }

    /**
     * Redeploys an application (undeploy + deploy).
     */
    public void redeploy(Path warPath) {
        String appName = deriveAppName(warPath);
        undeploy(appName);
        deploy(warPath);
    }

    public boolean isDeployed(String appName) {
        return deployments.containsKey(appName);
    }

    public Set<String> deployedApplications() {
        return Collections.unmodifiableSet(deployments.keySet());
    }

    /**
     * Undeploys all applications and cleans up resources.
     */
    public void undeployAll() {
        for (String appName : Set.copyOf(deployments.keySet())) {
            undeploy(appName);
        }
    }

    static String deriveAppName(Path warPath) {
        String fileName = warPath.getFileName().toString();
        if (fileName.endsWith(".war")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }

    static String deriveContextPath(String appName) {
        if ("ROOT".equalsIgnoreCase(appName)) {
            return "";
        }
        return "/" + appName;
    }
}
