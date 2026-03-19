package io.velo.was.webadmin;

import io.velo.was.admin.client.AdminClient;
import io.velo.was.admin.client.LocalAdminClient;
import io.velo.was.config.ServerConfiguration;
import io.velo.was.servlet.SimpleServletApplication;
import io.velo.was.webadmin.api.AdminApiDocsServlet;
import io.velo.was.webadmin.api.AdminApiServlet;
import io.velo.was.webadmin.api.AdminSseServlet;
import io.velo.was.webadmin.servlet.AdminAuthFilter;
import io.velo.was.webadmin.servlet.AdminConsoleServlet;
import io.velo.was.webadmin.servlet.AdminDashboardServlet;
import io.velo.was.webadmin.servlet.AdminLoginServlet;
import io.velo.was.webadmin.servlet.AdminLogoutServlet;
import io.velo.was.webadmin.servlet.AdminStaticResourceServlet;
import io.velo.was.webadmin.servlet.page.ApplicationsPageServlet;
import io.velo.was.webadmin.servlet.page.ClustersPageServlet;
import io.velo.was.webadmin.servlet.page.DiagnosticsPageServlet;
import io.velo.was.webadmin.servlet.page.DomainPageServlet;
import io.velo.was.webadmin.servlet.page.HistoryPageServlet;
import io.velo.was.webadmin.servlet.page.MonitoringPageServlet;
import io.velo.was.webadmin.servlet.page.NodesPageServlet;
import io.velo.was.webadmin.servlet.page.ResourcesPageServlet;
import io.velo.was.webadmin.servlet.page.ScriptsPageServlet;
import io.velo.was.webadmin.servlet.page.SecurityPageServlet;
import io.velo.was.webadmin.servlet.page.ServersPageServlet;
import io.velo.was.webadmin.servlet.page.SettingsPageServlet;

/**
 * Factory that builds the Web Admin {@link SimpleServletApplication}.
 * <p>
 * Called by the bootstrap module when {@code server.webAdmin.enabled} is {@code true}.
 * The application is deployed at the configured context path (default {@code /admin}).
 */
public final class WebAdminApplication {

    private static final String APP_NAME = "velo-webadmin";

    private WebAdminApplication() {
    }

    /**
     * Creates the Web Admin servlet application.
     *
     * @param configuration server configuration (used to populate dashboard data)
     * @return a fully configured {@link SimpleServletApplication}
     */
    public static SimpleServletApplication create(ServerConfiguration configuration) {
        String contextPath = configuration.getServer().getWebAdmin().getContextPath();
        AdminClient adminClient = new LocalAdminClient(configuration);

        var builder = SimpleServletApplication.builder(APP_NAME, contextPath)
                .filter(new AdminAuthFilter())
                // Core pages
                .servlet("/", new AdminDashboardServlet(configuration))
                .servlet("/login", new AdminLoginServlet(configuration, adminClient))
                .servlet("/logout", new AdminLogoutServlet())
                .servlet("/console", new AdminConsoleServlet(configuration))
                // Management pages
                .servlet("/servers", new ServersPageServlet(configuration))
                .servlet("/clusters", new ClustersPageServlet(configuration))
                .servlet("/nodes", new NodesPageServlet(configuration))
                .servlet("/applications", new ApplicationsPageServlet(configuration))
                .servlet("/resources", new ResourcesPageServlet(configuration))
                .servlet("/monitoring", new MonitoringPageServlet(configuration))
                .servlet("/diagnostics", new DiagnosticsPageServlet(configuration))
                .servlet("/security", new SecurityPageServlet(configuration))
                .servlet("/history", new HistoryPageServlet(configuration))
                .servlet("/domain", new DomainPageServlet(configuration))
                .servlet("/scripts", new ScriptsPageServlet(configuration))
                .servlet("/settings", new SettingsPageServlet(configuration))
                // API endpoints
                .servlet("/api/*", new AdminApiServlet(configuration, adminClient))
                .servlet("/sse/*", new AdminSseServlet(configuration))
                // Static resources
                .servlet("/static/*", new AdminStaticResourceServlet());

        // Swagger API Documentation (conditional)
        if (configuration.getServer().getWebAdmin().isApiDocsEnabled()) {
            builder.servlet("/api-docs/*", new AdminApiDocsServlet(configuration));
        }

        return builder.build();
    }
}
