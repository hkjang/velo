package io.velo.was.jsp.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses JSP source text into a {@link JspDocument}.
 * <p>
 * Handles:
 * <ul>
 *     <li>{@code <%@ page ... %>} - page directive</li>
 *     <li>{@code <%@ include ... %>} - include directive</li>
 *     <li>{@code <%@ taglib ... %>} - taglib directive</li>
 *     <li>{@code <%! ... %>} - declaration</li>
 *     <li>{@code <%= ... %>} - expression</li>
 *     <li>{@code <% ... %>} - scriptlet</li>
 *     <li>{@code ${...}} - EL expression</li>
 *     <li>{@code <jsp:include .../>}, {@code <jsp:forward .../>}, etc. - action tags</li>
 *     <li>Plain template text</li>
 * </ul>
 */
public class JspParser {

    private static final Pattern DIRECTIVE_PATTERN = Pattern.compile(
            "<%@\\s*(page|include|taglib)\\s+(.*?)%>", Pattern.DOTALL);
    private static final Pattern DECLARATION_PATTERN = Pattern.compile(
            "<%!\\s*(.*?)%>", Pattern.DOTALL);
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile(
            "<%=\\s*(.*?)%>", Pattern.DOTALL);
    private static final Pattern SCRIPTLET_PATTERN = Pattern.compile(
            "<%(?![!=@])\\s*(.*?)%>", Pattern.DOTALL);
    private static final Pattern EL_PATTERN = Pattern.compile(
            "\\$\\{(.*?)\\}");
    private static final Pattern ACTION_SELF_CLOSE_PATTERN = Pattern.compile(
            "<jsp:(include|forward|param|useBean|getProperty|setProperty|plugin)\\s*(.*?)/>", Pattern.DOTALL);
    private static final Pattern ACTION_WITH_BODY_PATTERN = Pattern.compile(
            "<jsp:(include|forward|useBean)\\s*(.*?)>(.*?)</jsp:\\1>", Pattern.DOTALL);
    private static final Pattern ATTR_PATTERN = Pattern.compile(
            "(\\w+)\\s*=\\s*\"([^\"]*)\"");

    private static final Pattern JSP_TAG_PATTERN = Pattern.compile(
            "<%@\\s*(?:page|include|taglib)\\s+.*?%>" +
            "|<%!\\s*.*?%>" +
            "|<%=\\s*.*?%>" +
            "|<%(?![!=@])\\s*.*?%>" +
            "|\\$\\{.*?\\}" +
            "|<jsp:(?:include|forward|param|useBean|getProperty|setProperty|plugin)\\s+.*?/>" +
            "|<jsp:(?:include|forward|useBean)\\s+.*?>.*?</jsp:(?:include|forward|useBean)>",
            Pattern.DOTALL
    );

    public JspDocument parse(String source, String sourcePath) {
        List<JspDocument.JspNode> nodes = new ArrayList<>();
        Matcher matcher = JSP_TAG_PATTERN.matcher(source);

        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String text = source.substring(lastEnd, matcher.start());
                addTextWithEL(nodes, text);
            }
            String match = matcher.group();
            JspDocument.JspNode node = parseTag(match);
            if (node != null) {
                nodes.add(node);
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < source.length()) {
            String text = source.substring(lastEnd);
            addTextWithEL(nodes, text);
        }

        return new JspDocument(sourcePath, nodes);
    }

    private void addTextWithEL(List<JspDocument.JspNode> nodes, String text) {
        Matcher elMatcher = EL_PATTERN.matcher(text);
        int lastEnd = 0;
        while (elMatcher.find()) {
            if (elMatcher.start() > lastEnd) {
                nodes.add(new JspDocument.TemplateText(text.substring(lastEnd, elMatcher.start())));
            }
            nodes.add(new JspDocument.ElExpression(elMatcher.group(1).trim()));
            lastEnd = elMatcher.end();
        }
        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd);
            if (!remaining.isEmpty()) {
                nodes.add(new JspDocument.TemplateText(remaining));
            }
        }
    }

    private JspDocument.JspNode parseTag(String tag) {
        Matcher m;

        // EL expression: ${...}
        m = EL_PATTERN.matcher(tag);
        if (m.matches()) {
            return new JspDocument.ElExpression(m.group(1).trim());
        }

        m = DIRECTIVE_PATTERN.matcher(tag);
        if (m.matches()) {
            return parseDirective(m.group(1).trim(), m.group(2).trim());
        }

        m = DECLARATION_PATTERN.matcher(tag);
        if (m.matches()) {
            return new JspDocument.Declaration(m.group(1).trim());
        }

        m = EXPRESSION_PATTERN.matcher(tag);
        if (m.matches()) {
            return new JspDocument.Expression(m.group(1).trim());
        }

        m = SCRIPTLET_PATTERN.matcher(tag);
        if (m.matches()) {
            return new JspDocument.Scriptlet(m.group(1).trim());
        }

        m = ACTION_WITH_BODY_PATTERN.matcher(tag);
        if (m.matches()) {
            return new JspDocument.ActionTag(m.group(1), parseAttributes(m.group(2)), m.group(3));
        }

        m = ACTION_SELF_CLOSE_PATTERN.matcher(tag);
        if (m.matches()) {
            return new JspDocument.ActionTag(m.group(1), parseAttributes(m.group(2)), null);
        }

        return null;
    }

    private JspDocument.JspNode parseDirective(String type, String attrString) {
        Map<String, String> attrs = parseAttributes(attrString);
        return switch (type) {
            case "page" -> new JspDocument.PageDirective(attrs);
            case "include" -> new JspDocument.IncludeDirective(attrs.getOrDefault("file", ""));
            case "taglib" -> new JspDocument.TaglibDirective(
                    attrs.getOrDefault("uri", ""), attrs.getOrDefault("prefix", ""));
            default -> null;
        };
    }

    private Map<String, String> parseAttributes(String attrString) {
        Map<String, String> attrs = new LinkedHashMap<>();
        Matcher m = ATTR_PATTERN.matcher(attrString);
        while (m.find()) {
            attrs.put(m.group(1), m.group(2));
        }
        return Map.copyOf(attrs);
    }
}
