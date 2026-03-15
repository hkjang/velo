package io.velo.was.admin.command;

import io.velo.was.admin.VeloAdmin;
import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandCategory;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandRegistry;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;
import io.velo.was.admin.client.LocalAdminClient;
import io.velo.was.config.ServerConfiguration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 전체 67개 CLI 명령어에 대한 통합 테스트.
 * LocalAdminClient를 사용하여 모든 명령어의 실행을 검증한다.
 */
class AllCommandsTest {

    private static CommandRegistry registry;
    private static CommandContext context;
    private static StringWriter outputCapture;

    @BeforeAll
    static void setUp() {
        registry = VeloAdmin.createRegistry();
        ServerConfiguration config = new ServerConfiguration();
        AdminClient client = new LocalAdminClient(config);
        outputCapture = new StringWriter();
        PrintWriter writer = new PrintWriter(outputCapture, true);
        context = new CommandContext(client, writer, null);
    }

    // ── Helper ──

    private CommandResult exec(String commandName, String... args) {
        Command command = registry.find(commandName);
        assertNotNull(command, "Command not found: " + commandName);
        return command.execute(context, args);
    }

    private void assertSuccess(CommandResult result) {
        assertTrue(result.success(), "Expected success but got error: " + result.message());
    }

    private void assertError(CommandResult result) {
        assertFalse(result.success(), "Expected error but got success: " + result.message());
    }

    // ═══════════════════════════════════════════
    //  1. Registry Completeness
    // ═══════════════════════════════════════════

    @Test
    @DisplayName("Registry에 67개 명령어가 등록되어 있어야 한다")
    void registryShouldContainAllCommands() {
        List<Command> all = registry.all();
        assertEquals(74, all.size(), "Expected 74 commands, found: " + all.size());
    }

    @Test
    @DisplayName("모든 14개 카테고리가 존재해야 한다")
    void allCategoriesShouldExist() {
        Map<CommandCategory, List<Command>> grouped = registry.groupedByCategory();
        assertEquals(14, grouped.size(), "Expected 14 categories");
        for (CommandCategory cat : CommandCategory.values()) {
            assertTrue(grouped.containsKey(cat), "Missing category: " + cat.displayName());
        }
    }

    @Test
    @DisplayName("모든 명령어는 name, description, category를 가져야 한다")
    void allCommandsHaveMetadata() {
        for (Command cmd : registry.all()) {
            assertNotNull(cmd.name(), "name() null");
            assertFalse(cmd.name().isBlank(), "name() blank");
            assertNotNull(cmd.description(), "description() null for " + cmd.name());
            assertNotNull(cmd.category(), "category() null for " + cmd.name());
        }
    }

    // ═══════════════════════════════════════════
    //  2. Basic Commands
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("[Basic] 기본 CLI 명령어")
    class BasicCommands {

        @Test
        @DisplayName("help - 전체 명령어 목록")
        void help() {
            CommandResult r = exec("help");
            assertSuccess(r);
            assertTrue(r.message().contains("Available commands"));
        }

        @Test
        @DisplayName("help <command> - 특정 명령어 헬프")
        void helpSpecific() {
            CommandResult r = exec("help", "server-info");
            assertSuccess(r);
            assertTrue(r.message().contains("server-info"));
        }

        @Test
        @DisplayName("help <invalid> - 없는 명령어")
        void helpInvalid() {
            CommandResult r = exec("help", "nonexistent-cmd");
            assertError(r);
        }

        @Test
        @DisplayName("exit - CLI 종료 요청")
        void exit() {
            // Create separate context so we don't affect other tests
            ServerConfiguration config = new ServerConfiguration();
            CommandContext ctx = new CommandContext(new LocalAdminClient(config),
                    new PrintWriter(new StringWriter()), null);
            Command cmd = registry.find("exit");
            cmd.execute(ctx, new String[0]);
            assertTrue(ctx.isExitRequested());
        }

        @Test
        @DisplayName("quit - CLI 종료 요청")
        void quit() {
            ServerConfiguration config = new ServerConfiguration();
            CommandContext ctx = new CommandContext(new LocalAdminClient(config),
                    new PrintWriter(new StringWriter()), null);
            Command cmd = registry.find("quit");
            cmd.execute(ctx, new String[0]);
            assertTrue(ctx.isExitRequested());
        }

        @Test
        @DisplayName("version - 버전 확인")
        void version() {
            CommandResult r = exec("version");
            assertSuccess(r);
            assertTrue(r.message().contains("Velo WAS version"));
        }

        @Test
        @DisplayName("history - 히스토리 메시지")
        void history() {
            CommandResult r = exec("history");
            assertSuccess(r);
        }

        @Test
        @DisplayName("clear - 화면 초기화")
        void clear() {
            CommandResult r = exec("clear");
            assertTrue(r.success());
        }
    }

    // ═══════════════════════════════════════════
    //  3. Domain Commands
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("[Domain] 도메인 관리 명령어")
    class DomainCommands {

        @Test
        @DisplayName("list-domains - 도메인 목록")
        void listDomains() {
            CommandResult r = exec("list-domains");
            assertSuccess(r);
            assertTrue(r.message().contains("default"));
        }

        @Test
        @DisplayName("domain-info - 도메인 정보")
        void domainInfo() {
            CommandResult r = exec("domain-info", "default");
            assertSuccess(r);
            assertTrue(r.message().contains("Domain Name"));
            assertTrue(r.message().contains("RUNNING"));
        }

        @Test
        @DisplayName("domain-info (default) - 인자 없이 기본 도메인")
        void domainInfoDefault() {
            CommandResult r = exec("domain-info");
            assertSuccess(r);
        }

        @Test
        @DisplayName("create-domain - 도메인 생성")
        void createDomain() {
            CommandResult r = exec("create-domain", "test-domain");
            assertSuccess(r);
        }

        @Test
        @DisplayName("create-domain - 인자 누락")
        void createDomainNoArgs() {
            CommandResult r = exec("create-domain");
            assertError(r);
            assertTrue(r.message().contains("Usage"));
        }

        @Test
        @DisplayName("remove-domain - 존재하지 않는 도메인")
        void removeDomain() {
            CommandResult r = exec("remove-domain", "nonexistent-domain");
            assertError(r);
        }

        @Test
        @DisplayName("set-domain-property - 속성 설정")
        void setDomainProperty() {
            CommandResult r = exec("set-domain-property", "default", "key", "value");
            assertSuccess(r);
        }

        @Test
        @DisplayName("set-domain-property - 인자 부족")
        void setDomainPropertyNoArgs() {
            CommandResult r = exec("set-domain-property", "default");
            assertError(r);
            assertTrue(r.message().contains("Usage"));
        }

        @Test
        @DisplayName("get-domain-property - 존재하지 않는 속성")
        void getDomainProperty() {
            CommandResult r = exec("get-domain-property", "default", "nonexistent-key");
            assertError(r);
        }
    }

    // ═══════════════════════════════════════════
    //  4. Server Commands
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("[Server] 서버 관리 명령어")
    class ServerCommands {

        @Test
        @DisplayName("list-servers - 서버 목록")
        void listServers() {
            CommandResult r = exec("list-servers");
            assertSuccess(r);
            assertTrue(r.message().contains("velo-was"));
        }

        @Test
        @DisplayName("server-info - 서버 상세 정보")
        void serverInfo() {
            CommandResult r = exec("server-info");
            assertSuccess(r);
            assertTrue(r.message().contains("Server Name"));
            assertTrue(r.message().contains("RUNNING"));
        }

        @Test
        @DisplayName("start-server - 이미 실행 중")
        void startServer() {
            CommandResult r = exec("start-server", "velo-was");
            assertError(r);
        }

        @Test
        @DisplayName("start-server - 인자 누락")
        void startServerNoArgs() {
            CommandResult r = exec("start-server");
            assertError(r);
            assertTrue(r.message().contains("Usage"));
        }

        @Test
        @DisplayName("stop-server - 로컬 모드 미지원")
        void stopServer() {
            CommandResult r = exec("stop-server", "velo-was");
            assertError(r);
        }

        @Test
        @DisplayName("restart-server - 로컬 모드 미지원")
        void restartServer() {
            CommandResult r = exec("restart-server", "velo-was");
            assertError(r);
        }

        @Test
        @DisplayName("suspend-server - 로컬 모드 미지원")
        void suspendServer() {
            CommandResult r = exec("suspend-server", "velo-was");
            assertError(r);
        }

        @Test
        @DisplayName("resume-server - 로컬 모드 미지원")
        void resumeServer() {
            CommandResult r = exec("resume-server", "velo-was");
            assertError(r);
        }

        @Test
        @DisplayName("kill-server - 로컬 모드 미지원")
        void killServer() {
            CommandResult r = exec("kill-server", "velo-was");
            assertError(r);
        }
    }

    // ═══════════════════════════════════════════
    //  5. Cluster Commands
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("[Cluster] 클러스터 관리 명령어")
    class ClusterCommands {

        @Test
        @DisplayName("list-clusters - 빈 클러스터 목록")
        void listClusters() {
            CommandResult r = exec("list-clusters");
            assertSuccess(r);
        }

        @Test
        @DisplayName("cluster-info - 클러스터 없음")
        void clusterInfo() {
            CommandResult r = exec("cluster-info", "test-cluster");
            assertError(r);
        }

        @Test
        @DisplayName("start-cluster - 클러스터 없음")
        void startCluster() {
            CommandResult r = exec("start-cluster", "test-cluster");
            assertError(r);
        }

        @Test
        @DisplayName("stop-cluster - 클러스터 없음")
        void stopCluster() {
            CommandResult r = exec("stop-cluster", "test-cluster");
            assertError(r);
        }

        @Test
        @DisplayName("restart-cluster - 클러스터 없음")
        void restartCluster() {
            CommandResult r = exec("restart-cluster", "test-cluster");
            assertError(r);
        }

        @Test
        @DisplayName("add-server-to-cluster - 클러스터 없음")
        void addServerToCluster() {
            CommandResult r = exec("add-server-to-cluster", "test-cluster", "server1");
            assertError(r);
        }

        @Test
        @DisplayName("add-server-to-cluster - 인자 부족")
        void addServerToClusterNoArgs() {
            CommandResult r = exec("add-server-to-cluster");
            assertError(r);
            assertTrue(r.message().contains("Usage"));
        }

        @Test
        @DisplayName("remove-server-from-cluster - 클러스터 없음")
        void removeServerFromCluster() {
            CommandResult r = exec("remove-server-from-cluster", "test-cluster", "server1");
            assertError(r);
        }
    }

    // ═══════════════════════════════════════════
    //  6. Application Commands
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("[Application] 애플리케이션 관리 명령어")
    class ApplicationCommands {

        @Test
        @DisplayName("list-applications - 빈 앱 목록")
        void listApplications() {
            CommandResult r = exec("list-applications");
            assertSuccess(r);
        }

        @Test
        @DisplayName("application-info - 앱 없음")
        void applicationInfo() {
            CommandResult r = exec("application-info", "test-app");
            assertError(r);
        }

        @Test
        @DisplayName("deploy - 미구현")
        void deploy() {
            CommandResult r = exec("deploy", "/path/to/app.war", "/test");
            assertError(r);
        }

        @Test
        @DisplayName("deploy - 인자 누락")
        void deployNoArgs() {
            CommandResult r = exec("deploy");
            assertError(r);
            assertTrue(r.message().contains("Usage"));
        }

        @Test
        @DisplayName("undeploy - 미구현")
        void undeploy() {
            CommandResult r = exec("undeploy", "test-app");
            assertError(r);
        }

        @Test
        @DisplayName("redeploy - 미구현")
        void redeploy() {
            CommandResult r = exec("redeploy", "test-app");
            assertError(r);
        }

        @Test
        @DisplayName("start-application - 미구현")
        void startApplication() {
            CommandResult r = exec("start-application", "test-app");
            assertError(r);
        }

        @Test
        @DisplayName("stop-application - 미구현")
        void stopApplication() {
            CommandResult r = exec("stop-application", "test-app");
            assertError(r);
        }
    }

    // ═══════════════════════════════════════════
    //  7. Datasource Commands
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("[Datasource] 데이터소스 관리 명령어")
    class DatasourceCommands {

        @Test
        @DisplayName("list-datasources - 빈 데이터소스 목록")
        void listDatasources() {
            CommandResult r = exec("list-datasources");
            assertSuccess(r);
        }

        @Test
        @DisplayName("datasource-info - 데이터소스 없음")
        void datasourceInfo() {
            CommandResult r = exec("datasource-info", "test-ds");
            assertError(r);
        }

        @Test
        @DisplayName("enable-datasource - 데이터소스 없음")
        void enableDatasource() {
            CommandResult r = exec("enable-datasource", "test-ds");
            assertError(r);
        }

        @Test
        @DisplayName("disable-datasource - 데이터소스 없음")
        void disableDatasource() {
            CommandResult r = exec("disable-datasource", "test-ds");
            assertError(r);
        }

        @Test
        @DisplayName("test-datasource - 데이터소스 없음")
        void testDatasource() {
            CommandResult r = exec("test-datasource", "test-ds");
            assertError(r);
        }
    }

    // ═══════════════════════════════════════════
    //  8. JDBC / Connection Pool Commands
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("[JDBC] JDBC / Connection Pool 명령어")
    class JdbcCommands {

        @Test
        @DisplayName("list-jdbc-resources - 빈 JDBC 리소스 목록")
        void listJdbcResources() {
            CommandResult r = exec("list-jdbc-resources");
            assertSuccess(r);
        }

        @Test
        @DisplayName("jdbc-resource-info - JDBC 리소스 없음")
        void jdbcResourceInfo() {
            CommandResult r = exec("jdbc-resource-info", "test-jdbc");
            assertError(r);
        }

        @Test
        @DisplayName("reset-connection-pool - 풀 없음")
        void resetConnectionPool() {
            CommandResult r = exec("reset-connection-pool", "test-pool");
            assertError(r);
        }

        @Test
        @DisplayName("flush-connection-pool - 풀 없음")
        void flushConnectionPool() {
            CommandResult r = exec("flush-connection-pool", "test-pool");
            assertError(r);
        }
    }

    // ═══════════════════════════════════════════
    //  9. JMS Commands
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("[JMS] JMS 관리 명령어")
    class JmsCommands {

        @Test
        @DisplayName("list-jms-servers - 빈 JMS 서버 목록")
        void listJmsServers() {
            CommandResult r = exec("list-jms-servers");
            assertSuccess(r);
        }

        @Test
        @DisplayName("jms-server-info - JMS 서버 없음")
        void jmsServerInfo() {
            CommandResult r = exec("jms-server-info", "test-jms");
            assertError(r);
        }

        @Test
        @DisplayName("list-jms-destinations - 빈 destination 목록")
        void listJmsDestinations() {
            CommandResult r = exec("list-jms-destinations");
            assertSuccess(r);
        }

        @Test
        @DisplayName("jms-destination-info - destination 없음")
        void jmsDestinationInfo() {
            CommandResult r = exec("jms-destination-info", "test-dest");
            assertError(r);
        }

        @Test
        @DisplayName("purge-jms-queue - 큐 없음")
        void purgeJmsQueue() {
            CommandResult r = exec("purge-jms-queue", "test-queue");
            assertError(r);
        }
    }

    // ═══════════════════════════════════════════
    //  10. Thread / Resource Commands
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("[Thread] 스레드 / 리소스 관리 명령어")
    class ThreadCommands {

        @Test
        @DisplayName("list-thread-pools - 스레드 풀 목록")
        void listThreadPools() {
            CommandResult r = exec("list-thread-pools");
            assertSuccess(r);
            assertTrue(r.message().contains("boss"));
            assertTrue(r.message().contains("worker"));
            assertTrue(r.message().contains("business"));
        }

        @Test
        @DisplayName("thread-pool-info - boss 풀 정보")
        void threadPoolInfo() {
            CommandResult r = exec("thread-pool-info", "boss");
            assertSuccess(r);
        }

        @Test
        @DisplayName("thread-pool-info - 없는 풀")
        void threadPoolInfoNotFound() {
            CommandResult r = exec("thread-pool-info", "nonexistent");
            assertError(r);
        }

        @Test
        @DisplayName("reset-thread-pool - boss 풀 초기화")
        void resetThreadPool() {
            CommandResult r = exec("reset-thread-pool", "boss");
            assertSuccess(r);
        }

        @Test
        @DisplayName("reset-thread-pool - 없는 풀")
        void resetThreadPoolNotFound() {
            CommandResult r = exec("reset-thread-pool", "nonexistent");
            assertError(r);
        }

        @Test
        @DisplayName("resource-info - 리소스 상태 조회")
        void resourceInfo() {
            CommandResult r = exec("resource-info");
            assertSuccess(r);
            assertTrue(r.message().contains("Available Processors"));
        }
    }

    // ═══════════════════════════════════════════
    //  11. Monitoring Commands
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("[Monitoring] 모니터링 명령어")
    class MonitoringCommands {

        @Test
        @DisplayName("system-info - 시스템 정보")
        void systemInfo() {
            CommandResult r = exec("system-info");
            assertSuccess(r);
            assertTrue(r.message().contains("OS Name"));
        }

        @Test
        @DisplayName("jvm-info - JVM 정보")
        void jvmInfo() {
            CommandResult r = exec("jvm-info");
            assertSuccess(r);
            assertTrue(r.message().contains("JVM Name"));
        }

        @Test
        @DisplayName("memory-info - 메모리 상태")
        void memoryInfo() {
            CommandResult r = exec("memory-info");
            assertSuccess(r);
            assertTrue(r.message().contains("Heap Used"));
        }

        @Test
        @DisplayName("thread-info - 스레드 상태")
        void threadInfo() {
            CommandResult r = exec("thread-info");
            assertSuccess(r);
            assertTrue(r.message().contains("Thread Count"));
        }

        @Test
        @DisplayName("transaction-info - 트랜잭션 정보")
        void transactionInfo() {
            CommandResult r = exec("transaction-info");
            assertSuccess(r);
            assertTrue(r.message().contains("Active Transactions"));
        }
    }

    // ═══════════════════════════════════════════
    //  12. Log Commands
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("[Log] 로그 관리 명령어")
    class LogCommands {

        @Test
        @DisplayName("list-loggers - 로거 목록")
        void listLoggers() {
            CommandResult r = exec("list-loggers");
            assertSuccess(r);
            assertTrue(r.message().contains("ROOT"));
        }

        @Test
        @DisplayName("logger-info - 로거 정보")
        void loggerInfo() {
            CommandResult r = exec("logger-info", "ROOT");
            assertSuccess(r);
            assertTrue(r.message().contains("Logger Name"));
            assertTrue(r.message().contains("INFO"));
        }

        @Test
        @DisplayName("logger-info - 없는 로거")
        void loggerInfoNotFound() {
            CommandResult r = exec("logger-info", "nonexistent.logger");
            assertError(r);
        }

        @Test
        @DisplayName("get-log-level - 로그 레벨 조회")
        void getLogLevel() {
            CommandResult r = exec("get-log-level", "ROOT");
            assertSuccess(r);
            assertTrue(r.message().contains("INFO"));
        }

        @Test
        @DisplayName("set-log-level - 로그 레벨 변경")
        void setLogLevel() {
            CommandResult r = exec("set-log-level", "ROOT", "DEBUG");
            assertSuccess(r);
            // Verify changed
            CommandResult r2 = exec("get-log-level", "ROOT");
            assertTrue(r2.message().contains("DEBUG"));
            // Restore
            exec("set-log-level", "ROOT", "INFO");
        }

        @Test
        @DisplayName("set-log-level - 잘못된 레벨")
        void setLogLevelInvalid() {
            CommandResult r = exec("set-log-level", "ROOT", "INVALID");
            assertError(r);
        }
    }

    // ═══════════════════════════════════════════
    //  13. JMX / MBean Commands
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("[JMX] JMX / MBean 관리 명령어")
    class JmxCommands {

        @Test
        @DisplayName("list-mbeans - MBean 목록")
        void listMBeans() {
            CommandResult r = exec("list-mbeans");
            assertSuccess(r);
            assertTrue(r.message().contains("Total:"));
        }

        @Test
        @DisplayName("get-mbean-attribute - Runtime HeapMemoryUsage")
        void getMBeanAttribute() {
            CommandResult r = exec("get-mbean-attribute",
                    "java.lang:type=Runtime", "VmName");
            assertSuccess(r);
            assertTrue(r.message().contains("VmName"));
        }

        @Test
        @DisplayName("get-mbean-attribute - 잘못된 MBean")
        void getMBeanAttributeInvalid() {
            CommandResult r = exec("get-mbean-attribute",
                    "invalid:type=Nonexistent", "attr");
            assertError(r);
        }

        @Test
        @DisplayName("set-mbean-attribute - 인자 부족")
        void setMBeanAttributeNoArgs() {
            CommandResult r = exec("set-mbean-attribute", "mbean");
            assertError(r);
            assertTrue(r.message().contains("Usage"));
        }

        @Test
        @DisplayName("invoke-mbean-operation - 인자 부족")
        void invokeMBeanOperationNoArgs() {
            CommandResult r = exec("invoke-mbean-operation");
            assertError(r);
            assertTrue(r.message().contains("Usage"));
        }
    }

    // ═══════════════════════════════════════════
    //  14. Security Commands
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("[Security] 보안 관리 명령어")
    class SecurityCommands {

        @Test
        @DisplayName("list-users - 사용자 목록")
        void listUsers() {
            CommandResult r = exec("list-users");
            assertSuccess(r);
            assertTrue(r.message().contains("admin"));
        }

        @Test
        @DisplayName("create-user + remove-user - 생성/삭제 사이클")
        void createAndRemoveUser() {
            CommandResult r1 = exec("create-user", "testuser", "testpass");
            assertSuccess(r1);

            CommandResult r2 = exec("remove-user", "testuser");
            assertSuccess(r2);
        }

        @Test
        @DisplayName("create-user - 중복 사용자")
        void createUserDuplicate() {
            CommandResult r = exec("create-user", "admin", "pass");
            assertError(r);
        }

        @Test
        @DisplayName("remove-user - 없는 사용자")
        void removeUserNotFound() {
            CommandResult r = exec("remove-user", "nonexistent");
            assertError(r);
        }

        @Test
        @DisplayName("change-password - 비밀번호 변경")
        void changePassword() {
            CommandResult r = exec("change-password", "admin", "newpass");
            assertSuccess(r);
            // Restore
            exec("change-password", "admin", "admin");
        }

        @Test
        @DisplayName("change-password - 없는 사용자")
        void changePasswordNotFound() {
            CommandResult r = exec("change-password", "nobody", "pass");
            assertError(r);
        }

        @Test
        @DisplayName("list-roles - 역할 목록")
        void listRoles() {
            CommandResult r = exec("list-roles");
            assertSuccess(r);
            assertTrue(r.message().contains("admin"));
        }
    }

    // ═══════════════════════════════════════════
    //  15. Script / Automation Commands
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("[Script] 스크립트 / 자동화 명령어")
    class ScriptCommands {

        @Test
        @DisplayName("record-script + stop-record 사이클")
        void recordAndStop(@TempDir Path tempDir) {
            Path scriptFile = tempDir.resolve("test-script.velo");
            ServerConfiguration config = new ServerConfiguration();
            CommandContext ctx = new CommandContext(new LocalAdminClient(config),
                    new PrintWriter(new StringWriter()), null);

            Command record = registry.find("record-script");
            CommandResult r1 = record.execute(ctx, new String[]{scriptFile.toString()});
            assertSuccess(r1);
            assertTrue(ctx.isRecording());

            Command stop = registry.find("stop-record");
            CommandResult r2 = stop.execute(ctx, new String[0]);
            assertSuccess(r2);
            assertFalse(ctx.isRecording());
            assertTrue(Files.exists(scriptFile));
        }

        @Test
        @DisplayName("record-script - 인자 누락")
        void recordScriptNoArgs() {
            CommandResult r = exec("record-script");
            assertError(r);
            assertTrue(r.message().contains("Usage"));
        }

        @Test
        @DisplayName("stop-record - 녹화 중이 아님")
        void stopRecordNotRecording() {
            CommandResult r = exec("stop-record");
            assertError(r);
        }

        @Test
        @DisplayName("run-script - 파일 없음")
        void runScriptNotFound() {
            CommandResult r = exec("run-script", "/nonexistent/script.velo");
            assertError(r);
        }

        @Test
        @DisplayName("run-script - 스크립트 실행")
        void runScript(@TempDir Path tempDir) throws Exception {
            Path script = tempDir.resolve("test.velo");
            Files.writeString(script, "# comment\nversion\nlist-servers\n");

            ServerConfiguration config = new ServerConfiguration();
            StringWriter sw = new StringWriter();
            CommandContext ctx = new CommandContext(new LocalAdminClient(config),
                    new PrintWriter(sw), null);

            Command cmd = registry.find("run-script");
            CommandResult r = cmd.execute(ctx, new String[]{script.toString()});
            assertSuccess(r);
        }
    }

    // ═══════════════════════════════════════════
    //  16. Usage Validation (all commands with args)
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("[Usage] 인자 누락 시 Usage 메시지 검증")
    class UsageValidation {

        @Test
        @DisplayName("인자 필요 명령어 - 인자 없이 호출 시 에러")
        void commandsRequiringArgsReturnUsageOnMissing() {
            String[] commandsNeedingArgs = {
                    "start-server", "stop-server", "restart-server",
                    "suspend-server", "resume-server", "kill-server",
                    "cluster-info", "start-cluster", "stop-cluster", "restart-cluster",
                    "create-domain", "remove-domain",
                    "application-info", "undeploy", "redeploy",
                    "start-application", "stop-application",
                    "datasource-info", "enable-datasource", "disable-datasource",
                    "test-datasource",
                    "jdbc-resource-info", "reset-connection-pool", "flush-connection-pool",
                    "jms-server-info", "jms-destination-info", "purge-jms-queue",
                    "thread-pool-info", "reset-thread-pool",
                    "logger-info", "get-log-level",
                    "create-user", "remove-user", "change-password",
                    "record-script", "run-script"
            };

            for (String cmdName : commandsNeedingArgs) {
                Command cmd = registry.find(cmdName);
                assertNotNull(cmd, "Command not found: " + cmdName);
                CommandResult r = cmd.execute(context, new String[0]);
                assertFalse(r.success(),
                        cmdName + " should fail with no args, got: " + r.message());
            }
        }
    }
}
