package io.velo.was.aiplatform.servlet;

import io.velo.was.admin.client.AdminClient;
import io.velo.was.admin.client.LocalAdminClient;
import io.velo.was.config.ServerConfiguration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

public class AiPlatformLoginServlet extends HttpServlet {

    private final ServerConfiguration configuration;
    private final AdminClient adminClient;
    private final SecureRandom secureRandom = new SecureRandom();

    public AiPlatformLoginServlet(ServerConfiguration configuration) {
        this(configuration, new LocalAdminClient(configuration));
    }

    public AiPlatformLoginServlet(ServerConfiguration configuration, AdminClient adminClient) {
        this.configuration = configuration;
        this.adminClient = adminClient;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        HttpSession session = req.getSession(true);
        String csrf = (String) session.getAttribute(AiPlatformAuthFilter.CSRF_ATTR);
        if (csrf == null) {
            byte[] token = new byte[24];
            secureRandom.nextBytes(token);
            csrf = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
            session.setAttribute(AiPlatformAuthFilter.CSRF_ATTR, csrf);
        }

        ServerConfiguration.AiPlatform ai = configuration.getServer().getAiPlatform();
        String currentGoal = ai.getRoadmap().getStages().stream()
                .filter(stage -> stage.getStage() == ai.getRoadmap().getCurrentStage())
                .findFirst()
                .map(ServerConfiguration.RoadmapStage::getGoal)
                .orElse("Platform roadmap in progress");
        String error = req.getParameter("error") != null
                ? "<div class=\"notice error\">관리자 계정 인증에 실패했습니다. 내장 AI Platform 콘솔은 현재 WAS 관리자 계정을 그대로 사용합니다.</div>"
                : "";
        String logout = req.getParameter("logout") != null
                ? "<div class=\"notice info\">세션이 종료되었습니다.</div>"
                : "";

        StringBuilder page = new StringBuilder(8_192);
        page.append("""
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Velo AI Platform Login</title>
                <style>
                  :root { --ink:#14261f; --ink-soft:#5f746b; --teal:#0f766e; --teal-deep:#134e4a; --paper:rgba(255,250,242,0.86); --line:rgba(20,38,31,0.08); --danger:#b91c1c; --danger-bg:rgba(185,28,28,0.08); --info:#0f766e; --info-bg:rgba(15,118,110,0.08); }
                  * { box-sizing:border-box; }
                  body { margin:0; min-height:100vh; display:grid; place-items:center; padding:18px; font-family:"IBM Plex Sans","Noto Sans KR","Segoe UI",sans-serif; background:radial-gradient(circle at 10% 20%, rgba(15,118,110,0.18), transparent 26%), radial-gradient(circle at 90% 10%, rgba(194,65,12,0.12), transparent 20%), linear-gradient(180deg, #fcf7ef, #f2e7d5); color:var(--ink); }
                  .login-shell { width:min(1080px, 100%); display:grid; grid-template-columns:1.15fr 0.85fr; border-radius:32px; overflow:hidden; box-shadow:0 24px 70px rgba(20,38,31,0.14); border:1px solid rgba(255,255,255,0.55); }
                  .story { padding:42px; background:linear-gradient(135deg, rgba(15,118,110,0.98), rgba(20,78,74,0.94)); color:#fdf7ed; }
                  .story .eyebrow, .brand { font-size:12px; letter-spacing:0.18em; text-transform:uppercase; }
                  .story .eyebrow { opacity:0.78; }
                  .story h1 { margin:14px 0; font-size:clamp(34px, 5vw, 58px); line-height:1.02; letter-spacing:-0.07em; }
                  .story p, .panel p, .meta { color:var(--ink-soft); }
                  .story p { margin:0; max-width:520px; line-height:1.85; color:rgba(253,247,237,0.84); }
                  .story-grid { margin-top:24px; display:grid; gap:12px; }
                  .story-card { padding:16px 18px; border-radius:18px; background:rgba(255,255,255,0.12); border:1px solid rgba(255,255,255,0.16); }
                  .story-card strong { display:block; font-size:14px; margin-bottom:4px; }
                  .story-card span { font-size:12px; color:rgba(253,247,237,0.78); }
                  .panel { padding:34px; background:var(--paper); backdrop-filter:blur(14px); }
                  .panel h2 { margin:12px 0 8px; font-size:30px; letter-spacing:-0.05em; }
                  .panel p { margin:0 0 24px; line-height:1.8; font-size:14px; }
                  .notice { padding:12px 14px; border-radius:16px; margin-bottom:14px; font-size:13px; line-height:1.7; }
                  .notice.error { background:var(--danger-bg); color:var(--danger); }
                  .notice.info { background:var(--info-bg); color:var(--info); }
                  label { display:block; margin-bottom:8px; font-size:13px; color:var(--ink-soft); font-weight:600; }
                  input { width:100%; padding:14px 16px; border-radius:16px; border:1px solid var(--line); background:rgba(255,255,255,0.8); font:inherit; color:var(--ink); outline:none; margin-bottom:16px; }
                  button { width:100%; border:0; border-radius:999px; padding:14px 18px; font:inherit; font-weight:700; cursor:pointer; color:#fff8ef; background:linear-gradient(135deg, var(--teal), var(--teal-deep)); }
                  .meta { margin-top:18px; padding-top:18px; border-top:1px solid var(--line); display:grid; gap:8px; font-size:12px; }
                  @media (max-width:920px) { .login-shell { grid-template-columns:1fr; } .story, .panel { padding:28px; } }
                </style>
                </head>
                <body>
                  <div class="login-shell">
                    <section class="story">
                      <div class="eyebrow">Built In Console</div>
                      <h1>AI serving control, standalone console.</h1>
                      <p>Velo WAS에 기본 탑재되는 AI Platform 기능을 `webadmin`과 분리된 전용 콘솔에서 제어합니다. 멀티모델 서빙, 플랫폼화, 라우팅, 고급 기능, 사업화 로드맵을 모두 설정 기반으로 노출합니다.</p>
                      <div class="story-grid">
                """);
        page.append("                        <div class=\"story-card\"><strong>Mode</strong><span>")
                .append(AiPlatformLayout.escapeHtml(ai.getMode()))
                .append(" · config-driven operating profile</span></div>\n")
                .append("                        <div class=\"story-card\"><strong>Console Path</strong><span>")
                .append(AiPlatformLayout.escapeHtml(ai.getConsole().getContextPath()))
                .append("</span></div>\n")
                .append("                        <div class=\"story-card\"><strong>Roadmap</strong><span>Stage ")
                .append(ai.getRoadmap().getCurrentStage())
                .append(" / 5 · ")
                .append(AiPlatformLayout.escapeHtml(currentGoal))
                .append("</span></div>\n");
        page.append("""
                      </div>
                    </section>
                    <section class="panel">
                      <div class="brand">Velo AI Platform</div>
                      <h2>관리자 계정으로 로그인</h2>
                      <p>이 콘솔은 현재 WAS 관리자 인증 체계를 재사용합니다. 별도 UI이지만 운영 자격은 하나의 관리자 계정으로 유지됩니다.</p>
                """);
        page.append(error).append(logout);
        page.append("                      <form method=\"post\" action=\"")
                .append(AiPlatformLayout.escapeHtml(req.getContextPath()))
                .append("/login\">\n")
                .append("                        <input type=\"hidden\" name=\"_csrf\" value=\"")
                .append(AiPlatformLayout.escapeHtml(csrf))
                .append("\">\n");
        page.append("""
                        <label for="username">Username</label>
                        <input id="username" name="username" type="text" value="admin" autocomplete="username">
                        <label for="password">Password</label>
                        <input id="password" name="password" type="password" value="admin" autocomplete="current-password">
                        <button type="submit">Enter AI Platform Console</button>
                      </form>
                      <div class="meta">
                """);
        page.append("                        <span>Serving strategy: ")
                .append(AiPlatformLayout.escapeHtml(ai.getServing().getDefaultStrategy()))
                .append("</span>\n")
                .append("                        <span>Models bundled by default: ")
                .append(ai.getServing().getModels().size())
                .append("</span>\n")
                .append("                        <span>Route policies bundled by default: ")
                .append(ai.getServing().getRoutePolicies().size())
                .append("</span>\n");
        page.append("""
                      </div>
                    </section>
                  </div>
                </body>
                </html>
                """);
        resp.getWriter().write(page.toString());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(true);
        String sessionCsrf = (String) session.getAttribute(AiPlatformAuthFilter.CSRF_ATTR);
        String requestCsrf = req.getParameter("_csrf");
        if (sessionCsrf == null || !sessionCsrf.equals(requestCsrf)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write("CSRF token validation failed");
            return;
        }

        String username = req.getParameter("username");
        String password = req.getParameter("password");
        if (adminClient.authenticate(username, password)) {
            session.setAttribute(AiPlatformAuthFilter.AUTH_ATTR, Boolean.TRUE);
            session.setAttribute(AiPlatformAuthFilter.USER_ATTR, username);
            resp.sendRedirect(req.getContextPath() + "/");
            return;
        }

        resp.sendRedirect(req.getContextPath() + "/login?error=1");
    }
}
