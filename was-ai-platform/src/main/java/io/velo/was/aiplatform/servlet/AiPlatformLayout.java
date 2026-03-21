package io.velo.was.aiplatform.servlet;

import io.velo.was.config.ServerConfiguration;

public final class AiPlatformLayout {

    private AiPlatformLayout() {
    }

    public static String page(String title, ServerConfiguration configuration, String body) {
        ServerConfiguration.Server server = configuration.getServer();
        ServerConfiguration.AiPlatform ai = server.getAiPlatform();
        String contextPath = ai.getConsole().getContextPath();

        StringBuilder html = new StringBuilder(16_384);
        html.append("<!DOCTYPE html>\n<html lang=\"ko\">\n<head>\n<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>").append(escapeHtml(title)).append("</title>\n");
        html.append("<style>\n");
        html.append(css());
        html.append("</style>\n</head>\n<body>\n");
        // sidebar
        html.append("<aside class=\"sidebar\" id=\"sidebar\">\n");
        html.append("  <div class=\"sidebar-brand\">\n");
        html.append("    <div class=\"brand-icon\">AI</div>\n");
        html.append("    <div><div class=\"brand-name\">Velo AI Platform</div>");
        html.append("<div class=\"brand-sub\">").append(escapeHtml(server.getName())).append(" / ").append(escapeHtml(ai.getMode())).append("</div></div>\n");
        html.append("  </div>\n");
        html.append("  <nav class=\"sidebar-nav\">\n");
        html.append("    <div class=\"nav-label\">\ub300\uc2dc\ubcf4\ub4dc</div>\n");
        html.append("    <a href=\"#overview\" class=\"nav-item active\" onclick=\"showTab(event,'overview')\">&#x1f4ca; \uac1c\uc694</a>\n");
        html.append("    <a href=\"#usage\" class=\"nav-item\" onclick=\"showTab(event,'usage')\">\ud83d\udcc8 \uc0ac\uc6a9\ub7c9</a>\n");
        html.append("    <div class=\"nav-label\">\ubaa8\ub378 \uad00\ub9ac</div>\n");
        html.append("    <a href=\"#serving\" class=\"nav-item\" onclick=\"showTab(event,'serving')\">\u2699\ufe0f \uc11c\ube59 \uc124\uc815</a>\n");
        html.append("    <a href=\"#registry\" class=\"nav-item\" onclick=\"showTab(event,'registry')\">\ud83d\uddc2\ufe0f \ubaa8\ub378 \ub808\uc9c0\uc2a4\ud2b8\ub9ac</a>\n");
        html.append("    <a href=\"#providers\" class=\"nav-item\" onclick=\"showTab(event,'providers')\">\ud83d\udd17 \ud504\ub85c\ubc14\uc774\ub354</a>\n");
        html.append("    <div class=\"nav-label\">\uc6b4\uc601</div>\n");
        html.append("    <a href=\"#sandbox\" class=\"nav-item\" onclick=\"showTab(event,'sandbox')\">\ud83e\uddea \uac8c\uc774\ud2b8\uc6e8\uc774 \ud14c\uc2a4\ud2b8</a>\n");
        html.append("    <a href=\"#tenants\" class=\"nav-item\" onclick=\"showTab(event,'tenants')\">\ud83c\udfe2 \ud14c\ub10c\ud2b8</a>\n");
        html.append("    <a href=\"#published\" class=\"nav-item\" onclick=\"showTab(event,'published')\">\ud83d\ude80 API \ubc1c\ud589</a>\n");
        html.append("    <a href=\"#developer\" class=\"nav-item\" onclick=\"showTab(event,'developer')\">\ud83d\udcd6 \uac1c\ubc1c\uc790 \ud3ec\ud138</a>\n");
        html.append("    <div class=\"nav-label\">\uc124\uc815</div>\n");
        html.append("    <a href=\"#platform\" class=\"nav-item\" onclick=\"showTab(event,'platform')\">\ud83c\udfdb\ufe0f \ud50c\ub7ab\ud3fc</a>\n");
        html.append("    <a href=\"#config\" class=\"nav-item\" onclick=\"showTab(event,'config')\">\ud83d\udcdd YAML \uc124\uc815</a>\n");
        html.append("    <a href=\"#roadmap\" class=\"nav-item\" onclick=\"showTab(event,'roadmap')\">\ud83d\uddfa\ufe0f \ub85c\ub4dc\ub9f5</a>\n");
        html.append("  </nav>\n");
        html.append("  <div class=\"sidebar-footer\">\n");
        html.append("    <a href=\"").append(escapeHtml(contextPath)).append("/logout\" class=\"nav-item logout\">\ub85c\uadf8\uc544\uc6c3</a>\n");
        html.append("  </div>\n");
        html.append("</aside>\n");
        // main
        html.append("<main class=\"main\">\n");
        html.append("  <button class=\"mobile-menu\" onclick=\"document.getElementById('sidebar').classList.toggle('open')\">\u2630 \uba54\ub274</button>\n");
        html.append(body);
        html.append("</main>\n");
        // tab script
        html.append("<script>\n");
        html.append("function showTab(e,id){e&&e.preventDefault();document.querySelectorAll('.tab-panel').forEach(p=>p.style.display='none');document.querySelectorAll('.nav-item').forEach(a=>a.classList.remove('active'));const el=document.getElementById('tab-'+id);if(el)el.style.display='block';if(e&&e.currentTarget)e.currentTarget.classList.add('active');document.getElementById('sidebar').classList.remove('open');}\n");
        html.append("document.addEventListener('DOMContentLoaded',()=>{const h=location.hash.replace('#','');if(h){const a=document.querySelector('.nav-item[onclick*=\"'+h+'\"]');if(a){showTab(null,h);document.querySelectorAll('.nav-item').forEach(x=>x.classList.remove('active'));a.classList.add('active');}}});\n");
        html.append("</script>\n");
        html.append("</body>\n</html>");
        return html.toString();
    }

    private static String css() {
        return """
            :root{--bg:#f4efe6;--surface:#fff;--text:#1a1a1a;--soft:#6b7280;--border:#e5e7eb;--primary:#0f766e;--primary-light:#ccfbf1;--primary-dark:#134e4a;--danger:#dc2626;--radius:12px;--shadow:0 1px 3px rgba(0,0,0,0.08);}
            *{box-sizing:border-box;margin:0;padding:0;}
            html{scroll-behavior:smooth;}
            body{font-family:'Pretendard','Noto Sans KR','IBM Plex Sans',system-ui,sans-serif;color:var(--text);background:var(--bg);display:flex;min-height:100vh;}
            /* sidebar */
            .sidebar{width:240px;min-height:100vh;background:var(--primary-dark);color:#e2e8f0;display:flex;flex-direction:column;position:fixed;top:0;left:0;z-index:100;overflow-y:auto;}
            .sidebar-brand{padding:20px 16px;display:flex;align-items:center;gap:12px;border-bottom:1px solid rgba(255,255,255,0.1);}
            .brand-icon{width:40px;height:40px;background:var(--primary);border-radius:10px;display:grid;place-items:center;font-weight:800;font-size:15px;color:#fff;flex-shrink:0;}
            .brand-name{font-size:14px;font-weight:700;color:#fff;}
            .brand-sub{font-size:11px;color:rgba(255,255,255,0.5);margin-top:2px;}
            .sidebar-nav{flex:1;padding:12px 8px;}
            .nav-label{font-size:10px;font-weight:700;letter-spacing:0.1em;text-transform:uppercase;color:rgba(255,255,255,0.35);padding:16px 12px 6px;white-space:nowrap;}
            .nav-item{display:block;padding:9px 12px;border-radius:8px;font-size:13px;color:rgba(255,255,255,0.7);text-decoration:none;transition:all 0.15s;cursor:pointer;white-space:nowrap;}
            .nav-item:hover{background:rgba(255,255,255,0.08);color:#fff;}
            .nav-item.active{background:var(--primary);color:#fff;font-weight:600;}
            .sidebar-footer{padding:12px 8px;border-top:1px solid rgba(255,255,255,0.1);}
            .nav-item.logout{color:rgba(255,255,255,0.5);}
            .nav-item.logout:hover{color:#fca5a5;background:rgba(220,38,38,0.15);}
            /* main */
            .main{flex:1;margin-left:240px;padding:24px 32px 48px;max-width:1100px;}
            .mobile-menu{display:none;padding:10px 16px;background:var(--primary-dark);color:#fff;border:0;border-radius:8px;cursor:pointer;font-size:14px;margin-bottom:16px;}
            /* tab panels */
            .tab-panel{display:none;}
            .tab-panel:first-of-type{display:block;}
            /* cards */
            .card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:20px;box-shadow:var(--shadow);margin-bottom:16px;}
            .card-header{font-size:18px;font-weight:700;margin-bottom:4px;word-break:keep-all;}
            .card-desc{font-size:13px;color:var(--soft);margin-bottom:16px;line-height:1.6;word-break:keep-all;}
            /* metric row */
            .metrics{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:12px;margin-bottom:16px;}
            .metric{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:16px;box-shadow:var(--shadow);}
            .metric-label{font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.06em;color:var(--soft);margin-bottom:6px;}
            .metric-val{font-size:24px;font-weight:700;color:var(--text);word-break:break-all;}
            .metric-note{font-size:12px;color:var(--soft);margin-top:4px;word-break:keep-all;}
            /* pills */
            .pills{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:12px;}
            .pill{padding:6px 12px;border-radius:20px;font-size:12px;font-weight:600;white-space:nowrap;}
            .pill.ok{background:var(--primary-light);color:var(--primary);}
            .pill.off{background:#f1f5f9;color:#64748b;}
            /* chips (hero) */
            .chips{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px;}
            .chip{padding:6px 14px;border-radius:20px;font-size:12px;font-weight:500;background:rgba(255,255,255,0.15);border:1px solid rgba(255,255,255,0.2);color:rgba(255,255,255,0.9);}
            .chip.green{background:rgba(34,197,94,0.2);border-color:rgba(34,197,94,0.3);}
            /* table */
            .tbl-wrap{overflow-x:auto;border:1px solid var(--border);border-radius:var(--radius);margin-bottom:12px;}
            table{width:100%;border-collapse:collapse;font-size:13px;min-width:600px;}
            th{background:#f8fafc;padding:10px 14px;text-align:left;font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:0.05em;color:var(--soft);border-bottom:1px solid var(--border);white-space:nowrap;}
            td{padding:10px 14px;border-bottom:1px solid var(--border);}
            tr:last-child td{border-bottom:0;}
            td strong{font-weight:600;}
            /* forms */
            .form-grid{display:grid;gap:10px;margin-bottom:12px;}
            .form-grid.cols-2{grid-template-columns:repeat(2,1fr);}
            .form-grid.cols-3{grid-template-columns:repeat(3,1fr);}
            .form-input,.form-select,.form-textarea{width:100%;padding:10px 14px;border:1px solid var(--border);border-radius:8px;font-size:13px;font-family:inherit;background:var(--surface);}
            .form-textarea{min-height:120px;resize:vertical;}
            .form-input:focus,.form-select:focus,.form-textarea:focus{outline:none;border-color:var(--primary);box-shadow:0 0 0 3px rgba(15,118,110,0.1);}
            /* buttons */
            .btns{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:12px;}
            .btn{padding:9px 16px;border:0;border-radius:8px;font-size:13px;font-weight:600;cursor:pointer;white-space:nowrap;transition:all 0.15s;}
            .btn-primary{background:var(--primary);color:#fff;}
            .btn-primary:hover{background:var(--primary-dark);}
            .btn-secondary{background:#f1f5f9;color:var(--text);border:1px solid var(--border);}
            .btn-secondary:hover{background:#e2e8f0;}
            /* code/json */
            .code-box{background:#1e293b;color:#e2e8f0;border-radius:var(--radius);padding:16px;font-family:'JetBrains Mono','Cascadia Code',Consolas,monospace;font-size:12px;line-height:1.7;overflow:auto;max-height:360px;white-space:pre-wrap;margin-bottom:12px;}
            .json-box{background:#f8fafc;border:1px solid var(--border);border-radius:var(--radius);padding:14px;font-family:'JetBrains Mono',Consolas,monospace;font-size:12px;line-height:1.6;overflow:auto;max-height:340px;white-space:pre-wrap;margin-bottom:12px;}
            /* hero */
            .hero{background:linear-gradient(135deg,var(--primary-dark),var(--primary));color:#fff;border-radius:var(--radius);padding:28px;margin-bottom:20px;}
            .hero-eyebrow{font-size:11px;letter-spacing:0.15em;text-transform:uppercase;opacity:0.7;}
            .hero h1{font-size:clamp(22px,3vw,32px);font-weight:700;line-height:1.3;margin:8px 0;word-break:keep-all;}
            .hero p{font-size:14px;line-height:1.7;color:rgba(255,255,255,0.8);word-break:keep-all;}
            /* info-list */
            .info-list{display:grid;gap:8px;}
            .info-item{background:#f8fafc;border:1px solid var(--border);border-radius:8px;padding:12px 14px;}
            .info-item strong{font-size:13px;display:block;margin-bottom:2px;}
            .info-item span{font-size:12px;color:var(--soft);line-height:1.5;word-break:keep-all;}
            /* two-col */
            .two-col{display:grid;grid-template-columns:1fr 1fr;gap:16px;}
            /* timeline */
            .timeline{display:grid;gap:10px;}
            .tl-item{position:relative;padding:14px 14px 14px 52px;border:1px solid var(--border);border-radius:10px;background:#f8fafc;}
            .tl-num{position:absolute;left:12px;top:14px;width:28px;height:28px;border-radius:50%;display:grid;place-items:center;font-size:12px;font-weight:700;color:#fff;background:#94a3b8;}
            .tl-item.active{border-color:var(--primary);background:var(--primary-light);}
            .tl-item.active .tl-num{background:var(--primary);}
            .tl-title{font-size:15px;font-weight:700;}
            .tl-note{font-size:12px;color:var(--soft);}
            .tl-caps{display:flex;flex-wrap:wrap;gap:6px;margin-top:6px;}
            .tl-caps span{font-size:11px;padding:3px 8px;background:rgba(0,0,0,0.05);border-radius:4px;}
            /* status */
            .status-on{color:var(--primary);font-weight:600;}
            .status-off{color:#94a3b8;}
            /* responsive */
            @media(max-width:768px){
                .sidebar{transform:translateX(-100%);transition:transform 0.25s;}
                .sidebar.open{transform:translateX(0);}
                .main{margin-left:0;padding:16px;}
                .mobile-menu{display:block;}
                .two-col,.form-grid.cols-2,.form-grid.cols-3{grid-template-columns:1fr;}
                .metrics{grid-template-columns:1fr 1fr;}
            }
            """;
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
        String state = enabled ? "\ud65c\uc131" : "\ube44\ud65c\uc131";
        return "<span class=\"pill " + css + "\">" + escapeHtml(label) + " \u2014 " + state + "</span>";
    }
}
