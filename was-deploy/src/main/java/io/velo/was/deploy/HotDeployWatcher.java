package io.velo.was.deploy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;

/**
 * Watches a deploy directory for WAR file changes and triggers
 * automatic deployment, undeployment, and redeployment.
 */
public class HotDeployWatcher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HotDeployWatcher.class);

    private final Path deployDirectory;
    private final DeploymentRegistry registry;
    private final int debounceSeconds;
    private volatile boolean running;
    private Thread watchThread;

    public HotDeployWatcher(Path deployDirectory, DeploymentRegistry registry, int debounceSeconds) {
        this.deployDirectory = deployDirectory;
        this.registry = registry;
        this.debounceSeconds = debounceSeconds;
    }

    /**
     * Starts the watcher on a dedicated daemon thread.
     */
    public void start() throws IOException {
        if (!Files.exists(deployDirectory)) {
            Files.createDirectories(deployDirectory);
        }

        running = true;
        watchThread = Thread.ofPlatform()
                .daemon(true)
                .name("velo-hot-deploy")
                .start(this::watchLoop);

        log.info("Hot deploy watcher started: directory={} debounce={}s", deployDirectory, debounceSeconds);
    }

    private void watchLoop() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            deployDirectory.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);

            while (running) {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }

                // Debounce: wait for file copy to finish
                if (debounceSeconds > 0) {
                    Thread.sleep(debounceSeconds * 1000L);
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path fileName = pathEvent.context();
                    Path fullPath = deployDirectory.resolve(fileName);

                    if (!isWarFile(fileName)) {
                        continue;
                    }

                    String appName = DeploymentRegistry.deriveAppName(fullPath);

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        log.info("Hot deploy: detected new WAR {}", fileName);
                        registry.deploy(fullPath);
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        log.info("Hot deploy: detected WAR removal {}", fileName);
                        registry.undeploy(appName);
                    } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        log.info("Hot deploy: detected WAR modification {}", fileName);
                        registry.redeploy(fullPath);
                    }
                }

                if (!key.reset()) {
                    log.warn("Hot deploy: watch key invalidated, stopping watcher");
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Hot deploy watcher interrupted");
        } catch (IOException e) {
            log.error("Hot deploy watcher error", e);
        }
    }

    private static boolean isWarFile(Path fileName) {
        return fileName.toString().endsWith(".war");
    }

    @Override
    public void close() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
            try {
                watchThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Hot deploy watcher stopped");
    }
}
