package io.velo.was.aiplatform.servlet;

import io.velo.was.config.ServerConfiguration;

public final class AiPlatformLayout {

    private AiPlatformLayout() {
    }

    public static String page(String title, ServerConfiguration configuration, String body) {
        ServerConfiguration.Server server = configuration.getServer();
        ServerConfiguration.AiPlatform ai = server.getAiPlatform();
        String contextPath = ai.getConsole().getContextPath();

        StringBuilder html = new StringBuilder(12_288);
        html.append("""
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>""");
        html.append(escapeHtml(title));
        html.append("""
                </title>
                <style>
                  :root { --bg:#f6efe3; --surface:rgba(255,255,255,0.82); --text:#1a2a25; --soft:#51645d; --line:rgba(17,24,39,0.08); --teal:#0f766e; --teal-soft:rgba(15,118,110,0.12); --shadow:0 22px 50px rgba(18,37,33,0.12); --radius:28px; }
                  * { box-sizing:border-box; }
                  html { scroll-behavior:smooth; }
                  body { margin:0; font-family:"IBM Plex Sans","Noto Sans KR","Segoe UI",sans-serif; color:var(--text); background:radial-gradient(circle at top left, rgba(15,118,110,0.18), transparent 30%), radial-gradient(circle at top right, rgba(194,65,12,0.12), transparent 24%), linear-gradient(180deg,#f9f3e8 0%,#f1e7d8 100%); }
                  a { color:inherit; text-decoration:none; }
                  .shell { width:min(1280px, calc(100vw - 32px)); margin:20px auto 48px; }
                  .topbar { position:sticky; top:12px; z-index:40; display:flex; flex-wrap:wrap; gap:16px; align-items:center; justify-content:space-between; padding:18px 22px; border:1px solid rgba(255,255,255,0.45); border-radius:24px; background:rgba(255,250,242,0.76); backdrop-filter:blur(18px); box-shadow:var(--shadow); }
                  .brand { display:flex; align-items:center; gap:14px; }
                  .brand-mark { width:48px; height:48px; display:grid; place-items:center; border-radius:16px; background:linear-gradient(135deg,#0f766e 0%,#164e63 100%); color:#fff7ed; font-size:18px; font-weight:800; letter-spacing:0.08em; }
                  .brand-title { font-size:17px; font-weight:700; letter-spacing:-0.03em; }
                  .brand-subtitle, .meta, .section-title p, .metric-note, .soft-item span, th { color:var(--soft); }
                  .brand-subtitle { font-size:12px; margin-top:2px; }
                  .nav, .meta, .pill-row, .hero-chips, .timeline-caps, .toolbar { display:flex; flex-wrap:wrap; gap:10px; }
                  .nav a, .meta .logout, .pill, .hero-chip, .timeline-caps span, .toolbar button { border-radius:999px; }
                  .nav a { padding:10px 14px; font-size:13px; background:rgba(255,255,255,0.48); border:1px solid rgba(17,24,39,0.05); }
                  .meta { justify-content:flex-end; align-items:center; font-size:12px; gap:12px; }
                  .meta .logout, .toolbar button { padding:10px 14px; background:#16302b; color:#fef6e4; border:0; cursor:pointer; font-weight:600; }
                  .hero { margin-top:18px; padding:36px; border-radius:var(--radius); background:linear-gradient(135deg, rgba(15,118,110,0.98), rgba(22,78,99,0.94)); color:#f5f3ec; box-shadow:var(--shadow); }
                  .eyebrow, .metric-kicker { font-size:11px; letter-spacing:0.18em; text-transform:uppercase; opacity:0.82; }
                  .hero h1 { margin:12px 0; font-size:clamp(34px, 5vw, 54px); line-height:1.02; letter-spacing:-0.06em; }
                  .hero p { margin:0; max-width:760px; font-size:15px; line-height:1.8; color:rgba(255,247,237,0.86); }
                  .hero-chip, .pill, .timeline-caps span { padding:10px 14px; font-size:12px; }
                  .hero-chip { background:rgba(255,255,255,0.14); border:1px solid rgba(255,255,255,0.18); }
                  .stack { display:grid; gap:18px; margin-top:18px; }
                  .grid { display:grid; gap:18px; grid-template-columns:repeat(12, minmax(0, 1fr)); }
                  .panel { background:var(--surface); border:1px solid rgba(255,255,255,0.5); border-radius:var(--radius); box-shadow:var(--shadow); padding:22px; backdrop-filter:blur(18px); }
                  .span-3 { grid-column:span 3; } .span-5 { grid-column:span 5; } .span-6 { grid-column:span 6; } .span-7 { grid-column:span 7; } .span-12 { grid-column:span 12; }
                  .section-title { display:flex; justify-content:space-between; align-items:end; gap:12px; margin-bottom:14px; }
                  .section-title h2 { margin:0; font-size:24px; letter-spacing:-0.04em; }
                  .section-title p, .metric-note, .soft-item span { font-size:13px; line-height:1.7; }
                  .metric-value { margin-top:8px; font-size:34px; letter-spacing:-0.06em; font-weight:700; }
                  .pill.ok { background:var(--teal-soft); color:var(--teal); } .pill.off { background:rgba(100,116,139,0.12); color:#475569; }
                  .soft-list, .timeline { display:grid; gap:10px; margin-top:14px; }
                  .soft-item, .timeline-item { border:1px solid var(--line); background:rgba(255,255,255,0.5); }
                  .soft-item { padding:14px 16px; border-radius:14px; }
                  .soft-item strong { display:block; font-size:13px; margin-bottom:4px; }
                  .table-wrap { overflow:auto; border-radius:16px; border:1px solid var(--line); background:rgba(255,255,255,0.46); }
                  table { width:100%; border-collapse:collapse; min-width:720px; }
                  th, td { padding:14px 16px; text-align:left; border-bottom:1px solid var(--line); font-size:13px; }
                  th { font-weight:600; text-transform:uppercase; letter-spacing:0.08em; font-size:11px; }
                  tr:last-child td { border-bottom:0; }
                  code, pre { font-family:"JetBrains Mono","Cascadia Code",Consolas,monospace; }
                  .yaml, .json-box { margin:0; padding:18px; border-radius:18px; overflow:auto; font-size:12px; line-height:1.8; }
                  .yaml { white-space:pre-wrap; background:#14231f; color:#e7e0d2; }
                  .json-box { margin-top:14px; background:rgba(255,255,255,0.55); border:1px solid var(--line); color:var(--text); max-height:360px; }
                  .timeline-item { position:relative; padding:18px 18px 18px 62px; border-radius:20px; }
                  .timeline-index { position:absolute; left:18px; top:18px; width:30px; height:30px; border-radius:50%; display:grid; place-items:center; font-size:12px; font-weight:700; color:#fffdf8; background:#94a3b8; }
                  .timeline-item.active { border-color:rgba(15,118,110,0.28); background:linear-gradient(135deg, rgba(15,118,110,0.12), rgba(255,255,255,0.62)); }
                  .timeline-item.active .timeline-index { background:var(--teal); }
                  .timeline-goal { font-size:18px; font-weight:700; letter-spacing:-0.04em; }
                  @media (max-width:1080px) { .span-3, .span-5, .span-6, .span-7 { grid-column:span 12; } .shell { width:min(100vw - 18px, 1280px); } .hero { padding:26px; } .topbar { padding:16px; } }
                </style>
                </head>
                <body>
                  <div class="shell">
                    <header class="topbar">
                      <div class="brand">
                        <div class="brand-mark">AI</div>
                        <div>
                          <div class="brand-title">Velo AI Platform</div>
                          <div class="brand-subtitle">Built-in control plane for model serving, routing, and platform roadmap</div>
                        </div>
                      </div>
                      <nav class="nav">
                        <a href="#overview">Overview</a>
                        <a href="#serving">Serving</a>
                        <a href="#platform">Platform</a>
                        <a href="#advanced">Advanced</a>
                        <a href="#developer">Developer</a>
                        <a href="#sandbox">Sandbox</a>
                        <a href="#registry">Registry</a>
                        <a href="#usage">Usage</a>
                        <a href="#published">Published</a>
                        <a href="#tuning">Tuning</a>
                        <a href="#roadmap">Roadmap</a>
                        <a href="#configuration">Configuration</a>
                      </nav>
                      <div class="meta">
                        <span>""");
        html.append(escapeHtml(server.getName()));
        html.append("</span><span>");
        html.append(escapeHtml(server.getNodeId()));
        html.append("</span><span>");
        html.append(escapeHtml(ai.getMode()));
        html.append("</span><a class=\"logout\" href=\"");
        html.append(escapeHtml(contextPath));
        html.append("/logout\">Logout</a></div></header>");
        html.append(body);
        html.append("</div></body></html>");
        return html.toString();
    }

    public static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static String featurePill(String label, boolean enabled) {
        String css = enabled ? "ok" : "off";
        String state = enabled ? "Enabled" : "Disabled";
        return "<span class=\"pill " + css + "\">" + escapeHtml(label) + " - " + state + "</span>";
    }
}
