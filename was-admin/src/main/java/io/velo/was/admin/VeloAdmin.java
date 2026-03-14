package io.velo.was.admin;

import io.velo.was.admin.cli.CliShell;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandRegistry;
import io.velo.was.admin.client.AdminClient;
import io.velo.was.admin.client.LocalAdminClient;
import io.velo.was.admin.client.RemoteAdminClient;
import io.velo.was.config.ServerConfiguration;

import io.velo.was.admin.command.basic.*;
import io.velo.was.admin.command.domain.*;
import io.velo.was.admin.command.server.*;
import io.velo.was.admin.command.cluster.*;
import io.velo.was.admin.command.application.*;
import io.velo.was.admin.command.datasource.*;
import io.velo.was.admin.command.jdbc.*;
import io.velo.was.admin.command.jms.*;
import io.velo.was.admin.command.thread.*;
import io.velo.was.admin.command.monitoring.*;
import io.velo.was.admin.command.log.*;
import io.velo.was.admin.command.jmx.*;
import io.velo.was.admin.command.security.*;
import io.velo.was.admin.command.script.*;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class VeloAdmin {

    private static final String VERSION = "0.1.0";

    public static void main(String[] args) throws Exception {
        AdminClient client;
        Path configPath = null;

        if (args.length >= 2 && "--remote".equals(args[0])) {
            // --remote host:port
            String[] hostPort = args[1].split(":");
            String host = hostPort[0];
            int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 8080;
            client = new RemoteAdminClient(host, port);
        } else {
            // Local mode: load configuration
            configPath = args.length > 0 ? Path.of(args[0]) : Path.of("conf/server.yaml");
            ServerConfiguration configuration = loadConfiguration(configPath);
            client = new LocalAdminClient(configuration);
        }

        try (client) {
            CommandRegistry registry = createRegistry();
            PrintWriter output = new PrintWriter(System.out, true);
            CommandContext context = new CommandContext(client, output, configPath);

            CliShell shell = new CliShell(registry, context, VERSION);
            shell.run();
        }
    }

    public static CommandRegistry createRegistry() {
        CommandRegistry registry = new CommandRegistry();

        // ── Basic ──
        registry.register(new HelpCommand(registry));
        registry.register(new ExitCommand("exit"));
        registry.register(new ExitCommand("quit"));
        registry.register(new ClearCommand());
        registry.register(new HistoryCommand());
        registry.register(new VersionCommand(VERSION));

        // ── Domain ──
        registry.register(new DomainInfoCommand());
        registry.register(new ListDomainsCommand());
        registry.register(new CreateDomainCommand());
        registry.register(new RemoveDomainCommand());
        registry.register(new SetDomainPropertyCommand());
        registry.register(new GetDomainPropertyCommand());

        // ── Server ──
        registry.register(new ListServersCommand());
        registry.register(new ServerInfoCommand());
        registry.register(new StartServerCommand());
        registry.register(new StopServerCommand());
        registry.register(new RestartServerCommand());
        registry.register(new SuspendServerCommand());
        registry.register(new ResumeServerCommand());
        registry.register(new KillServerCommand());

        // ── Cluster ──
        registry.register(new ListClustersCommand());
        registry.register(new ClusterInfoCommand());
        registry.register(new StartClusterCommand());
        registry.register(new StopClusterCommand());
        registry.register(new RestartClusterCommand());
        registry.register(new AddServerToClusterCommand());
        registry.register(new RemoveServerFromClusterCommand());

        // ── Application ──
        registry.register(new DeployCommand());
        registry.register(new UndeployCommand());
        registry.register(new RedeployCommand());
        registry.register(new ListApplicationsCommand());
        registry.register(new ApplicationInfoCommand());
        registry.register(new StartApplicationCommand());
        registry.register(new StopApplicationCommand());

        // ── Datasource ──
        registry.register(new ListDatasourcesCommand());
        registry.register(new DatasourceInfoCommand());
        registry.register(new EnableDatasourceCommand());
        registry.register(new DisableDatasourceCommand());
        registry.register(new TestDatasourceCommand());

        // ── JDBC / Connection Pool ──
        registry.register(new ListJdbcResourcesCommand());
        registry.register(new JdbcResourceInfoCommand());
        registry.register(new ResetConnectionPoolCommand());
        registry.register(new FlushConnectionPoolCommand());

        // ── JMS ──
        registry.register(new ListJmsServersCommand());
        registry.register(new JmsServerInfoCommand());
        registry.register(new ListJmsDestinationsCommand());
        registry.register(new JmsDestinationInfoCommand());
        registry.register(new PurgeJmsQueueCommand());

        // ── Thread / Resource ──
        registry.register(new ListThreadPoolsCommand());
        registry.register(new ThreadPoolInfoCommand());
        registry.register(new ResetThreadPoolCommand());
        registry.register(new ResourceInfoCommand());

        // ── Monitoring ──
        registry.register(new SystemInfoCommand());
        registry.register(new JvmInfoCommand());
        registry.register(new MemoryInfoCommand());
        registry.register(new ThreadInfoCommand());
        registry.register(new TransactionInfoCommand());

        // ── Log ──
        registry.register(new ListLoggersCommand());
        registry.register(new LoggerInfoCommand());
        registry.register(new GetLogLevelCommand());
        registry.register(new SetLogLevelCommand());

        // ── JMX / MBean ──
        registry.register(new ListMBeansCommand());
        registry.register(new GetMBeanAttributeCommand());
        registry.register(new SetMBeanAttributeCommand());
        registry.register(new InvokeMBeanOperationCommand());

        // ── Security ──
        registry.register(new ListUsersCommand());
        registry.register(new CreateUserCommand());
        registry.register(new RemoveUserCommand());
        registry.register(new ChangePasswordCommand());
        registry.register(new ListRolesCommand());

        // ── Script / Automation ──
        registry.register(new RunScriptCommand(registry));
        registry.register(new RecordScriptCommand());
        registry.register(new StopRecordCommand());

        return registry;
    }

    private static ServerConfiguration loadConfiguration(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            System.err.println("Configuration file not found: " + configPath);
            System.err.println("Using default configuration.");
            return new ServerConfiguration();
        }
        try (InputStream input = Files.newInputStream(configPath)) {
            Yaml yaml = new Yaml();
            return yaml.loadAs(input, ServerConfiguration.class);
        }
    }
}
