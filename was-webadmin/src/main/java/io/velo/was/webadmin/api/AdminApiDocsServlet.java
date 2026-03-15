package io.velo.was.webadmin.api;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.servlet.AdminPageLayout;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Serves OpenAPI 3.0 specification and Swagger UI for the Velo Web Admin REST API.
 * <p>
 * Endpoints:
 * <ul>
 *   <li>{@code GET /api-docs}       → OpenAPI 3.0 JSON specification</li>
 *   <li>{@code GET /api-docs/ui}    → Swagger UI HTML page</li>
 * </ul>
 * <p>
 * Enabled conditionally via {@code server.webAdmin.apiDocsEnabled} configuration.
 */
public class AdminApiDocsServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public AdminApiDocsServlet(ServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        if ("/ui".equals(pathInfo)) {
            serveSwaggerUi(req, resp);
        } else {
            serveOpenApiSpec(req, resp);
        }
    }

    private void serveOpenApiSpec(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");

        ServerConfiguration.Server server = configuration.getServer();
        String ctx = server.getWebAdmin().getContextPath();
        int port = server.getListener().getPort();
        String host = server.getListener().getHost();
        if ("0.0.0.0".equals(host)) host = "localhost";

        PrintWriter out = resp.getWriter();
        out.write("""
                {
                  "openapi": "3.0.3",
                  "info": {
                    "title": "Velo WAS Admin API",
                    "description": "REST API for the Velo Web Application Server administration console. Provides server management, application deployment, monitoring, diagnostics, security, and configuration management capabilities.",
                    "version": "0.1.0",
                    "contact": {
                      "name": "Velo WAS"
                    },
                    "license": {
                      "name": "Proprietary"
                    }
                  },
                  "servers": [
                    {
                      "url": "%s",
                      "description": "Current server"
                    }
                  ],
                  "tags": [
                    {"name": "Status", "description": "Server status and health information"},
                    {"name": "Servers", "description": "Server instance management"},
                    {"name": "Applications", "description": "Application deployment and management"},
                    {"name": "Resources", "description": "Resource monitoring (memory, connections)"},
                    {"name": "Monitoring", "description": "Metrics and performance monitoring"},
                    {"name": "Diagnostics", "description": "Thread dumps, JVM info, system info"},
                    {"name": "Security", "description": "User and session management"},
                    {"name": "Configuration", "description": "Server configuration management"},
                    {"name": "Commands", "description": "CLI command execution"},
                    {"name": "Audit", "description": "Audit trail and history"},
                    {"name": "Clusters", "description": "Cluster management"},
                    {"name": "Nodes", "description": "Node management"}
                  ],
                  "paths": {
                    "/api/status": {
                      "get": {
                        "tags": ["Status"],
                        "summary": "Get server status",
                        "description": "Returns current server status including uptime, memory usage, thread count, and basic server information.",
                        "operationId": "getStatus",
                        "responses": {
                          "200": {
                            "description": "Server status",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "$ref": "#/components/schemas/ServerStatus"
                                }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/servers": {
                      "get": {
                        "tags": ["Servers"],
                        "summary": "List all servers",
                        "description": "Returns a list of all managed server instances with their status, host, port, and thread configuration.",
                        "operationId": "listServers",
                        "responses": {
                          "200": {
                            "description": "Server list",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "servers": {
                                      "type": "array",
                                      "items": { "$ref": "#/components/schemas/ServerInfo" }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/applications": {
                      "get": {
                        "tags": ["Applications"],
                        "summary": "List all applications",
                        "description": "Returns deployed and internal applications with context paths, status, servlet/filter counts.",
                        "operationId": "listApplications",
                        "responses": {
                          "200": {
                            "description": "Application list",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "applications": {
                                      "type": "array",
                                      "items": { "$ref": "#/components/schemas/ApplicationInfo" }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/resources": {
                      "get": {
                        "tags": ["Resources"],
                        "summary": "Get resource usage",
                        "description": "Returns heap and non-heap memory usage statistics.",
                        "operationId": "getResources",
                        "responses": {
                          "200": {
                            "description": "Resource usage",
                            "content": {
                              "application/json": {
                                "schema": { "$ref": "#/components/schemas/ResourceInfo" }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/threads": {
                      "get": {
                        "tags": ["Diagnostics"],
                        "summary": "Get thread information",
                        "description": "Returns all JVM threads with state, daemon status, and deadlock detection.",
                        "operationId": "getThreads",
                        "responses": {
                          "200": {
                            "description": "Thread information",
                            "content": {
                              "application/json": {
                                "schema": { "$ref": "#/components/schemas/ThreadInfo" }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/monitoring": {
                      "get": {
                        "tags": ["Monitoring"],
                        "summary": "Get metrics snapshot",
                        "description": "Returns current metrics snapshot including request counts, response times, and error rates.",
                        "operationId": "getMonitoring",
                        "responses": {
                          "200": {
                            "description": "Metrics snapshot",
                            "content": {
                              "application/json": {
                                "schema": { "type": "object" }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/commands": {
                      "get": {
                        "tags": ["Commands"],
                        "summary": "List available CLI commands",
                        "description": "Returns all registered CLI commands with name, description, category, and usage.",
                        "operationId": "listCommands",
                        "responses": {
                          "200": {
                            "description": "Command list",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "commands": {
                                      "type": "array",
                                      "items": { "$ref": "#/components/schemas/CommandInfo" }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/execute": {
                      "post": {
                        "tags": ["Commands"],
                        "summary": "Execute a CLI command",
                        "description": "Executes any registered CLI command via the web console. Provides full CLI parity.",
                        "operationId": "executeCommand",
                        "requestBody": {
                          "required": true,
                          "content": {
                            "application/json": {
                              "schema": {
                                "type": "object",
                                "required": ["command"],
                                "properties": {
                                  "command": {
                                    "type": "string",
                                    "description": "The CLI command to execute",
                                    "example": "status"
                                  }
                                }
                              }
                            }
                          }
                        },
                        "responses": {
                          "200": {
                            "description": "Command result",
                            "content": {
                              "application/json": {
                                "schema": { "$ref": "#/components/schemas/CommandResult" }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/config": {
                      "get": {
                        "tags": ["Configuration"],
                        "summary": "Get server configuration",
                        "description": "Returns the current server.yaml configuration file content.",
                        "operationId": "getConfig",
                        "responses": {
                          "200": {
                            "description": "Configuration content",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "path": { "type": "string" },
                                    "content": { "type": "string" }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/config/save": {
                      "post": {
                        "tags": ["Configuration"],
                        "summary": "Save configuration draft",
                        "description": "Creates a configuration change draft for review and approval workflow.",
                        "operationId": "saveConfig",
                        "requestBody": {
                          "required": true,
                          "content": {
                            "application/json": {
                              "schema": {
                                "type": "object",
                                "required": ["content"],
                                "properties": {
                                  "content": { "type": "string", "description": "YAML configuration content" }
                                }
                              }
                            }
                          }
                        },
                        "responses": {
                          "200": {
                            "description": "Draft created",
                            "content": {
                              "application/json": {
                                "schema": { "$ref": "#/components/schemas/CommandResult" }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/jvm": {
                      "get": {
                        "tags": ["Diagnostics"],
                        "summary": "Get JVM information",
                        "description": "Returns JVM vendor, version, memory settings, garbage collector info.",
                        "operationId": "getJvmInfo",
                        "responses": {
                          "200": {
                            "description": "JVM information",
                            "content": {
                              "application/json": {
                                "schema": { "type": "object", "additionalProperties": { "type": "string" } }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/system": {
                      "get": {
                        "tags": ["Diagnostics"],
                        "summary": "Get system information",
                        "description": "Returns OS name, version, architecture, CPU count, and system properties.",
                        "operationId": "getSystemInfo",
                        "responses": {
                          "200": {
                            "description": "System information",
                            "content": {
                              "application/json": {
                                "schema": { "type": "object", "additionalProperties": { "type": "string" } }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/threadpools": {
                      "get": {
                        "tags": ["Diagnostics"],
                        "summary": "List thread pools",
                        "description": "Returns all managed thread pools with active count, pool size, and max pool size.",
                        "operationId": "listThreadPools",
                        "responses": {
                          "200": {
                            "description": "Thread pool list",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "threadPools": {
                                      "type": "array",
                                      "items": { "$ref": "#/components/schemas/ThreadPoolInfo" }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/loggers": {
                      "get": {
                        "tags": ["Configuration"],
                        "summary": "List loggers",
                        "description": "Returns all configured loggers with their current log levels.",
                        "operationId": "listLoggers",
                        "responses": {
                          "200": {
                            "description": "Logger list",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "loggers": {
                                      "type": "array",
                                      "items": { "$ref": "#/components/schemas/LoggerInfo" }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/loggers/set": {
                      "post": {
                        "tags": ["Configuration"],
                        "summary": "Set logger level",
                        "description": "Dynamically changes the log level of a specific logger at runtime.",
                        "operationId": "setLogLevel",
                        "requestBody": {
                          "required": true,
                          "content": {
                            "application/json": {
                              "schema": {
                                "type": "object",
                                "required": ["logger", "level"],
                                "properties": {
                                  "logger": { "type": "string", "example": "io.velo.was" },
                                  "level": { "type": "string", "enum": ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"], "example": "DEBUG" }
                                }
                              }
                            }
                          }
                        },
                        "responses": {
                          "200": {
                            "description": "Level updated",
                            "content": {
                              "application/json": {
                                "schema": { "$ref": "#/components/schemas/CommandResult" }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/users": {
                      "get": {
                        "tags": ["Security"],
                        "summary": "List users",
                        "description": "Returns all registered admin users.",
                        "operationId": "listUsers",
                        "responses": {
                          "200": {
                            "description": "User list",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "users": {
                                      "type": "array",
                                      "items": { "$ref": "#/components/schemas/UserInfo" }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/users/create": {
                      "post": {
                        "tags": ["Security"],
                        "summary": "Create user",
                        "description": "Creates a new admin user account.",
                        "operationId": "createUser",
                        "requestBody": {
                          "required": true,
                          "content": {
                            "application/json": {
                              "schema": {
                                "type": "object",
                                "required": ["username", "password"],
                                "properties": {
                                  "username": { "type": "string" },
                                  "password": { "type": "string", "format": "password" }
                                }
                              }
                            }
                          }
                        },
                        "responses": {
                          "200": {
                            "description": "User created",
                            "content": {
                              "application/json": {
                                "schema": { "$ref": "#/components/schemas/CommandResult" }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/users/remove": {
                      "post": {
                        "tags": ["Security"],
                        "summary": "Remove user",
                        "description": "Removes an existing admin user account.",
                        "operationId": "removeUser",
                        "requestBody": {
                          "required": true,
                          "content": {
                            "application/json": {
                              "schema": {
                                "type": "object",
                                "required": ["username"],
                                "properties": {
                                  "username": { "type": "string" }
                                }
                              }
                            }
                          }
                        },
                        "responses": {
                          "200": {
                            "description": "User removed",
                            "content": {
                              "application/json": {
                                "schema": { "$ref": "#/components/schemas/CommandResult" }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/users/change-password": {
                      "post": {
                        "tags": ["Security"],
                        "summary": "Change user password",
                        "description": "Changes the password of an existing admin user.",
                        "operationId": "changePassword",
                        "requestBody": {
                          "required": true,
                          "content": {
                            "application/json": {
                              "schema": {
                                "type": "object",
                                "required": ["username", "password"],
                                "properties": {
                                  "username": { "type": "string" },
                                  "password": { "type": "string", "format": "password" }
                                }
                              }
                            }
                          }
                        },
                        "responses": {
                          "200": {
                            "description": "Password changed",
                            "content": {
                              "application/json": {
                                "schema": { "$ref": "#/components/schemas/CommandResult" }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/audit": {
                      "get": {
                        "tags": ["Audit"],
                        "summary": "Get audit events",
                        "description": "Returns audit trail events with optional filtering by user or action.",
                        "operationId": "getAuditEvents",
                        "parameters": [
                          { "name": "limit", "in": "query", "schema": { "type": "integer", "default": 100 }, "description": "Maximum number of events to return" },
                          { "name": "user", "in": "query", "schema": { "type": "string" }, "description": "Filter by username" },
                          { "name": "action", "in": "query", "schema": { "type": "string" }, "description": "Filter by action type" }
                        ],
                        "responses": {
                          "200": {
                            "description": "Audit events",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "events": { "type": "array", "items": { "$ref": "#/components/schemas/AuditEvent" } },
                                    "total": { "type": "integer" }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/drafts": {
                      "get": {
                        "tags": ["Configuration"],
                        "summary": "List configuration drafts",
                        "description": "Returns configuration change drafts with optional status filtering.",
                        "operationId": "listDrafts",
                        "parameters": [
                          { "name": "status", "in": "query", "schema": { "type": "string", "enum": ["PENDING", "VALIDATED", "REVIEWED", "APPROVED", "APPLIED", "REJECTED", "ROLLED_BACK"] } }
                        ],
                        "responses": {
                          "200": {
                            "description": "Draft list",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "drafts": { "type": "array", "items": { "type": "object" } },
                                    "total": { "type": "integer" }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/drafts/create": {
                      "post": {
                        "tags": ["Configuration"],
                        "summary": "Create configuration draft",
                        "description": "Creates a new configuration change draft for the approval workflow.",
                        "operationId": "createDraft",
                        "requestBody": {
                          "required": true,
                          "content": {
                            "application/json": {
                              "schema": {
                                "type": "object",
                                "required": ["target"],
                                "properties": {
                                  "target": { "type": "string", "description": "Target configuration file" },
                                  "description": { "type": "string" },
                                  "changes": { "type": "string" }
                                }
                              }
                            }
                          }
                        },
                        "responses": {
                          "200": {
                            "description": "Draft created",
                            "content": {
                              "application/json": {
                                "schema": { "type": "object" }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/clusters": {
                      "get": {
                        "tags": ["Clusters"],
                        "summary": "List clusters",
                        "description": "Returns all configured clusters with member count and status.",
                        "operationId": "listClusters",
                        "responses": {
                          "200": {
                            "description": "Cluster list",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "clusters": {
                                      "type": "array",
                                      "items": { "$ref": "#/components/schemas/ClusterInfo" }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/api/nodes": {
                      "get": {
                        "tags": ["Nodes"],
                        "summary": "List nodes",
                        "description": "Returns all nodes with OS info, CPU count, Java version, and associated servers.",
                        "operationId": "listNodes",
                        "responses": {
                          "200": {
                            "description": "Node list",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "nodes": {
                                      "type": "array",
                                      "items": { "$ref": "#/components/schemas/NodeInfo" }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  },
                  "components": {
                    "schemas": {
                      "ServerStatus": {
                        "type": "object",
                        "properties": {
                          "status": { "type": "string", "enum": ["RUNNING", "STOPPED", "STARTING", "FAILED"] },
                          "serverName": { "type": "string" },
                          "nodeId": { "type": "string" },
                          "host": { "type": "string" },
                          "port": { "type": "integer" },
                          "tlsEnabled": { "type": "boolean" },
                          "uptimeMs": { "type": "integer", "format": "int64" },
                          "heapUsedBytes": { "type": "integer", "format": "int64" },
                          "heapMaxBytes": { "type": "integer", "format": "int64" },
                          "threadCount": { "type": "integer" },
                          "availableProcessors": { "type": "integer" },
                          "javaVersion": { "type": "string" }
                        }
                      },
                      "ServerInfo": {
                        "type": "object",
                        "properties": {
                          "name": { "type": "string" },
                          "nodeId": { "type": "string" },
                          "status": { "type": "string" },
                          "host": { "type": "string" },
                          "port": { "type": "integer" },
                          "uptimeMs": { "type": "integer", "format": "int64" },
                          "transport": { "type": "string" },
                          "tlsEnabled": { "type": "boolean" },
                          "bossThreads": { "type": "integer" },
                          "workerThreads": { "type": "integer" },
                          "businessThreads": { "type": "integer" }
                        }
                      },
                      "ApplicationInfo": {
                        "type": "object",
                        "properties": {
                          "name": { "type": "string" },
                          "contextPath": { "type": "string" },
                          "status": { "type": "string", "enum": ["RUNNING", "STOPPED", "FAILED", "DEPLOYING"] },
                          "type": { "type": "string", "enum": ["INTERNAL", "DEPLOYED"] },
                          "servletCount": { "type": "integer" },
                          "filterCount": { "type": "integer" }
                        }
                      },
                      "ResourceInfo": {
                        "type": "object",
                        "properties": {
                          "resources": {
                            "type": "object",
                            "properties": {
                              "heapMemory": {
                                "type": "object",
                                "properties": {
                                  "used": { "type": "integer", "format": "int64" },
                                  "committed": { "type": "integer", "format": "int64" },
                                  "max": { "type": "integer", "format": "int64" }
                                }
                              },
                              "nonHeapMemory": {
                                "type": "object",
                                "properties": {
                                  "used": { "type": "integer", "format": "int64" },
                                  "committed": { "type": "integer", "format": "int64" }
                                }
                              }
                            }
                          }
                        }
                      },
                      "ThreadInfo": {
                        "type": "object",
                        "properties": {
                          "threadCount": { "type": "integer" },
                          "daemonThreadCount": { "type": "integer" },
                          "peakThreadCount": { "type": "integer" },
                          "deadlockedCount": { "type": "integer" },
                          "threads": {
                            "type": "array",
                            "items": {
                              "type": "object",
                              "properties": {
                                "id": { "type": "integer", "format": "int64" },
                                "name": { "type": "string" },
                                "state": { "type": "string" },
                                "daemon": { "type": "boolean" }
                              }
                            }
                          }
                        }
                      },
                      "CommandInfo": {
                        "type": "object",
                        "properties": {
                          "name": { "type": "string" },
                          "description": { "type": "string" },
                          "category": { "type": "string" },
                          "usage": { "type": "string" }
                        }
                      },
                      "CommandResult": {
                        "type": "object",
                        "properties": {
                          "success": { "type": "boolean" },
                          "message": { "type": "string" }
                        }
                      },
                      "ThreadPoolInfo": {
                        "type": "object",
                        "properties": {
                          "name": { "type": "string" },
                          "activeCount": { "type": "integer" },
                          "poolSize": { "type": "integer" },
                          "maxPoolSize": { "type": "integer" }
                        }
                      },
                      "LoggerInfo": {
                        "type": "object",
                        "properties": {
                          "name": { "type": "string" },
                          "level": { "type": "string", "enum": ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"] }
                        }
                      },
                      "UserInfo": {
                        "type": "object",
                        "properties": {
                          "username": { "type": "string" }
                        }
                      },
                      "AuditEvent": {
                        "type": "object",
                        "properties": {
                          "timestamp": { "type": "string", "format": "date-time" },
                          "user": { "type": "string" },
                          "action": { "type": "string" },
                          "target": { "type": "string" },
                          "detail": { "type": "string" },
                          "clientIp": { "type": "string" },
                          "success": { "type": "boolean" }
                        }
                      },
                      "ClusterInfo": {
                        "type": "object",
                        "properties": {
                          "name": { "type": "string" },
                          "memberCount": { "type": "integer" },
                          "status": { "type": "string" }
                        }
                      },
                      "NodeInfo": {
                        "type": "object",
                        "properties": {
                          "nodeId": { "type": "string" },
                          "host": { "type": "string" },
                          "os": { "type": "string" },
                          "arch": { "type": "string" },
                          "cpus": { "type": "integer" },
                          "java": { "type": "string" },
                          "status": { "type": "string" },
                          "servers": {
                            "type": "array",
                            "items": {
                              "type": "object",
                              "properties": {
                                "name": { "type": "string" },
                                "port": { "type": "integer" }
                              }
                            }
                          },
                          "tcpListeners": { "type": "integer" }
                        }
                      }
                    },
                    "securitySchemes": {
                      "sessionAuth": {
                        "type": "apiKey",
                        "in": "cookie",
                        "name": "JSESSIONID",
                        "description": "Session-based authentication via Web Admin login"
                      }
                    }
                  },
                  "security": [
                    { "sessionAuth": [] }
                  ]
                }""".formatted(escapeJson(ctx)));
    }

    private void serveSwaggerUi(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        ServerConfiguration.Server server = configuration.getServer();
        String ctx = server.getWebAdmin().getContextPath();

        PrintWriter out = resp.getWriter();
        out.write(AdminPageLayout.head("API Documentation"));
        out.write(AdminPageLayout.header(server.getName(), server.getNodeId(), ctx));
        out.write(AdminPageLayout.sidebar(ctx, "api-docs"));
        out.write("""
                <div class="main">
                  <div class="breadcrumb"><a href="%s">Dashboard</a><span>/</span>API Documentation</div>
                  <div class="page-header">
                    <div>
                      <h1 class="page-title">API Documentation</h1>
                      <p class="page-subtitle">OpenAPI 3.0 specification for the Velo WAS Admin REST API</p>
                    </div>
                    <div class="btn-group">
                      <a href="%s/api-docs" target="_blank" class="btn">
                        <span style="font-size:14px;">{ }</span> OpenAPI JSON
                      </a>
                    </div>
                  </div>

                  <div class="card" style="margin-bottom:20px;">
                    <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px;">
                      <span style="font-size:24px;font-weight:700;color:var(--primary);">Velo WAS Admin API</span>
                      <span class="badge badge-success">v0.1.0</span>
                      <span class="badge badge-info">OAS 3.0</span>
                    </div>
                    <p style="color:var(--text2);font-size:14px;margin-bottom:16px;">
                      REST API for server management, application deployment, monitoring, diagnostics, security, and configuration.
                    </p>
                    <div style="display:flex;gap:16px;font-size:13px;color:var(--text3);">
                      <span>Base URL: <code style="color:var(--primary);background:var(--primary-bg);padding:2px 6px;border-radius:4px;">%s</code></span>
                      <span>Auth: Session Cookie</span>
                    </div>
                  </div>

                  <div id="apiContent"></div>
                </div>

                <script>
                (function() {
                  var CTX = '%s';
                  fetch(CTX + '/api-docs')
                    .then(function(r) { return r.json(); })
                    .then(function(spec) { renderApiDocs(spec); })
                    .catch(function(e) { document.getElementById('apiContent').innerHTML = '<div class="alert alert-danger">Failed to load API spec: ' + e.message + '</div>'; });

                  function renderApiDocs(spec) {
                    var container = document.getElementById('apiContent');
                    var html = '';

                    // Group paths by tag
                    var tagGroups = {};
                    spec.tags.forEach(function(t) { tagGroups[t.name] = { description: t.description, endpoints: [] }; });

                    Object.keys(spec.paths).forEach(function(path) {
                      var methods = spec.paths[path];
                      Object.keys(methods).forEach(function(method) {
                        var op = methods[method];
                        var tags = op.tags || ['Default'];
                        tags.forEach(function(tag) {
                          if (!tagGroups[tag]) tagGroups[tag] = { description: '', endpoints: [] };
                          tagGroups[tag].endpoints.push({ method: method, path: path, op: op });
                        });
                      });
                    });

                    Object.keys(tagGroups).forEach(function(tag) {
                      var group = tagGroups[tag];
                      if (group.endpoints.length === 0) return;

                      html += '<div class="card" style="margin-bottom:16px;">';
                      html += '<div class="card-header"><span class="card-title">' + escHtml(tag) + '</span>';
                      html += '<span style="color:var(--text3);font-size:12px;">' + escHtml(group.description) + '</span></div>';

                      group.endpoints.forEach(function(ep) {
                        var methodColors = { get: 'var(--success)', post: 'var(--warning)', put: 'var(--info)', delete: 'var(--danger)' };
                        var methodBg = { get: 'var(--success-bg)', post: 'var(--warning-bg)', put: 'var(--info-bg)', delete: 'var(--danger-bg)' };
                        var color = methodColors[ep.method] || 'var(--text)';
                        var bg = methodBg[ep.method] || 'var(--surface2)';

                        html += '<div class="endpoint-item" style="padding:12px 0;border-bottom:1px solid var(--border);cursor:pointer;" onclick="toggleEndpoint(this)">';
                        html += '<div style="display:flex;align-items:center;gap:12px;">';
                        html += '<span style="display:inline-block;width:60px;text-align:center;padding:3px 8px;border-radius:4px;font-size:11px;font-weight:700;text-transform:uppercase;background:' + bg + ';color:' + color + ';">' + ep.method.toUpperCase() + '</span>';
                        html += '<code style="font-size:13px;color:var(--text);font-weight:500;">' + escHtml(ep.path) + '</code>';
                        html += '<span style="color:var(--text2);font-size:13px;margin-left:auto;">' + escHtml(ep.op.summary || '') + '</span>';
                        html += '</div>';

                        // Expandable detail
                        html += '<div class="endpoint-detail" style="display:none;margin-top:12px;padding:12px;background:var(--bg);border-radius:8px;">';
                        if (ep.op.description) {
                          html += '<p style="color:var(--text2);font-size:13px;margin-bottom:12px;">' + escHtml(ep.op.description) + '</p>';
                        }

                        // Parameters
                        if (ep.op.parameters && ep.op.parameters.length > 0) {
                          html += '<div style="margin-bottom:12px;"><strong style="font-size:12px;color:var(--text2);text-transform:uppercase;">Parameters</strong>';
                          html += '<table class="data-table" style="margin-top:8px;"><thead><tr><th>Name</th><th>In</th><th>Type</th><th>Description</th></tr></thead><tbody>';
                          ep.op.parameters.forEach(function(p) {
                            html += '<tr><td><code>' + escHtml(p.name) + '</code></td>';
                            html += '<td>' + escHtml(p.in) + '</td>';
                            html += '<td>' + (p.schema ? escHtml(p.schema.type || '') : '') + '</td>';
                            html += '<td style="color:var(--text2);">' + escHtml(p.description || '') + '</td></tr>';
                          });
                          html += '</tbody></table></div>';
                        }

                        // Request body
                        if (ep.op.requestBody) {
                          html += '<div style="margin-bottom:12px;"><strong style="font-size:12px;color:var(--text2);text-transform:uppercase;">Request Body</strong>';
                          var content = ep.op.requestBody.content;
                          if (content && content['application/json'] && content['application/json'].schema) {
                            var schema = content['application/json'].schema;
                            if (schema.properties) {
                              html += '<table class="data-table" style="margin-top:8px;"><thead><tr><th>Field</th><th>Type</th><th>Required</th><th>Description</th></tr></thead><tbody>';
                              var required = schema.required || [];
                              Object.keys(schema.properties).forEach(function(prop) {
                                var p = schema.properties[prop];
                                html += '<tr><td><code>' + escHtml(prop) + '</code></td>';
                                html += '<td>' + escHtml(p.type || '') + '</td>';
                                html += '<td>' + (required.indexOf(prop) >= 0 ? '<span style="color:var(--danger);">Yes</span>' : 'No') + '</td>';
                                html += '<td style="color:var(--text2);">' + escHtml(p.description || '') + (p.example ? ' <code style="color:var(--text3);">e.g. ' + escHtml(String(p.example)) + '</code>' : '') + '</td></tr>';
                              });
                              html += '</tbody></table>';
                            }
                          }
                          html += '</div>';
                        }

                        // Responses
                        html += '<div><strong style="font-size:12px;color:var(--text2);text-transform:uppercase;">Responses</strong>';
                        Object.keys(ep.op.responses).forEach(function(code) {
                          var r = ep.op.responses[code];
                          var codeColor = code.startsWith('2') ? 'var(--success)' : code.startsWith('4') ? 'var(--warning)' : 'var(--danger)';
                          html += '<div style="margin-top:8px;display:flex;align-items:center;gap:8px;">';
                          html += '<span style="color:' + codeColor + ';font-weight:600;font-size:13px;">' + code + '</span>';
                          html += '<span style="color:var(--text2);font-size:13px;">' + escHtml(r.description || '') + '</span>';
                          html += '</div>';
                        });
                        html += '</div>';

                        // Try it button
                        if (ep.method === 'get') {
                          html += '<div style="margin-top:12px;"><button class="btn btn-sm btn-primary" onclick="event.stopPropagation();tryEndpoint(\\'' + escHtml(ep.path) + '\\')">Try It</button>';
                          html += '<pre class="try-result" style="display:none;margin-top:8px;background:var(--surface2);padding:12px;border-radius:6px;font-size:12px;overflow-x:auto;max-height:300px;color:var(--text);white-space:pre-wrap;"></pre></div>';
                        }

                        html += '</div>'; // endpoint-detail
                        html += '</div>'; // endpoint-item
                      });

                      html += '</div>';
                    });

                    // Schema section
                    if (spec.components && spec.components.schemas) {
                      html += '<div class="card" style="margin-bottom:16px;">';
                      html += '<div class="card-header"><span class="card-title">Schemas</span></div>';
                      Object.keys(spec.components.schemas).forEach(function(name) {
                        var schema = spec.components.schemas[name];
                        html += '<div style="padding:10px 0;border-bottom:1px solid var(--border);cursor:pointer;" onclick="var d=this.querySelector(\\'.schema-detail\\');d.style.display=d.style.display===\\'none\\'?\\'block\\':\\'none\\';">';
                        html += '<code style="font-size:13px;font-weight:600;color:var(--primary);">' + escHtml(name) + '</code>';
                        html += '<span style="color:var(--text3);font-size:12px;margin-left:8px;">' + escHtml(schema.type || 'object') + '</span>';
                        html += '<div class="schema-detail" style="display:none;margin-top:8px;">';
                        if (schema.properties) {
                          html += '<table class="data-table"><thead><tr><th>Property</th><th>Type</th><th>Format</th></tr></thead><tbody>';
                          Object.keys(schema.properties).forEach(function(prop) {
                            var p = schema.properties[prop];
                            var type = p.type || (p['$ref'] ? p['$ref'].split('/').pop() : 'object');
                            if (p.items) type += '[' + (p.items.type || (p.items['$ref'] ? p.items['$ref'].split('/').pop() : '')) + ']';
                            html += '<tr><td><code>' + escHtml(prop) + '</code></td><td>' + escHtml(type) + '</td><td style="color:var(--text3);">' + escHtml(p.format || '') + '</td></tr>';
                          });
                          html += '</tbody></table>';
                        }
                        html += '</div></div>';
                      });
                      html += '</div>';
                    }

                    container.innerHTML = html;
                  }

                  window.toggleEndpoint = function(el) {
                    var detail = el.querySelector('.endpoint-detail');
                    if (detail) detail.style.display = detail.style.display === 'none' ? 'block' : 'none';
                  };

                  window.tryEndpoint = function(path) {
                    var btn = event.target;
                    var pre = btn.parentNode.querySelector('.try-result');
                    pre.style.display = 'block';
                    pre.textContent = 'Loading...';
                    fetch(CTX + path)
                      .then(function(r) { return r.text(); })
                      .then(function(text) {
                        try { pre.textContent = JSON.stringify(JSON.parse(text), null, 2); }
                        catch(e) { pre.textContent = text; }
                      })
                      .catch(function(e) { pre.textContent = 'Error: ' + e.message; });
                  };

                  function escHtml(s) {
                    if (!s) return '';
                    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
                  }
                })();
                </script>
                """.formatted(ctx, ctx, ctx, ctx));
        out.write(AdminPageLayout.footer());
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
