package io.velo.was.servlet;

import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

        String body = responseContext.toNettyResponse(false, false, null)
                .content()
                .toString(CharsetUtil.UTF_8);

        assertTrue(body.contains("__veloRapBrowserCompatPatched"));
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
}
