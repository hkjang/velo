package io.velo.was.bootstrap;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Test servlet for WAR deployment integration test.
 */
public class TestGreetingServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json; charset=UTF-8");
        String name = request.getParameter("name");
        if (name == null) name = "Velo";

        PrintWriter out = response.getWriter();
        out.printf("""
                {"greeting":"Hello, %s!","servlet":"TestGreetingServlet","status":"ok"}
                """.trim(), name);
    }
}
