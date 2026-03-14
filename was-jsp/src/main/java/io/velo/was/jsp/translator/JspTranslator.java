package io.velo.was.jsp.translator;

import io.velo.was.jsp.parser.JspDocument;
import io.velo.was.jsp.parser.JspDocument.*;

import java.util.Map;

/**
 * Translates a {@link JspDocument} into a Java Servlet source code string.
 * <p>
 * The generated class extends {@code jakarta.servlet.http.HttpServlet} and
 * implements the {@code _jspService} pattern by overriding {@code service()}.
 */
public class JspTranslator {

    private static final String PACKAGE_PREFIX = "io.velo.was.jsp.generated";

    public TranslatedSource translate(JspDocument document) {
        String className = deriveClassName(document.sourcePath());
        StringBuilder sb = new StringBuilder();

        sb.append("package ").append(PACKAGE_PREFIX).append(";\n\n");
        sb.append("import jakarta.servlet.http.HttpServlet;\n");
        sb.append("import jakarta.servlet.http.HttpServletRequest;\n");
        sb.append("import jakarta.servlet.http.HttpServletResponse;\n");
        sb.append("import jakarta.servlet.http.HttpSession;\n");
        sb.append("import jakarta.servlet.ServletContext;\n");
        sb.append("import jakarta.servlet.ServletConfig;\n");
        sb.append("import jakarta.servlet.ServletException;\n");
        sb.append("import java.io.IOException;\n");
        sb.append("import java.io.PrintWriter;\n\n");

        // Collect declarations
        StringBuilder declarations = new StringBuilder();
        for (JspNode node : document.nodes()) {
            if (node instanceof Declaration decl) {
                declarations.append("    ").append(decl.code()).append("\n");
            }
        }

        sb.append("public class ").append(className).append(" extends HttpServlet {\n\n");

        if (!declarations.isEmpty()) {
            sb.append("    // JSP declarations\n");
            sb.append(declarations);
            sb.append("\n");
        }

        sb.append("    @Override\n");
        sb.append("    protected void service(HttpServletRequest request, HttpServletResponse response)\n");
        sb.append("            throws ServletException, IOException {\n");

        // Set content type
        sb.append("        response.setContentType(\"").append(escapeJava(document.contentType())).append("\");\n");

        // Implicit variables
        sb.append("        PrintWriter out = response.getWriter();\n");
        sb.append("        ServletContext application = getServletContext();\n");
        sb.append("        ServletConfig config = getServletConfig();\n");
        sb.append("        Object page = this;\n");

        if (document.sessionEnabled()) {
            sb.append("        HttpSession session = request.getSession(true);\n");
        }

        // Error page variables
        if (document.isErrorPage()) {
            sb.append("        Throwable exception = (Throwable) request.getAttribute(\"jakarta.servlet.error.exception\");\n");
        }

        sb.append("\n        try {\n");

        // Body
        for (JspNode node : document.nodes()) {
            switch (node) {
                case TemplateText tt -> {
                    String text = tt.text();
                    if (!text.isEmpty()) {
                        sb.append("            out.write(\"").append(escapeJava(text)).append("\");\n");
                    }
                }
                case Expression expr -> {
                    sb.append("            out.print(").append(expr.expression()).append(");\n");
                }
                case Scriptlet scr -> {
                    sb.append("            ").append(scr.code()).append("\n");
                }
                case ElExpression el -> {
                    sb.append("            out.print(io.velo.was.jsp.runtime.SimpleElEvaluator.evaluate(\"")
                      .append(escapeJava(el.expression()))
                      .append("\", request, session, application));\n");
                }
                case ActionTag action -> {
                    generateActionTag(sb, action);
                }
                case Declaration ignored -> { /* already handled above */ }
                case PageDirective ignored2 -> { /* metadata only */ }
                case IncludeDirective inc -> {
                    sb.append("            request.getRequestDispatcher(\"")
                      .append(escapeJava(inc.file()))
                      .append("\").include(request, response);\n");
                }
                case TaglibDirective ignored3 -> { /* metadata only */ }
            }
        }

        // Error page handling
        String errorPage = document.errorPage();
        sb.append("        } catch (Exception _jspException) {\n");
        if (errorPage != null) {
            sb.append("            request.setAttribute(\"jakarta.servlet.error.exception\", _jspException);\n");
            sb.append("            request.getRequestDispatcher(\"")
              .append(escapeJava(errorPage))
              .append("\").forward(request, response);\n");
        } else {
            sb.append("            if (_jspException instanceof ServletException se) throw se;\n");
            sb.append("            if (_jspException instanceof IOException ioe) throw ioe;\n");
            sb.append("            throw new ServletException(_jspException);\n");
        }
        sb.append("        }\n");

        sb.append("    }\n");
        sb.append("}\n");

        return new TranslatedSource(PACKAGE_PREFIX, className, sb.toString(), document.sourcePath());
    }

    private void generateActionTag(StringBuilder sb, ActionTag action) {
        switch (action.action()) {
            case "include" -> {
                String page = action.attributes().getOrDefault("page", "");
                sb.append("            request.getRequestDispatcher(\"")
                  .append(escapeJava(page))
                  .append("\").include(request, response);\n");
            }
            case "forward" -> {
                String page = action.attributes().getOrDefault("page", "");
                sb.append("            request.getRequestDispatcher(\"")
                  .append(escapeJava(page))
                  .append("\").forward(request, response);\n");
                sb.append("            return;\n");
            }
            case "useBean" -> {
                String id = action.attributes().getOrDefault("id", "bean");
                String className = action.attributes().getOrDefault("class", "java.lang.Object");
                String scope = action.attributes().getOrDefault("scope", "page");
                String attrScope = switch (scope) {
                    case "request" -> "request";
                    case "session" -> "session";
                    case "application" -> "application";
                    default -> "request";
                };
                sb.append("            ").append(className).append(" ").append(id)
                  .append(" = (").append(className).append(") ");
                if ("session".equals(attrScope)) {
                    sb.append("session.getAttribute(\"").append(id).append("\");\n");
                    sb.append("            if (").append(id).append(" == null) { ")
                      .append(id).append(" = new ").append(className).append("(); ")
                      .append("session.setAttribute(\"").append(id).append("\", ").append(id).append("); }\n");
                } else if ("application".equals(attrScope)) {
                    sb.append("application.getAttribute(\"").append(id).append("\");\n");
                    sb.append("            if (").append(id).append(" == null) { ")
                      .append(id).append(" = new ").append(className).append("(); ")
                      .append("application.setAttribute(\"").append(id).append("\", ").append(id).append("); }\n");
                } else {
                    sb.append("request.getAttribute(\"").append(id).append("\");\n");
                    sb.append("            if (").append(id).append(" == null) { ")
                      .append(id).append(" = new ").append(className).append("(); ")
                      .append("request.setAttribute(\"").append(id).append("\", ").append(id).append("); }\n");
                }
            }
            case "getProperty" -> {
                String name = action.attributes().getOrDefault("name", "bean");
                String property = action.attributes().getOrDefault("property", "");
                String getter = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
                sb.append("            out.print(").append(name).append(".").append(getter).append("());\n");
            }
            case "setProperty" -> {
                String name = action.attributes().getOrDefault("name", "bean");
                String property = action.attributes().getOrDefault("property", "");
                String value = action.attributes().getOrDefault("value", "null");
                String setter = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
                sb.append("            ").append(name).append(".").append(setter)
                  .append("(").append(value).append(");\n");
            }
            default -> {
                sb.append("            // unsupported action: jsp:").append(action.action()).append("\n");
            }
        }
    }

    static String deriveClassName(String jspPath) {
        if (jspPath == null || jspPath.isBlank()) {
            return "_jsp_unnamed";
        }
        String name = jspPath.replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) name = name.substring(lastSlash + 1);
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex >= 0) name = name.substring(0, dotIndex);
        name = name.replaceAll("[^a-zA-Z0-9_]", "_");
        if (name.isEmpty() || Character.isDigit(name.charAt(0))) {
            name = "_" + name;
        }
        return "_jsp_" + name;
    }

    static String escapeJava(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
