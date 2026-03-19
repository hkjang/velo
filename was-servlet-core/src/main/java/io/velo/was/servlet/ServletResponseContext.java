package io.velo.was.servlet;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import jakarta.servlet.http.Cookie;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class ServletResponseContext {

    private static final String RAP_CLIENT_SCRIPT = "rap-client.js";
    private static final String RAP_BOOTSTRAP_MARKER = "rwt.remote.MessageProcessor.processMessage";
    private static final String RAP_BROWSER_COMPAT_MARKER = "__veloRapBrowserCompatPatched";
    private static final String RAP_BROWSER_COMPAT_PATCH = """
            <script type="text/javascript">
            (function() {
              if (window.%1$s) {
                return;
              }
              window.%1$s = true;
              function installPatch() {
                if (!window.rwt || !rwt.widgets || !rwt.widgets.Browser || !rwt.widgets.base || !rwt.widgets.base.Iframe) {
                  return false;
                }
                var Browser = rwt.widgets.Browser;
                var Iframe = rwt.widgets.base.Iframe;
                var originalIframeSyncSource = Iframe.prototype._syncSource;
                if (typeof originalIframeSyncSource === "function" && !Iframe.prototype.__veloSyncSourcePatched) {
                  Iframe.prototype.__veloSyncSourcePatched = true;
                  Iframe.prototype._syncSource = function() {
                    this.__veloLastSourceSyncAt = Date.now();
                    return originalIframeSyncSource.apply(this, arguments);
                  };
                }
                var originalSetInline = Browser.prototype.setInline;
                if (typeof originalSetInline === "function" && !Browser.prototype.__veloSetInlinePatched) {
                  Browser.prototype.__veloSetInlinePatched = true;
                  Browser.prototype.setInline = function(value) {
                    var previous = this.getInline && this.getInline();
                    var result = originalSetInline.call(this, value);
                    if (value !== previous && this.isCreated && this.isCreated() && this.getSource && this.getSource()) {
                      var self = this;
                      window.setTimeout(function() {
                        if (!(self.isDisposed && self.isDisposed()) && self.syncSource) {
                          self.syncSource();
                        }
                      }, 0);
                    }
                    return result;
                  };
                }
                if (typeof Browser.prototype.execute === "function" && !Browser.prototype.__veloExecutePatched) {
                  Browser.prototype.__veloExecutePatched = true;
                  var originalExecute = Browser.prototype.execute;
                  var originalCheckIframeAccess = Browser.prototype._checkIframeAccess;
                  var originalEval = Browser.prototype._eval;
                  var originalParseEvalResult = Browser.prototype._parseEvalResult;
                  Browser.prototype.execute = function(script) {
                    var self = this;
                    var retryDelayMs = 50;
                    var retryWindowMs = 2000;
                    function isDisposed() {
                      return self.isDisposed && self.isDisposed();
                    }
                    function inRetryWindow() {
                      return self.getInline
                        && self.getInline()
                        && self.__veloLastSourceSyncAt
                        && Date.now() - self.__veloLastSourceSyncAt < retryWindowMs;
                    }
                    function documentPending() {
                      try {
                        if (!self.getInline || !self.getInline()) {
                          return false;
                        }
                        if (!self.isLoaded || !self.isLoaded()) {
                          return true;
                        }
                        var doc = self.getContentDocument && self.getContentDocument();
                        if (!doc || !doc.body) {
                          return true;
                        }
                        if (doc.readyState && doc.readyState !== "complete") {
                          return true;
                        }
                        var href = doc.location && doc.location.href ? String(doc.location.href) : "";
                        if (href === "about:blank" || href.indexOf("static/html/blank.html") !== -1) {
                          return true;
                        }
                        if (/getElementById\\(\\s*['"]list['"]\\s*\\)/.test(script)
                            && typeof doc.getElementById === "function"
                            && !doc.getElementById("list")) {
                          return true;
                        }
                      } catch (ignore) {
                        return true;
                      }
                      return false;
                    }
                    function sendResult(success, result) {
                      var connection = rwt.remote.Connection.getInstance();
                      var id = rwt.remote.ObjectRegistry.getId(self);
                      var method = success ? "evaluationSucceeded" : "evaluationFailed";
                      var properties = success ? { "result" : result } : {};
                      connection.getMessageWriter().appendCall(id, method, properties);
                      if (self.getExecutedFunctionPending && self.getExecutedFunctionPending()) {
                        connection.sendImmediate(false);
                      } else {
                        connection.send();
                      }
                    }
                    function attempt() {
                      if (isDisposed()) {
                        return;
                      }
                      try {
                        originalCheckIframeAccess.call(self);
                      } catch (error) {
                        if (inRetryWindow()) {
                          window.setTimeout(attempt, retryDelayMs);
                          return;
                        }
                        sendResult(false, null);
                        return;
                      }
                      if (inRetryWindow() && documentPending()) {
                        window.setTimeout(attempt, retryDelayMs);
                        return;
                      }
                      var success = true;
                      var result = null;
                      try {
                        result = originalParseEvalResult.call(self, originalEval.call(self, script));
                      } catch (error) {
                        if (inRetryWindow()) {
                          window.setTimeout(attempt, retryDelayMs);
                          return;
                        }
                        success = false;
                      }
                      sendResult(success, result);
                    }
                    if (!self.getInline || !self.getInline()) {
                      return originalExecute.call(self, script);
                    }
                    attempt();
                  };
                }
                return true;
              }
              if (!installPatch()) {
                var attempts = 0;
                var installTimer = window.setInterval(function() {
                  attempts += 1;
                  if (installPatch() || attempts >= 20) {
                    window.clearInterval(installTimer);
                  }
                }, 25);
              }
            })();
            </script>
            """.formatted(RAP_BROWSER_COMPAT_MARKER);

    private final ServletBodyOutputStream outputStream = new ServletBodyOutputStream();
    private final Map<String, List<String>> headers = new LinkedHashMap<>();
    private final List<Cookie> cookies = new ArrayList<>();
    private int status = 200;
    private String characterEncoding = StandardCharsets.UTF_8.name();
    private String contentType = "text/plain; charset=UTF-8";
    private boolean errorSent;
    private String errorMessage;
    private boolean committed;
    private PrintWriter writer = newWriter();

    public ServletBodyOutputStream outputStream() {
        return outputStream;
    }

    public PrintWriter writer() {
        return writer;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }

    public void sendError(int status, String message) {
        this.status = status;
        this.errorSent = true;
        this.errorMessage = message;
    }

    public boolean errorSent() {
        return errorSent;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public void addHeader(String name, String value) {
        headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
    }

    public void setHeader(String name, String value) {
        headers.put(name, new ArrayList<>(List.of(value)));
    }

    public boolean containsHeader(String name) {
        return headers.containsKey(name);
    }

    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
    }

    public String characterEncoding() {
        return characterEncoding;
    }

    public void setCharacterEncoding(String characterEncoding) {
        if (committed || characterEncoding == null || characterEncoding.isBlank()) {
            return;
        }
        this.characterEncoding = characterEncoding;
    }

    public String contentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        if (committed || contentType == null || contentType.isBlank()) {
            return;
        }
        this.contentType = contentType;
    }

    public void reset() {
        if (committed) {
            throw new IllegalStateException("Response already committed");
        }
        writer.flush();
        outputStream.reset();
        writer = newWriter();
        headers.clear();
        cookies.clear();
        status = 200;
        errorSent = false;
        errorMessage = null;
    }

    public boolean isCommitted() {
        return committed;
    }

    public FullHttpResponse toNettyResponse(boolean headRequest, boolean setSessionCookie, String sessionId) {
        writer.flush();
        byte[] body = applyCompatibilityPatches(outputStream.toByteArray());
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(status),
                headRequest ? Unpooled.EMPTY_BUFFER : Unpooled.wrappedBuffer(body));

        if (contentType != null) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        }
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            response.headers().set(entry.getKey(), entry.getValue());
        }
        for (Cookie cookie : cookies) {
            response.headers().add(HttpHeaderNames.SET_COOKIE, cookie.getName() + "=" + cookie.getValue() + "; Path=/; HttpOnly");
        }
        if (setSessionCookie && sessionId != null) {
            response.headers().add(HttpHeaderNames.SET_COOKIE, "JSESSIONID=" + sessionId + "; Path=/; HttpOnly");
        }
        committed = true;
        return response;
    }

    private PrintWriter newWriter() {
        return new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);
    }

    private byte[] applyCompatibilityPatches(byte[] body) {
        if (!isHtmlResponse() || body.length == 0) {
            return body;
        }
        Charset charset = responseCharset();
        String responseBody = new String(body, charset);
        String patchedBody = injectRapBrowserCompatibilityPatch(responseBody);
        return patchedBody.equals(responseBody) ? body : patchedBody.getBytes(charset);
    }

    private boolean isHtmlResponse() {
        return contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("text/html");
    }

    private Charset responseCharset() {
        try {
            return Charset.forName(characterEncoding);
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    static String injectRapBrowserCompatibilityPatch(String responseBody) {
        if (responseBody == null
                || responseBody.contains(RAP_BROWSER_COMPAT_MARKER)
                || !responseBody.contains(RAP_CLIENT_SCRIPT)
                || !responseBody.contains(RAP_BOOTSTRAP_MARKER)) {
            return responseBody;
        }
        int rapScriptIndex = responseBody.indexOf(RAP_CLIENT_SCRIPT);
        int scriptCloseIndex = responseBody.indexOf("</script>", rapScriptIndex);
        if (scriptCloseIndex < 0) {
            return responseBody;
        }
        int insertIndex = scriptCloseIndex + "</script>".length();
        return responseBody.substring(0, insertIndex)
                + "\n"
                + RAP_BROWSER_COMPAT_PATCH
                + responseBody.substring(insertIndex);
    }
}
