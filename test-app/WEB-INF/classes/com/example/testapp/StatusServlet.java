package com.example.testapp;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;

/**
 * REST-style API servlet returning JSON status.
 */
public class StatusServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json; charset=UTF-8");

        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        long totalMemory = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long freeMemory = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;

        PrintWriter out = response.getWriter();
        out.printf("""
                {
                  "status": "running",
                  "appName": "test-app",
                  "uptimeMs": %d,
                  "memory": {
                    "totalMB": %d,
                    "usedMB": %d,
                    "freeMB": %d
                  },
                  "javaVersion": "%s",
                  "threads": %d
                }
                """, uptime, totalMemory, usedMemory, freeMemory,
                System.getProperty("java.version"),
                Thread.activeCount());
    }
}
