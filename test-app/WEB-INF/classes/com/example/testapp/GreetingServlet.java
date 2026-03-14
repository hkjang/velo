package com.example.testapp;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Simple test servlet that displays a greeting.
 */
public class GreetingServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html; charset=UTF-8");

        String name = request.getParameter("name");
        if (name == null || name.isBlank()) {
            name = "World";
        }

        // Set as request attribute so JSP can access it
        request.setAttribute("userName", name);
        request.setAttribute("serverTime", java.time.LocalDateTime.now().toString());

        PrintWriter out = response.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html><head><title>Greeting</title></head>");
        out.println("<body>");
        out.println("<h1>Hello, " + escapeHtml(name) + "!</h1>");
        out.println("<p>Server Time: " + java.time.LocalDateTime.now() + "</p>");
        out.println("<p><a href=\"info.jsp\">JSP Info Page</a></p>");
        out.println("<p><a href=\"api/status\">API Status</a></p>");
        out.println("</body></html>");
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
