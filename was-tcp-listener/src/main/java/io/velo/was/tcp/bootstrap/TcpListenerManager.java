package io.velo.was.tcp.bootstrap;

import io.velo.was.config.ServerConfiguration.TcpListenerConfig;
import io.velo.was.tcp.admin.TcpListenerAdmin;
import io.velo.was.tcp.router.TcpMessageRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple TCP listener instances.
 * Provides lifecycle control (startAll, stopAll, individual start/stop)
 * and JMX MBean registration.
 */
public class TcpListenerManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TcpListenerManager.class);

    private final Map<String, TcpListenerServer> listeners = new ConcurrentHashMap<>();
    private final Map<String, TcpMessageRouter> routers = new ConcurrentHashMap<>();

    /**
     * Registers a listener configuration with an associated router.
     */
    public TcpListenerManager register(TcpListenerConfig config, TcpMessageRouter router) {
        TcpListenerServer server = new TcpListenerServer(config, router);
        listeners.put(config.getName(), server);
        routers.put(config.getName(), router);
        return this;
    }

    /**
     * Starts all registered listeners.
     */
    public void startAll() throws Exception {
        for (TcpListenerServer server : listeners.values()) {
            server.start();
            registerMBean(server);
        }
        log.info("All TCP listeners started: {}", listeners.keySet());
    }

    /**
     * Stops all listeners.
     */
    public void stopAll() {
        for (TcpListenerServer server : listeners.values()) {
            try {
                server.stop();
                unregisterMBean(server.name());
            } catch (Exception e) {
                log.error("Error stopping listener: {}", server.name(), e);
            }
        }
        log.info("All TCP listeners stopped");
    }

    /**
     * Starts a specific listener by name.
     */
    public void start(String name) throws Exception {
        TcpListenerServer server = listeners.get(name);
        if (server == null) {
            throw new IllegalArgumentException("No listener registered: " + name);
        }
        server.start();
        registerMBean(server);
    }

    /**
     * Stops a specific listener by name.
     */
    public void stop(String name) {
        TcpListenerServer server = listeners.get(name);
        if (server != null) {
            server.stop();
            unregisterMBean(name);
        }
    }

    /**
     * Returns a listener by name.
     */
    public TcpListenerServer getListener(String name) {
        return listeners.get(name);
    }

    /**
     * Returns all listener names.
     */
    public Set<String> listenerNames() {
        return Set.copyOf(listeners.keySet());
    }

    /**
     * Returns a summary of all listener statuses.
     */
    public List<String> status() {
        List<String> statuses = new ArrayList<>();
        for (TcpListenerServer server : listeners.values()) {
            statuses.add(String.format("%s: port=%d running=%s active=%d accepted=%d",
                    server.name(), server.port(), server.isRunning(),
                    server.metrics().activeConnections(),
                    server.metrics().connectionsAccepted()));
        }
        return statuses;
    }

    @Override
    public void close() {
        stopAll();
    }

    private void registerMBean(TcpListenerServer server) {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName objectName = new ObjectName(
                    "io.velo.was:type=TcpListener,name=" + server.name());
            if (!mbs.isRegistered(objectName)) {
                TcpListenerAdmin admin = new TcpListenerAdmin(server, server.metrics());
                mbs.registerMBean(admin, objectName);
            }
        } catch (Exception e) {
            log.warn("Failed to register MBean for listener: {}", server.name(), e);
        }
    }

    private void unregisterMBean(String name) {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName objectName = new ObjectName("io.velo.was:type=TcpListener,name=" + name);
            if (mbs.isRegistered(objectName)) {
                mbs.unregisterMBean(objectName);
            }
        } catch (Exception e) {
            log.warn("Failed to unregister MBean for listener: {}", name, e);
        }
    }
}
