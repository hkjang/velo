package io.velo.was.bootstrap;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

public class SampleHelloServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(true);
        Integer visits = (Integer) session.getAttribute("visits");
        int nextVisits = visits == null ? 1 : visits + 1;
        session.setAttribute("visits", nextVisits);
        Object requestCount = req.getServletContext().getAttribute("requestCount");
        Object lifecycle = req.getServletContext().getAttribute("appLifecycle");

        resp.setContentType("application/json; charset=UTF-8");
        resp.getWriter().write("""
                {"message":"Hello from Velo Servlet","contextPath":"%s","servletPath":"%s","visits":%d,"requestCount":%s,"lifecycle":"%s"}
                """.formatted(
                req.getContextPath(),
                req.getServletPath(),
                nextVisits,
                requestCount == null ? "null" : requestCount.toString(),
                lifecycle == null ? "unknown" : lifecycle.toString()).trim());
    }
}
