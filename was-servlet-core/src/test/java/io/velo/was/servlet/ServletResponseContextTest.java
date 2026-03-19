package io.velo.was.servlet;

import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServletResponseContextTest {

    @Test
    void injectsRapBrowserCompatibilityPatchIntoBootstrapHtml() {
        ServletResponseContext responseContext = new ServletResponseContext();
        responseContext.setContentType("text/html; charset=UTF-8");
        responseContext.writer().write("""
                <!DOCTYPE html>
                <html>
                  <body>
                    <script type="text/javascript" src="rwt-resources/440/rap-client.js"></script>
                    <script type="text/javascript">
                      rwt.remote.MessageProcessor.processMessage({});
                    </script>
                  </body>
                </html>
                """);

        String body = responseContext.toNettyResponse(false, null)
                .content()
                .toString(CharsetUtil.UTF_8);

        assertTrue(body.contains("__veloRapBrowserCompatPatched"));
        assertTrue(body.contains("__veloLastSourceSyncAt"));
        assertTrue(body.contains("evaluationSucceeded"));
        assertTrue(body.indexOf("__veloRapBrowserCompatPatched") > body.indexOf("rap-client.js"));
        assertTrue(body.indexOf("__veloRapBrowserCompatPatched")
                < body.indexOf("rwt.remote.MessageProcessor.processMessage"));
    }

    @Test
    void leavesPlainHtmlUntouched() {
        String body = ServletResponseContext.injectRapBrowserCompatibilityPatch("""
                <!DOCTYPE html>
                <html><body><h1>Hello</h1></body></html>
                """);

        assertFalse(body.contains("__veloRapBrowserCompatPatched"));
    }

    @Test
    void doesNotInjectCompatibilityPatchTwice() {
        String onceInjected = ServletResponseContext.injectRapBrowserCompatibilityPatch("""
                <!DOCTYPE html>
                <html>
                  <body>
                    <script type="text/javascript" src="rwt-resources/440/rap-client.js"></script>
                    <script type="text/javascript">
                      rwt.remote.MessageProcessor.processMessage({});
                    </script>
                  </body>
                </html>
                """);

        String twiceInjected = ServletResponseContext.injectRapBrowserCompatibilityPatch(onceInjected);

        assertTrue(onceInjected.contains("__veloRapBrowserCompatPatched"));
        assertTrue(twiceInjected.contains("__veloRapBrowserCompatPatched"));
        assertTrue(onceInjected.equals(twiceInjected));
    }

    @Test
    void copiesNonceFromRapClientScriptToCompatibilityPatch() {
        String body = ServletResponseContext.injectRapBrowserCompatibilityPatch("""
                <!DOCTYPE html>
                <html>
                  <body>
                    <script type="text/javascript" nonce="abc123" src="rwt-resources/440/rap-client.js"></script>
                    <script type="text/javascript">
                      rwt.remote.MessageProcessor.processMessage({});
                    </script>
                  </body>
                </html>
                """);

        assertTrue(body.contains("nonce=\"abc123\""));
        assertTrue(body.contains("<script type=\"text/javascript\" nonce=\"abc123\">"));
    }

    @Test
    void resetBufferPreservesHeadersStatusAndCookies() {
        ServletResponseContext responseContext = new ServletResponseContext();
        responseContext.setStatus(202);
        responseContext.setHeader("X-Test", "yes");
        responseContext.addCookie(new jakarta.servlet.http.Cookie("sample", "cookie"));
        responseContext.writer().write("before");

        responseContext.resetBuffer();
        responseContext.writer().write("after");

        var response = responseContext.toNettyResponse(false, null);
        assertEquals(202, response.status().code());
        assertEquals("yes", response.headers().get("X-Test"));
        assertEquals("after", response.content().toString(CharsetUtil.UTF_8));
        assertTrue(response.headers().getAll("Set-Cookie").stream().anyMatch(value -> value.startsWith("sample=cookie")));
    }

    @Test
    void clearErrorStateKeepsStatusButDisablesErrorDispatchFlag() {
        ServletResponseContext responseContext = new ServletResponseContext();
        responseContext.sendError(404, "missing");

        responseContext.clearErrorState();

        assertEquals(404, responseContext.status());
        assertFalse(responseContext.errorSent());
        assertNull(responseContext.errorMessage());
    }
}
