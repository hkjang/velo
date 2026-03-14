package io.velo.was.bootstrap;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;

public class SampleLifecycleListener implements ServletContextListener, ServletRequestListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        sce.getServletContext().setAttribute("appLifecycle", "started");
    }

    @Override
    public void requestInitialized(ServletRequestEvent sre) {
        Integer count = (Integer) sre.getServletContext().getAttribute("requestCount");
        sre.getServletContext().setAttribute("requestCount", count == null ? 1 : count + 1);
    }
}
