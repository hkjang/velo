package io.velo.was.admin.cli;

public enum CommandCategory {

    BASIC("Basic"),
    DOMAIN("Domain Management"),
    SERVER("Server Management"),
    CLUSTER("Cluster Management"),
    APPLICATION("Application Management"),
    DATASOURCE("Datasource Management"),
    JDBC("JDBC / Connection Pool"),
    JMS("JMS Management"),
    THREAD("Thread / Resource Management"),
    MONITORING("Monitoring"),
    LOG("Log Management"),
    JMX("JMX / MBean Management"),
    SECURITY("Security Management"),
    SCRIPT("Script / Automation");

    private final String displayName;

    CommandCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
