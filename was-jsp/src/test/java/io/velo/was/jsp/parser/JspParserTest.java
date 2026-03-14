package io.velo.was.jsp.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JspParserTest {

    private final JspParser parser = new JspParser();

    @Test
    void parseTemplateText() {
        JspDocument doc = parser.parse("<html><body>Hello</body></html>", "test.jsp");
        assertEquals(1, doc.nodes().size());
        assertInstanceOf(JspDocument.TemplateText.class, doc.nodes().get(0));
        assertEquals("<html><body>Hello</body></html>",
                ((JspDocument.TemplateText) doc.nodes().get(0)).text());
    }

    @Test
    void parsePageDirective() {
        JspDocument doc = parser.parse(
                "<%@ page contentType=\"text/html\" pageEncoding=\"UTF-8\" %>", "test.jsp");
        assertEquals(1, doc.nodes().size());
        assertInstanceOf(JspDocument.PageDirective.class, doc.nodes().get(0));
        JspDocument.PageDirective pd = (JspDocument.PageDirective) doc.nodes().get(0);
        assertEquals("text/html", pd.attributes().get("contentType"));
        assertEquals("UTF-8", pd.attributes().get("pageEncoding"));
    }

    @Test
    void parseIncludeDirective() {
        JspDocument doc = parser.parse("<%@ include file=\"header.jsp\" %>", "test.jsp");
        assertEquals(1, doc.nodes().size());
        assertInstanceOf(JspDocument.IncludeDirective.class, doc.nodes().get(0));
        assertEquals("header.jsp", ((JspDocument.IncludeDirective) doc.nodes().get(0)).file());
    }

    @Test
    void parseTaglibDirective() {
        JspDocument doc = parser.parse(
                "<%@ taglib uri=\"http://java.sun.com/jsp/jstl/core\" prefix=\"c\" %>", "test.jsp");
        assertEquals(1, doc.nodes().size());
        assertInstanceOf(JspDocument.TaglibDirective.class, doc.nodes().get(0));
        JspDocument.TaglibDirective tld = (JspDocument.TaglibDirective) doc.nodes().get(0);
        assertEquals("http://java.sun.com/jsp/jstl/core", tld.uri());
        assertEquals("c", tld.prefix());
    }

    @Test
    void parseExpression() {
        JspDocument doc = parser.parse("<%= request.getParameter(\"name\") %>", "test.jsp");
        assertEquals(1, doc.nodes().size());
        assertInstanceOf(JspDocument.Expression.class, doc.nodes().get(0));
        assertEquals("request.getParameter(\"name\")",
                ((JspDocument.Expression) doc.nodes().get(0)).expression());
    }

    @Test
    void parseScriptlet() {
        JspDocument doc = parser.parse("<% String name = \"World\"; %>", "test.jsp");
        assertEquals(1, doc.nodes().size());
        assertInstanceOf(JspDocument.Scriptlet.class, doc.nodes().get(0));
        assertEquals("String name = \"World\";",
                ((JspDocument.Scriptlet) doc.nodes().get(0)).code());
    }

    @Test
    void parseDeclaration() {
        JspDocument doc = parser.parse("<%! private int count = 0; %>", "test.jsp");
        assertEquals(1, doc.nodes().size());
        assertInstanceOf(JspDocument.Declaration.class, doc.nodes().get(0));
        assertEquals("private int count = 0;",
                ((JspDocument.Declaration) doc.nodes().get(0)).code());
    }

    @Test
    void parseElExpression() {
        JspDocument doc = parser.parse("Hello ${user.name}!", "test.jsp");
        assertEquals(3, doc.nodes().size());
        assertInstanceOf(JspDocument.TemplateText.class, doc.nodes().get(0));
        assertInstanceOf(JspDocument.ElExpression.class, doc.nodes().get(1));
        assertInstanceOf(JspDocument.TemplateText.class, doc.nodes().get(2));
        assertEquals("user.name", ((JspDocument.ElExpression) doc.nodes().get(1)).expression());
    }

    @Test
    void parseActionTag() {
        JspDocument doc = parser.parse("<jsp:include page=\"/footer.jsp\"/>", "test.jsp");
        assertEquals(1, doc.nodes().size());
        assertInstanceOf(JspDocument.ActionTag.class, doc.nodes().get(0));
        JspDocument.ActionTag action = (JspDocument.ActionTag) doc.nodes().get(0);
        assertEquals("include", action.action());
        assertEquals("/footer.jsp", action.attributes().get("page"));
    }

    @Test
    void parseMixedContent() {
        String jsp = """
                <%@ page contentType="text/html" %>
                <html>
                <body>
                <% String name = "World"; %>
                Hello <%= name %>!
                ${user.email}
                <jsp:include page="/footer.jsp"/>
                </body>
                </html>""";
        JspDocument doc = parser.parse(jsp, "mixed.jsp");
        assertTrue(doc.nodes().size() >= 7);
        assertEquals("text/html", doc.contentType());
    }

    @Test
    void parseErrorPageDirective() {
        JspDocument doc = parser.parse(
                "<%@ page errorPage=\"/error.jsp\" %>", "test.jsp");
        assertEquals("/error.jsp", doc.errorPage());
        assertFalse(doc.isErrorPage());
    }

    @Test
    void parseIsErrorPage() {
        JspDocument doc = parser.parse(
                "<%@ page isErrorPage=\"true\" %>", "test.jsp");
        assertTrue(doc.isErrorPage());
    }

    @Test
    void parseSessionDisabled() {
        JspDocument doc = parser.parse(
                "<%@ page session=\"false\" %>", "test.jsp");
        assertFalse(doc.sessionEnabled());
    }

    @Test
    void parseBufferNone() {
        JspDocument doc = parser.parse(
                "<%@ page buffer=\"none\" %>", "test.jsp");
        assertEquals(0, doc.bufferSize());
    }
}
