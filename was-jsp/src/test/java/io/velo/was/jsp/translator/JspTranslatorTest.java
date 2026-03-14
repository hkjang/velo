package io.velo.was.jsp.translator;

import io.velo.was.jsp.parser.JspDocument;
import io.velo.was.jsp.parser.JspParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JspTranslatorTest {

    private final JspParser parser = new JspParser();
    private final JspTranslator translator = new JspTranslator();

    @Test
    void translateSimpleTemplate() {
        JspDocument doc = parser.parse("<html>Hello</html>", "hello.jsp");
        TranslatedSource result = translator.translate(doc);

        assertEquals("io.velo.was.jsp.generated", result.packageName());
        assertTrue(result.className().startsWith("_jsp_"));
        assertTrue(result.javaSource().contains("extends HttpServlet"));
        assertTrue(result.javaSource().contains("out.write("));
    }

    @Test
    void translateExpression() {
        JspDocument doc = parser.parse("<%= 1 + 1 %>", "expr.jsp");
        TranslatedSource result = translator.translate(doc);

        assertTrue(result.javaSource().contains("out.print(1 + 1)"));
    }

    @Test
    void translateScriptlet() {
        JspDocument doc = parser.parse("<% int x = 42; %>", "script.jsp");
        TranslatedSource result = translator.translate(doc);

        assertTrue(result.javaSource().contains("int x = 42;"));
    }

    @Test
    void translateDeclaration() {
        JspDocument doc = parser.parse("<%! private int count = 0; %>", "decl.jsp");
        TranslatedSource result = translator.translate(doc);

        assertTrue(result.javaSource().contains("private int count = 0;"));
        // Declaration should be at class level (before service method)
        int declIdx = result.javaSource().indexOf("private int count = 0;");
        int serviceIdx = result.javaSource().indexOf("protected void service");
        assertTrue(declIdx < serviceIdx);
    }

    @Test
    void translateElExpression() {
        JspDocument doc = parser.parse("${user.name}", "el.jsp");
        TranslatedSource result = translator.translate(doc);

        assertTrue(result.javaSource().contains("SimpleElEvaluator.evaluate"));
        assertTrue(result.javaSource().contains("user.name"));
    }

    @Test
    void translateSessionEnabled() {
        JspDocument doc = parser.parse(
                "<%@ page session=\"true\" %><%= session.getId() %>", "session.jsp");
        TranslatedSource result = translator.translate(doc);

        assertTrue(result.javaSource().contains("HttpSession session = request.getSession(true)"));
    }

    @Test
    void translateSessionDisabled() {
        JspDocument doc = parser.parse(
                "<%@ page session=\"false\" %>Hello", "no-session.jsp");
        TranslatedSource result = translator.translate(doc);

        assertFalse(result.javaSource().contains("getSession"));
    }

    @Test
    void translateErrorPage() {
        JspDocument doc = parser.parse(
                "<%@ page errorPage=\"/error.jsp\" %>Hello", "page.jsp");
        TranslatedSource result = translator.translate(doc);

        assertTrue(result.javaSource().contains("error.jsp"));
        assertTrue(result.javaSource().contains("forward(request, response)"));
    }

    @Test
    void translateIsErrorPage() {
        JspDocument doc = parser.parse(
                "<%@ page isErrorPage=\"true\" %><%= exception.getMessage() %>", "error.jsp");
        TranslatedSource result = translator.translate(doc);

        assertTrue(result.javaSource().contains("Throwable exception"));
    }

    @Test
    void translateJspInclude() {
        JspDocument doc = parser.parse(
                "<jsp:include page=\"/header.jsp\"/>", "main.jsp");
        TranslatedSource result = translator.translate(doc);

        assertTrue(result.javaSource().contains("getRequestDispatcher"));
        assertTrue(result.javaSource().contains("include(request, response)"));
    }

    @Test
    void translateJspForward() {
        JspDocument doc = parser.parse(
                "<jsp:forward page=\"/target.jsp\"/>", "fwd.jsp");
        TranslatedSource result = translator.translate(doc);

        assertTrue(result.javaSource().contains("forward(request, response)"));
        assertTrue(result.javaSource().contains("return;"));
    }

    @Test
    void translateFullyQualifiedClassName() {
        JspDocument doc = parser.parse("Hello", "hello.jsp");
        TranslatedSource result = translator.translate(doc);

        assertEquals("io.velo.was.jsp.generated._jsp_hello",
                result.fullyQualifiedClassName());
    }

    @Test
    void deriveClassNameFromPath() {
        assertEquals("_jsp_hello", JspTranslator.deriveClassName("hello.jsp"));
        assertEquals("_jsp_my_page", JspTranslator.deriveClassName("my-page.jsp"));
        assertEquals("_jsp_index", JspTranslator.deriveClassName("/views/index.jsp"));
        assertEquals("_jsp_unnamed", JspTranslator.deriveClassName(null));
    }
}
