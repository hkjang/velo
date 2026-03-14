package io.velo.was.jsp.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Parsed JSP document represented as a list of nodes.
 */
public class JspDocument {

    private final String sourcePath;
    private final List<JspNode> nodes;
    private final List<PageDirective> pageDirectives;

    public JspDocument(String sourcePath, List<JspNode> nodes) {
        this.sourcePath = sourcePath;
        this.nodes = List.copyOf(nodes);
        List<PageDirective> directives = new ArrayList<>();
        for (JspNode node : this.nodes) {
            if (node instanceof PageDirective pd) {
                directives.add(pd);
            }
        }
        this.pageDirectives = List.copyOf(directives);
    }

    public String sourcePath() { return sourcePath; }
    public List<JspNode> nodes() { return nodes; }
    public List<PageDirective> pageDirectives() { return pageDirectives; }

    public String pageEncoding() {
        for (PageDirective pd : pageDirectives) {
            String enc = pd.attributes().get("pageEncoding");
            if (enc != null) return enc;
            String contentType = pd.attributes().get("contentType");
            if (contentType != null && contentType.contains("charset=")) {
                return contentType.substring(contentType.indexOf("charset=") + 8).trim();
            }
        }
        return "UTF-8";
    }

    public String contentType() {
        for (PageDirective pd : pageDirectives) {
            String ct = pd.attributes().get("contentType");
            if (ct != null) return ct;
        }
        return "text/html; charset=UTF-8";
    }

    public boolean isErrorPage() {
        for (PageDirective pd : pageDirectives) {
            if ("true".equalsIgnoreCase(pd.attributes().get("isErrorPage"))) return true;
        }
        return false;
    }

    public String errorPage() {
        for (PageDirective pd : pageDirectives) {
            String ep = pd.attributes().get("errorPage");
            if (ep != null) return ep;
        }
        return null;
    }

    public boolean sessionEnabled() {
        for (PageDirective pd : pageDirectives) {
            String s = pd.attributes().get("session");
            if (s != null) return !"false".equalsIgnoreCase(s);
        }
        return true;
    }

    public int bufferSize() {
        for (PageDirective pd : pageDirectives) {
            String b = pd.attributes().get("buffer");
            if (b != null) {
                if ("none".equalsIgnoreCase(b)) return 0;
                String num = b.replaceAll("[^0-9]", "");
                try { return Integer.parseInt(num) * 1024; } catch (NumberFormatException e) { /* default */ }
            }
        }
        return 8192;
    }

    public boolean autoFlush() {
        for (PageDirective pd : pageDirectives) {
            String af = pd.attributes().get("autoFlush");
            if (af != null) return !"false".equalsIgnoreCase(af);
        }
        return true;
    }

    // --- Node types ---

    public sealed interface JspNode permits TemplateText, Scriptlet, Expression, Declaration,
            PageDirective, IncludeDirective, TaglibDirective, ActionTag, ElExpression {}

    public record TemplateText(String text) implements JspNode {}
    public record Scriptlet(String code) implements JspNode {}
    public record Expression(String expression) implements JspNode {}
    public record Declaration(String code) implements JspNode {}
    public record PageDirective(Map<String, String> attributes) implements JspNode {}
    public record IncludeDirective(String file) implements JspNode {}
    public record TaglibDirective(String uri, String prefix) implements JspNode {}
    public record ActionTag(String action, Map<String, String> attributes, String body) implements JspNode {}
    public record ElExpression(String expression) implements JspNode {}
}
