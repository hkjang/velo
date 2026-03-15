package io.velo.was.webadmin.servlet;

/**
 * Shared HTML layout helper for all Web Admin pages.
 * Provides consistent header, navigation, sidebar, and footer across all screens.
 */
public final class AdminPageLayout {

    private AdminPageLayout() {
    }

    public static final String CSS = """
            :root {
                --bg: #0f1117; --surface: #1a1d27; --surface2: #242836; --surface3: #2a2e3f;
                --border: #2e3348; --text: #e4e4e7; --text2: #9ca3af; --text3: #6b7280;
                --primary: #6366f1; --primary-hover: #818cf8; --primary-bg: rgba(99,102,241,0.1);
                --success: #22c55e; --success-bg: rgba(34,197,94,0.1);
                --warning: #f59e0b; --warning-bg: rgba(245,158,11,0.1);
                --danger: #ef4444; --danger-bg: rgba(239,68,68,0.1);
                --info: #3b82f6; --info-bg: rgba(59,130,246,0.1);
                --radius: 12px; --radius-sm: 8px; --radius-xs: 4px;
                --shadow: 0 1px 3px rgba(0,0,0,0.3);
                --sidebar-width: 240px;
            }
            * { margin:0; padding:0; box-sizing:border-box; }
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Noto Sans KR', sans-serif;
                   background: var(--bg); color: var(--text); line-height: 1.6; }
            a { color: var(--primary); text-decoration: none; }
            a:hover { color: var(--primary-hover); }

            /* Header */
            .header { background: var(--surface); border-bottom: 1px solid var(--border);
                      padding: 0 24px; height: 56px; display: flex; align-items: center;
                      justify-content: space-between; position: fixed; top: 0; left: 0; right: 0; z-index: 100; }
            .header-left { display: flex; align-items: center; gap: 16px; }
            .logo { font-size: 18px; font-weight: 700; color: var(--primary); letter-spacing: -0.5px; }
            .logo span { color: var(--text); font-weight: 400; opacity: 0.7; }
            .status-badge { display: inline-flex; align-items: center; gap: 6px;
                            padding: 3px 10px; border-radius: 20px; font-size: 12px;
                            background: var(--success-bg); color: var(--success); font-weight: 500; }
            .status-dot { width: 7px; height: 7px; border-radius: 50%;
                          background: var(--success); animation: pulse 2s infinite; }
            @keyframes pulse { 0%%,100%% { opacity:1; } 50%% { opacity:0.4; } }
            .header-right { display: flex; align-items: center; gap: 16px; font-size: 13px; color: var(--text2); }
            .header-search { background: var(--surface2); border: 1px solid var(--border); border-radius: 8px;
                             padding: 6px 12px; color: var(--text); font-size: 13px; width: 240px; outline: none; }
            .header-search:focus { border-color: var(--primary); }
            .header-search::placeholder { color: var(--text3); }

            /* Sidebar */
            .sidebar { position: fixed; top: 56px; left: 0; bottom: 0; width: var(--sidebar-width);
                       background: var(--surface); border-right: 1px solid var(--border);
                       overflow-y: auto; z-index: 90; padding: 12px 0; }
            .sidebar-section { padding: 8px 16px 4px; font-size: 11px; text-transform: uppercase;
                               letter-spacing: 0.8px; color: var(--text3); font-weight: 600; }
            .sidebar a { display: flex; align-items: center; gap: 10px; padding: 8px 16px;
                         color: var(--text2); font-size: 13px; transition: all 0.15s;
                         border-left: 3px solid transparent; }
            .sidebar a:hover { background: var(--surface2); color: var(--text); }
            .sidebar a.active { background: var(--primary-bg); color: var(--primary);
                                border-left-color: var(--primary); font-weight: 500; }
            .sidebar-icon { width: 18px; text-align: center; font-size: 14px; }

            /* Main content */
            .main { margin-left: var(--sidebar-width); margin-top: 56px; padding: 24px; min-height: calc(100vh - 56px); }
            .page-header { display: flex; align-items: center; justify-content: space-between;
                           margin-bottom: 24px; }
            .page-title { font-size: 22px; font-weight: 600; }
            .page-subtitle { font-size: 13px; color: var(--text2); margin-top: 4px; }
            .breadcrumb { font-size: 12px; color: var(--text3); margin-bottom: 16px; }
            .breadcrumb a { color: var(--text2); }
            .breadcrumb span { margin: 0 6px; }

            /* Cards & Grid */
            .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 16px; }
            .grid-2 { grid-template-columns: repeat(2, 1fr); }
            .grid-3 { grid-template-columns: repeat(3, 1fr); }
            .grid-4 { grid-template-columns: repeat(4, 1fr); }
            .card { background: var(--surface); border: 1px solid var(--border);
                    border-radius: var(--radius); padding: 20px; }
            .card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
            .card-title { font-size: 13px; text-transform: uppercase; letter-spacing: 0.5px;
                          color: var(--text2); font-weight: 600; }

            /* Metrics */
            .metric { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 12px; }
            .metric-label { font-size: 14px; color: var(--text2); }
            .metric-value { font-size: 24px; font-weight: 600; }
            .metric-value.sm { font-size: 18px; }
            .metric-value.success { color: var(--success); }
            .metric-value.warning { color: var(--warning); }
            .metric-value.danger { color: var(--danger); }

            /* Progress */
            .progress-bar { height: 6px; background: var(--surface2); border-radius: 3px; overflow: hidden; }
            .progress-fill { height: 100%%; border-radius: 3px; transition: width 0.5s; }

            /* Tables */
            .data-table { width: 100%%; border-collapse: collapse; font-size: 13px; }
            .data-table th { text-align: left; padding: 10px 12px; color: var(--text2);
                             font-weight: 600; font-size: 12px; text-transform: uppercase;
                             letter-spacing: 0.5px; border-bottom: 1px solid var(--border); }
            .data-table td { padding: 10px 12px; border-bottom: 1px solid var(--border); }
            .data-table tr:hover td { background: var(--surface2); }
            .data-table tr:last-child td { border-bottom: none; }
            .info-table { width: 100%%; }
            .info-table tr { border-bottom: 1px solid var(--border); }
            .info-table tr:last-child { border-bottom: none; }
            .info-table td { padding: 10px 0; font-size: 14px; }
            .info-table td:first-child { color: var(--text2); width: 40%%; }

            /* Badges & Tags */
            .badge { display: inline-flex; align-items: center; gap: 4px; padding: 2px 8px;
                     border-radius: 4px; font-size: 11px; font-weight: 600; text-transform: uppercase;
                     letter-spacing: 0.3px; }
            .badge-success { background: var(--success-bg); color: var(--success); }
            .badge-warning { background: var(--warning-bg); color: var(--warning); }
            .badge-danger { background: var(--danger-bg); color: var(--danger); }
            .badge-info { background: var(--info-bg); color: var(--info); }
            .badge-neutral { background: var(--surface2); color: var(--text2); }

            /* Buttons */
            .btn { display: inline-flex; align-items: center; gap: 6px; padding: 8px 16px;
                   border-radius: var(--radius-sm); font-size: 13px; font-weight: 500;
                   border: 1px solid var(--border); cursor: pointer; transition: all 0.15s;
                   background: var(--surface2); color: var(--text); }
            .btn:hover { background: var(--surface3); }
            .btn-primary { background: var(--primary); color: white; border-color: var(--primary); }
            .btn-primary:hover { background: var(--primary-hover); }
            .btn-success { background: var(--success); color: white; border-color: var(--success); }
            .btn-danger { background: var(--danger); color: white; border-color: var(--danger); }
            .btn-sm { padding: 4px 10px; font-size: 12px; }
            .btn-group { display: flex; gap: 8px; }

            /* Tabs */
            .tabs { display: flex; gap: 0; border-bottom: 1px solid var(--border); margin-bottom: 20px; }
            .tab { padding: 10px 20px; font-size: 13px; color: var(--text2); cursor: pointer;
                   border-bottom: 2px solid transparent; transition: all 0.15s; }
            .tab:hover { color: var(--text); }
            .tab.active { color: var(--primary); border-bottom-color: var(--primary); }
            .tab-panel { display: none; }
            .tab-panel.active { display: block; }

            /* Alerts */
            .alert { padding: 12px 16px; border-radius: var(--radius-sm); font-size: 13px;
                     margin-bottom: 16px; display: flex; align-items: center; gap: 10px; }
            .alert-info { background: var(--info-bg); color: var(--info); }
            .alert-warning { background: var(--warning-bg); color: var(--warning); }
            .alert-danger { background: var(--danger-bg); color: var(--danger); }
            .alert-success { background: var(--success-bg); color: var(--success); }

            /* Modals */
            .modal-overlay { display: none; position: fixed; inset: 0; background: rgba(0,0,0,0.6);
                             z-index: 200; justify-content: center; align-items: center; }
            .modal-overlay.open { display: flex; }
            .modal { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius);
                     padding: 24px; min-width: 400px; max-width: 600px; max-height: 80vh; overflow-y: auto; }
            .modal-title { font-size: 18px; font-weight: 600; margin-bottom: 16px; }
            .modal-footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }

            /* Form */
            .form-group { margin-bottom: 16px; }
            .form-label { display: block; font-size: 13px; color: var(--text2); margin-bottom: 6px; font-weight: 500; }
            .form-input { width: 100%%; padding: 8px 12px; background: var(--bg); border: 1px solid var(--border);
                          border-radius: var(--radius-sm); color: var(--text); font-size: 13px; outline: none; }
            .form-input:focus { border-color: var(--primary); }
            .form-select { width: 100%%; padding: 8px 12px; background: var(--bg); border: 1px solid var(--border);
                           border-radius: var(--radius-sm); color: var(--text); font-size: 13px; outline: none; }
            .form-hint { font-size: 11px; color: var(--text3); margin-top: 4px; }

            /* Accessibility */
            .skip-link { position:absolute;top:-40px;left:0;padding:8px;background:var(--primary);color:white;z-index:1000;font-size:13px;text-decoration:none;border-radius:0 0 8px 0; }
            .skip-link:focus { top:0; }
            *:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; border-radius: 4px; }
            .btn:focus-visible, .form-input:focus-visible, .header-search:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
            [role="menuitem"]:focus { background: var(--surface2); outline: none; }
            @media (prefers-reduced-motion: reduce) {
              *, *::before, *::after { animation-duration: 0.01ms !important; transition-duration: 0.01ms !important; }
            }

            /* Notification Bell & Favorites */
            .header-icon-btn { position:relative;background:none;border:1px solid var(--border);border-radius:8px;
                               color:var(--text2);cursor:pointer;padding:6px 10px;font-size:16px;line-height:1;
                               transition:all 0.15s; }
            .header-icon-btn:hover { background:var(--surface2);color:var(--text); }
            .notif-badge { position:absolute;top:-4px;right:-4px;background:var(--danger);color:white;
                           font-size:10px;font-weight:700;min-width:16px;height:16px;border-radius:8px;
                           display:flex;align-items:center;justify-content:center;padding:0 4px; }
            .dropdown-panel { display:none;position:absolute;top:calc(100%% + 8px);right:0;width:340px;
                              background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);
                              box-shadow:var(--shadow);z-index:150;overflow:hidden; }
            .dropdown-panel.open { display:block; }
            .dropdown-header { padding:12px 16px;border-bottom:1px solid var(--border);font-size:13px;
                               font-weight:600;display:flex;justify-content:space-between;align-items:center; }
            .dropdown-item { padding:10px 16px;font-size:13px;border-bottom:1px solid var(--border);
                             cursor:pointer;transition:background 0.1s; }
            .dropdown-item:hover { background:var(--surface2); }
            .dropdown-item:last-child { border-bottom:none; }
            .dropdown-item .item-time { font-size:11px;color:var(--text3);margin-top:2px; }
            .dropdown-empty { padding:24px 16px;text-align:center;color:var(--text3);font-size:13px; }

            /* Sidebar favorite star */
            .sidebar a .fav-star { display:none;margin-left:auto;font-size:12px;color:var(--text3);cursor:pointer;padding:2px 4px; }
            .sidebar a:hover .fav-star { display:inline; }
            .sidebar a .fav-star.active { display:inline;color:var(--warning); }

            /* Keyboard shortcuts section */
            .sidebar-shortcuts { padding:12px 16px;border-top:1px solid var(--border);margin-top:auto; }
            .sidebar-shortcuts .shortcut-row { display:flex;justify-content:space-between;align-items:center;
                                               padding:3px 0;font-size:11px;color:var(--text3); }
            .sidebar-shortcuts kbd { background:var(--surface2);padding:1px 5px;border-radius:3px;font-size:10px;
                                     font-family:inherit;border:1px solid var(--border); }

            /* Responsive */
            @media (max-width: 1024px) {
                .sidebar { display: none; }
                .main { margin-left: 0; }
                .grid-4 { grid-template-columns: repeat(2, 1fr); }
            }
            """;

    /**
     * Generates the HTML head section.
     */
    public static String head(String title) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s - Velo Web Admin</title>
                <style>
                %s
                </style>
                </head>
                <body>
                <a href="#main-content" class="skip-link">Skip to content</a>
                """.formatted(title, CSS);
    }

    /**
     * Generates the header bar with search and user info.
     */
    public static String header(String serverName, String nodeId, String contextPath) {
        return """
                <div class="header" role="banner">
                  <div class="header-left">
                    <div class="logo">Velo<span> Web Admin</span></div>
                    <div class="status-badge"><span class="status-dot"></span><span data-i18n="header.running">Running</span></div>
                  </div>
                  <div class="header-right">
                    <input class="header-search" type="text" placeholder="Search servers, apps, commands... (Ctrl+K)"
                           id="globalSearch" autocomplete="off" aria-label="Search"
                           data-i18n-placeholder="header.searchPlaceholder">
                    <div style="position:relative;display:inline-block;">
                      <button class="header-icon-btn" id="favoritesBtn" title="Favorites" aria-label="Favorites"
                              aria-expanded="false" aria-haspopup="true" onclick="toggleFavorites()">&#9733;</button>
                      <div class="dropdown-panel" id="favoritesDropdown" role="menu" aria-label="Favorites">
                        <div class="dropdown-header"><span data-i18n="header.favorites">Favorites</span>
                          <a href="#" onclick="manageFavorites();return false;" style="font-size:12px;" data-i18n="header.manage">Manage</a>
                        </div>
                        <div id="favoritesList"></div>
                      </div>
                    </div>
                    <div style="position:relative;display:inline-block;">
                      <button class="header-icon-btn" id="notifBtn" title="Notifications" aria-label="Notifications"
                              aria-expanded="false" aria-haspopup="true" onclick="toggleNotifications()">&#128276;
                        <span class="notif-badge" id="notifBadge" style="display:none;">0</span>
                      </button>
                      <div class="dropdown-panel" id="notifDropdown" role="menu" aria-label="Notifications">
                        <div class="dropdown-header"><span data-i18n="header.notifications">Notifications</span>
                          <a href="#" onclick="clearNotifications();return false;" style="font-size:12px;" data-i18n="header.clear">Clear</a>
                        </div>
                        <div id="notifList"></div>
                      </div>
                    </div>
                    <span>%s &middot; %s</span>
                    <a href="%s/logout" style="color:var(--text2);font-size:13px;padding:4px 8px;
                       border:1px solid var(--border);border-radius:6px;" data-i18n="header.logout">Logout</a>
                  </div>
                </div>
                %s
                """.formatted(escapeHtml(serverName), escapeHtml(nodeId), contextPath,
                commandPalette(contextPath));
    }

    /**
     * Generates the sidebar navigation. The {@code activePage} parameter
     * highlights the matching menu item.
     */
    public static String sidebar(String contextPath, String activePage) {
        return """
                <div class="sidebar" role="navigation" aria-label="Main navigation">
                  <div class="sidebar-section" data-i18n="nav.overview">Overview</div>
                  %s
                  <div class="sidebar-section" data-i18n="nav.infrastructure">Infrastructure</div>
                  %s
                  %s
                  %s
                  <div class="sidebar-section" data-i18n="nav.workloads">Workloads</div>
                  %s
                  %s
                  <div class="sidebar-section" data-i18n="nav.operations">Operations</div>
                  %s
                  %s
                  %s
                  <div class="sidebar-section" data-i18n="nav.tools">Tools</div>
                  %s
                  %s
                  %s
                  <div class="sidebar-section" data-i18n="nav.administration">Administration</div>
                  %s
                  %s
                  %s
                  <div class="sidebar-shortcuts">
                    <div class="sidebar-section" style="padding-left:0;" data-i18n="nav.shortcuts">Shortcuts</div>
                    <div class="shortcut-row"><span data-i18n="nav.shortcut.palette">Command Palette</span><kbd>Ctrl+K</kbd></div>
                    <div class="shortcut-row"><span data-i18n="nav.shortcut.dashboard">Go to Dashboard</span><kbd>G</kbd> <kbd>D</kbd></div>
                    <div class="shortcut-row"><span data-i18n="nav.shortcut.servers">Go to Servers</span><kbd>G</kbd> <kbd>S</kbd></div>
                    <div class="shortcut-row"><span data-i18n="nav.shortcut.console">Go to Console</span><kbd>G</kbd> <kbd>C</kbd></div>
                  </div>
                </div>
                """.formatted(
                sidebarLink(contextPath, "/", "dashboard", activePage, "\u25A3", "Dashboard"),
                sidebarLink(contextPath, "/servers", "servers", activePage, "\u2726", "Servers"),
                sidebarLink(contextPath, "/clusters", "clusters", activePage, "\u29BF", "Clusters"),
                sidebarLink(contextPath, "/nodes", "nodes", activePage, "\u2B22", "Nodes"),
                sidebarLink(contextPath, "/applications", "applications", activePage, "\u25CE", "Applications"),
                sidebarLink(contextPath, "/resources", "resources", activePage, "\u29C9", "Resources"),
                sidebarLink(contextPath, "/monitoring", "monitoring", activePage, "\u25C8", "Monitoring"),
                sidebarLink(contextPath, "/diagnostics", "diagnostics", activePage, "\u2692", "Diagnostics"),
                sidebarLink(contextPath, "/history", "history", activePage, "\u29D6", "History"),
                sidebarLink(contextPath, "/console", "console", activePage, "\u276F", "Console"),
                sidebarLink(contextPath, "/scripts", "scripts", activePage, "\u2630", "Scripts"),
                sidebarLink(contextPath, "/domain", "domain", activePage, "\u2B21", "Domain"),
                sidebarLink(contextPath, "/security", "security", activePage, "\u26BF", "Security"),
                sidebarLink(contextPath, "/settings", "settings", activePage, "\u2699", "Settings"),
                sidebarLink(contextPath, "/api-docs/ui", "api-docs", activePage, "\u2B64", "API Docs")
        );
    }

    private static String sidebarLink(String ctx, String path, String page, String active, String icon, String label) {
        boolean isActive = page.equals(active);
        String cls = isActive ? " class=\"active\"" : "";
        String aria = isActive ? " aria-current=\"page\"" : "";
        return "<a href=\"%s%s\"%s%s role=\"menuitem\"><span class=\"sidebar-icon\">%s</span><span data-i18n=\"nav.%s\">%s</span><span class=\"fav-star\" data-page=\"%s\" data-name=\"%s\" onclick=\"event.preventDefault();toggleFavoritePage(this);\">&#9733;</span></a>"
                .formatted(ctx, path, cls, aria, icon, page, label, path, label);
    }

    /**
     * Closes body and html tags.
     */
    public static String footer() {
        return """
                <div id="toastContainer" style="position:fixed;top:68px;right:24px;z-index:9999;display:flex;flex-direction:column;gap:8px;"></div>
                <script>
                function showToast(msg, type) {
                  type = type || 'info';
                  var colors = {success:'var(--success)',error:'var(--danger)',warning:'var(--warning)',info:'var(--primary)'};
                  var bgColors = {success:'var(--success-bg)',error:'var(--danger-bg)',warning:'var(--warning-bg)',info:'var(--primary-bg)'};
                  var container = document.getElementById('toastContainer');
                  var toast = document.createElement('div');
                  toast.style.cssText = 'padding:12px 20px;border-radius:8px;font-size:13px;max-width:400px;'
                    + 'border:1px solid ' + colors[type] + ';color:' + colors[type] + ';background:' + bgColors[type]
                    + ';backdrop-filter:blur(12px);animation:slideIn 0.3s ease;cursor:pointer;';
                  toast.textContent = msg;
                  toast.onclick = function(){ toast.remove(); };
                  container.appendChild(toast);
                  setTimeout(function(){ if(toast.parentNode) toast.style.opacity='0'; toast.style.transition='opacity 0.3s'; setTimeout(function(){toast.remove();},300); }, 4000);
                }
                // Apply saved theme globally
                (function() {
                  var t = localStorage.getItem('velo-theme');
                  if (t === 'light') {
                    var s = document.createElement('style');
                    s.id = 'light-theme-global';
                    s.textContent = ':root { --bg: #f5f6f8; --surface: #ffffff; --surface2: #f0f1f3; --surface3: #e4e6ea; --border: #d1d5db; --text: #1a1d27; --text2: #495057; --text3: #868e96; --shadow: 0 1px 3px rgba(0,0,0,0.08); }';
                    document.head.appendChild(s);
                  }
                })();

                // --- Global I18N ---
                var _GLOBAL_I18N = {
                  en: {
                    'header.running': 'Running',
                    'header.searchPlaceholder': 'Search servers, apps, commands... (Ctrl+K)',
                    'header.favorites': 'Favorites',
                    'header.manage': 'Manage',
                    'header.notifications': 'Notifications',
                    'header.clear': 'Clear',
                    'header.logout': 'Logout',
                    'nav.overview': 'Overview',
                    'nav.infrastructure': 'Infrastructure',
                    'nav.workloads': 'Workloads',
                    'nav.operations': 'Operations',
                    'nav.tools': 'Tools',
                    'nav.administration': 'Administration',
                    'nav.shortcuts': 'Shortcuts',
                    'nav.shortcut.palette': 'Command Palette',
                    'nav.shortcut.dashboard': 'Go to Dashboard',
                    'nav.shortcut.servers': 'Go to Servers',
                    'nav.shortcut.console': 'Go to Console',
                    'nav.dashboard': 'Dashboard',
                    'nav.servers': 'Servers',
                    'nav.clusters': 'Clusters',
                    'nav.nodes': 'Nodes',
                    'nav.applications': 'Applications',
                    'nav.resources': 'Resources',
                    'nav.monitoring': 'Monitoring',
                    'nav.diagnostics': 'Diagnostics',
                    'nav.history': 'History',
                    'nav.console': 'Console',
                    'nav.scripts': 'Scripts',
                    'nav.domain': 'Domain',
                    'nav.security': 'Security',
                    'nav.settings': 'Settings',
                    'nav.api-docs': 'API Docs',
                    'page.dashboard': 'Dashboard',
                    'page.servers': 'Servers',
                    'page.clusters': 'Clusters',
                    'page.nodes': 'Nodes',
                    'page.applications': 'Applications',
                    'page.resources': 'Resources',
                    'page.monitoring': 'Monitoring',
                    'page.diagnostics': 'Diagnostics',
                    'page.history': 'History',
                    'page.console': 'Console',
                    'page.security': 'Security',
                    'page.settings': 'Settings'
                  },
                  ko: {
                    'header.running': '실행 중',
                    'header.searchPlaceholder': '서버, 앱, 명령 검색... (Ctrl+K)',
                    'header.favorites': '즐겨찾기',
                    'header.manage': '관리',
                    'header.notifications': '알림',
                    'header.clear': '지우기',
                    'header.logout': '로그아웃',
                    'nav.overview': '개요',
                    'nav.infrastructure': '인프라',
                    'nav.workloads': '워크로드',
                    'nav.operations': '운영',
                    'nav.tools': '도구',
                    'nav.administration': '관리',
                    'nav.shortcuts': '단축키',
                    'nav.shortcut.palette': '명령 팔레트',
                    'nav.shortcut.dashboard': '대시보드 이동',
                    'nav.shortcut.servers': '서버 이동',
                    'nav.shortcut.console': '콘솔 이동',
                    'nav.dashboard': '대시보드',
                    'nav.servers': '서버',
                    'nav.clusters': '클러스터',
                    'nav.nodes': '노드',
                    'nav.applications': '애플리케이션',
                    'nav.resources': '리소스',
                    'nav.monitoring': '모니터링',
                    'nav.diagnostics': '진단',
                    'nav.history': '변경 이력',
                    'nav.console': '콘솔',
                    'nav.scripts': '스크립트',
                    'nav.domain': '도메인',
                    'nav.security': '보안',
                    'nav.settings': '설정',
                    'nav.api-docs': 'API 문서',
                    'page.dashboard': '대시보드',
                    'page.servers': '서버 관리',
                    'page.clusters': '클러스터 관리',
                    'page.nodes': '노드 관리',
                    'page.applications': '애플리케이션 관리',
                    'page.resources': '리소스 관리',
                    'page.monitoring': '모니터링',
                    'page.diagnostics': '진단',
                    'page.history': '변경 이력',
                    'page.console': '콘솔',
                    'page.security': '보안 관리',
                    'page.settings': '설정'
                  }
                };
                function _getGlobalLang() {
                  return localStorage.getItem('velo-lang') || 'en';
                }
                function _gt(key) {
                  var lang = _getGlobalLang();
                  var dict = _GLOBAL_I18N[lang] || _GLOBAL_I18N['en'];
                  return dict[key] || (_GLOBAL_I18N['en'] && _GLOBAL_I18N['en'][key]) || key;
                }
                function applyGlobalI18n() {
                  var lang = _getGlobalLang();
                  document.documentElement.lang = lang;
                  document.querySelectorAll('[data-i18n]').forEach(function(el) {
                    var key = el.getAttribute('data-i18n');
                    var val = _gt(key);
                    if (val && val !== key) el.textContent = val;
                  });
                  document.querySelectorAll('[data-i18n-placeholder]').forEach(function(el) {
                    var key = el.getAttribute('data-i18n-placeholder');
                    var val = _gt(key);
                    if (val && val !== key) el.placeholder = val;
                  });
                  document.querySelectorAll('[data-i18n-title]').forEach(function(el) {
                    var key = el.getAttribute('data-i18n-title');
                    var val = _gt(key);
                    if (val && val !== key) el.title = val;
                  });
                }
                applyGlobalI18n();

                // --- Favorites System ---
                function getFavorites() {
                  try { return JSON.parse(localStorage.getItem('velo-favorites') || '[]'); } catch(e){ return []; }
                }
                function saveFavorites(favs) { localStorage.setItem('velo-favorites', JSON.stringify(favs)); }
                function renderFavorites() {
                  var list = document.getElementById('favoritesList');
                  if (!list) return;
                  var favs = getFavorites();
                  if (favs.length === 0) {
                    list.innerHTML = '<div class="dropdown-empty">No favorites yet. Click the star next to a sidebar link to add one.</div>';
                  } else {
                    list.innerHTML = '';
                    favs.forEach(function(f) {
                      var div = document.createElement('div');
                      div.className = 'dropdown-item';
                      div.innerHTML = '<a href="' + f.path + '" style="color:var(--text);text-decoration:none;display:block;">' + f.name + '</a>';
                      list.appendChild(div);
                    });
                  }
                  // Update sidebar stars
                  document.querySelectorAll('.fav-star').forEach(function(star) {
                    var page = star.getAttribute('data-page');
                    var isFav = favs.some(function(f){ return f.path.endsWith(page); });
                    if (isFav) star.classList.add('active'); else star.classList.remove('active');
                  });
                }
                function toggleFavorites() {
                  var dd = document.getElementById('favoritesDropdown');
                  var notifDd = document.getElementById('notifDropdown');
                  if (notifDd) { notifDd.classList.remove('open'); document.getElementById('notifBtn').setAttribute('aria-expanded','false'); }
                  dd.classList.toggle('open');
                  document.getElementById('favoritesBtn').setAttribute('aria-expanded', dd.classList.contains('open') ? 'true' : 'false');
                  if (dd.classList.contains('open')) renderFavorites();
                }
                function toggleFavoritePage(star) {
                  var page = star.getAttribute('data-page');
                  var name = star.getAttribute('data-name');
                  var favs = getFavorites();
                  var href = star.parentElement.getAttribute('href');
                  var idx = favs.findIndex(function(f){ return f.path === href; });
                  if (idx >= 0) { favs.splice(idx, 1); star.classList.remove('active'); }
                  else { favs.push({name: name, path: href}); star.classList.add('active'); }
                  saveFavorites(favs);
                }
                function manageFavorites() {
                  var dd = document.getElementById('favoritesDropdown');
                  dd.classList.remove('open');
                  var favs = getFavorites();
                  var msg = favs.length === 0 ? 'No favorites saved.' : 'Current favorites:\\n' + favs.map(function(f){return '- '+f.name;}).join('\\n') + '\\n\\nClear all favorites?';
                  if (favs.length > 0 && confirm(msg)) { saveFavorites([]); renderFavorites(); }
                }
                renderFavorites();

                // --- Notification Bell ---
                var _notifEvents = [];
                function fetchNotifications() {
                  fetch(window.location.pathname.replace(/\\/[^\\/]*$/, '') + '/api/audit?limit=5')
                    .then(function(r){ return r.ok ? r.json() : []; })
                    .then(function(data){
                      if (Array.isArray(data)) _notifEvents = data;
                      else if (data && Array.isArray(data.events)) _notifEvents = data.events;
                      else _notifEvents = [];
                      var badge = document.getElementById('notifBadge');
                      if (_notifEvents.length > 0) { badge.textContent = _notifEvents.length; badge.style.display = 'flex'; }
                      else { badge.style.display = 'none'; }
                    }).catch(function(){ _notifEvents = []; });
                }
                function renderNotifications() {
                  var list = document.getElementById('notifList');
                  if (!list) return;
                  if (_notifEvents.length === 0) {
                    list.innerHTML = '<div class="dropdown-empty">No recent events.</div>';
                  } else {
                    list.innerHTML = '';
                    _notifEvents.forEach(function(evt) {
                      var div = document.createElement('div');
                      div.className = 'dropdown-item';
                      var desc = evt.action || evt.command || evt.message || 'Event';
                      var time = evt.timestamp || evt.time || '';
                      div.innerHTML = '<div style="color:var(--text);font-size:13px;">' + desc + '</div>'
                        + (time ? '<div class="item-time">' + time + '</div>' : '');
                      list.appendChild(div);
                    });
                  }
                }
                function toggleNotifications() {
                  var dd = document.getElementById('notifDropdown');
                  var favDd = document.getElementById('favoritesDropdown');
                  if (favDd) { favDd.classList.remove('open'); document.getElementById('favoritesBtn').setAttribute('aria-expanded','false'); }
                  dd.classList.toggle('open');
                  document.getElementById('notifBtn').setAttribute('aria-expanded', dd.classList.contains('open') ? 'true' : 'false');
                  if (dd.classList.contains('open')) renderNotifications();
                }
                function clearNotifications() {
                  _notifEvents = [];
                  var badge = document.getElementById('notifBadge');
                  if (badge) badge.style.display = 'none';
                  renderNotifications();
                }
                fetchNotifications();
                setInterval(fetchNotifications, 30000);

                // Close dropdowns on outside click
                document.addEventListener('click', function(e) {
                  var favBtn = document.getElementById('favoritesBtn');
                  var favDd = document.getElementById('favoritesDropdown');
                  var notifBtn = document.getElementById('notifBtn');
                  var notifDd = document.getElementById('notifDropdown');
                  if (favDd && !favDd.contains(e.target) && e.target !== favBtn) favDd.classList.remove('open');
                  if (notifDd && !notifDd.contains(e.target) && e.target !== notifBtn && !notifBtn.contains(e.target)) notifDd.classList.remove('open');
                });

                // --- Keyboard shortcuts: G then D/S/C ---
                (function(){
                  var gPressed = false, gTimer;
                  var ctx = document.querySelector('.sidebar a') ? document.querySelector('.sidebar a').getAttribute('href').replace(/\\/[^\\/]*$/, '') : '';
                  document.addEventListener('keydown', function(e) {
                    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.isContentEditable) return;
                    if (e.key === 'g' || e.key === 'G') {
                      if (!gPressed) { gPressed = true; clearTimeout(gTimer); gTimer = setTimeout(function(){ gPressed=false; }, 1000); return; }
                    }
                    if (gPressed) {
                      gPressed = false; clearTimeout(gTimer);
                      if (e.key === 'd' || e.key === 'D') { e.preventDefault(); window.location.href = ctx + '/'; }
                      else if (e.key === 's' || e.key === 'S') { e.preventDefault(); window.location.href = ctx + '/servers'; }
                      else if (e.key === 'c' || e.key === 'C') { e.preventDefault(); window.location.href = ctx + '/console'; }
                    }
                  });
                })();
                </script>
                <style>
                @keyframes slideIn { from { transform:translateX(100%%);opacity:0; } to { transform:translateX(0);opacity:1; } }
                </style>
                </body>
                </html>""";
    }

    /**
     * Wraps body content with head + header + sidebar + main wrapper.
     */
    public static String page(String title, String serverName, String nodeId,
                              String contextPath, String activePage, String bodyContent) {
        return head(title)
                + header(serverName, nodeId, contextPath)
                + sidebar(contextPath, activePage)
                + "<div class=\"main\" id=\"main-content\" role=\"main\" tabindex=\"-1\">\n" + bodyContent + "\n</div>\n"
                + footer();
    }

    /**
     * Generates the Command Palette modal (Ctrl+K).
     * Provides keyboard-driven quick navigation and command execution.
     */
    public static String commandPalette(String contextPath) {
        return """
                <div class="modal-overlay" id="cmdPalette" style="align-items:flex-start;padding-top:15vh;">
                  <div class="modal" style="min-width:560px;padding:0;overflow:hidden;">
                    <div style="padding:12px 16px;border-bottom:1px solid var(--border);display:flex;align-items:center;gap:8px;">
                      <span style="color:var(--text3);font-size:14px;">&gt;</span>
                      <input id="paletteInput" type="text" placeholder="Search pages, commands, settings..."
                             autocomplete="off" style="flex:1;background:transparent;border:none;color:var(--text);
                             font-size:14px;outline:none;">
                      <kbd style="background:var(--surface2);color:var(--text3);padding:2px 6px;border-radius:4px;
                           font-size:11px;">ESC</kbd>
                    </div>
                    <div id="paletteResults" style="max-height:360px;overflow-y:auto;padding:8px;"></div>
                  </div>
                </div>
                <script>
                (function(){
                  var palette = document.getElementById('cmdPalette');
                  var input = document.getElementById('paletteInput');
                  var results = document.getElementById('paletteResults');
                  var ctx = '%s';
                  function _pt(key,fallback) {
                    return (typeof _gt === 'function') ? _gt(key) || fallback : fallback;
                  }
                  var items = [
                    {type:'page',name:_pt('nav.dashboard','Dashboard'),path:'/'},
                    {type:'page',name:_pt('nav.servers','Servers'),path:'/servers'},
                    {type:'page',name:_pt('nav.clusters','Clusters'),path:'/clusters'},
                    {type:'page',name:_pt('nav.nodes','Nodes'),path:'/nodes'},
                    {type:'page',name:_pt('nav.applications','Applications'),path:'/applications'},
                    {type:'page',name:_pt('nav.resources','Resources'),path:'/resources'},
                    {type:'page',name:_pt('nav.monitoring','Monitoring'),path:'/monitoring'},
                    {type:'page',name:_pt('nav.diagnostics','Diagnostics'),path:'/diagnostics'},
                    {type:'page',name:_pt('nav.history','History'),path:'/history'},
                    {type:'page',name:_pt('nav.console','Console'),path:'/console'},
                    {type:'page',name:_pt('nav.scripts','Scripts'),path:'/scripts'},
                    {type:'page',name:_pt('nav.domain','Domain'),path:'/domain'},
                    {type:'page',name:_pt('nav.security','Security'),path:'/security'},
                    {type:'page',name:_pt('nav.settings','Settings'),path:'/settings'},
                    {type:'page',name:_pt('nav.api-docs','API Docs'),path:'/api-docs/ui'},
                    {type:'cmd',name:'Server Status',cmd:'status'},
                    {type:'cmd',name:'List Servers',cmd:'listservers'},
                    {type:'cmd',name:'List Applications',cmd:'listapplications'},
                    {type:'cmd',name:'Memory Info',cmd:'memoryinfo'},
                    {type:'cmd',name:'Thread Info',cmd:'threadinfo'},
                    {type:'cmd',name:'JVM Info',cmd:'jvminfo'},
                    {type:'cmd',name:'System Info',cmd:'systeminfo'},
                    {type:'cmd',name:'Thread Dump',cmd:'threaddump'},
                    {type:'cmd',name:'Deadlock Check',cmd:'deadlockcheck'},
                    {type:'cmd',name:'List DataSources',cmd:'listdatasources'},
                    {type:'cmd',name:'List Thread Pools',cmd:'listthreadpools'},
                    {type:'cmd',name:'List Users',cmd:'listusers'},
                    {type:'cmd',name:'List Roles',cmd:'listroles'},
                    {type:'action',name:_pt('header.logout','Logout'),path:'/logout'},
                    {type:'action',name:_pt('palette.refresh','Refresh Page'),action:'reload'}
                  ];
                  var selected = 0;

                  function render(query) {
                    var filtered = items;
                    if (query) {
                      var q = query.toLowerCase();
                      filtered = items.filter(function(i){return i.name.toLowerCase().includes(q) || (i.cmd && i.cmd.includes(q));});
                    }
                    selected = 0;
                    results.innerHTML = '';
                    filtered.forEach(function(item, idx) {
                      var div = document.createElement('div');
                      var typeLabel = item.type === 'page' ? 'Page' : item.type === 'cmd' ? 'Command' : 'Action';
                      var typeBadge = item.type === 'page' ? 'badge-info' : item.type === 'cmd' ? 'badge-success' : 'badge-warning';
                      div.style.cssText = 'padding:8px 12px;border-radius:6px;cursor:pointer;display:flex;align-items:center;justify-content:space-between;';
                      div.innerHTML = '<span>' + item.name + '</span><span class="badge '+typeBadge+'" style="font-size:10px;">' + typeLabel + '</span>';
                      if (idx === selected) div.style.background = 'var(--surface2)';
                      div.onclick = function(){ activate(item); };
                      div.onmouseover = function(){ selected = idx; highlight(); };
                      results.appendChild(div);
                    });
                  }
                  function highlight() {
                    var children = results.children;
                    for (var i = 0; i < children.length; i++) {
                      children[i].style.background = i === selected ? 'var(--surface2)' : '';
                    }
                  }
                  function activate(item) {
                    closePalette();
                    if (item.path) { window.location.href = ctx + item.path; }
                    else if (item.action === 'reload') { location.reload(); }
                    else if (item.cmd) { window.location.href = ctx + '/console'; }
                  }
                  function openPalette() {
                    palette.classList.add('open');
                    input.value = '';
                    render('');
                    setTimeout(function(){input.focus();}, 50);
                  }
                  function closePalette() { palette.classList.remove('open'); }

                  document.addEventListener('keydown', function(e) {
                    if ((e.ctrlKey || e.metaKey) && e.key === 'k') { e.preventDefault(); openPalette(); }
                    if (e.key === 'Escape') closePalette();
                  });
                  var searchBox = document.getElementById('globalSearch');
                  if (searchBox) searchBox.addEventListener('focus', function(e){ e.target.blur(); openPalette(); });
                  input.addEventListener('input', function(){ render(input.value); });
                  input.addEventListener('keydown', function(e) {
                    var children = results.children;
                    if (e.key === 'ArrowDown') { e.preventDefault(); selected = Math.min(selected+1, children.length-1); highlight(); }
                    else if (e.key === 'ArrowUp') { e.preventDefault(); selected = Math.max(selected-1, 0); highlight(); }
                    else if (e.key === 'Enter') {
                      var filtered = items.filter(function(i){
                        var q = input.value.toLowerCase();
                        return !q || i.name.toLowerCase().includes(q) || (i.cmd && i.cmd.includes(q));
                      });
                      if (filtered[selected]) activate(filtered[selected]);
                    }
                  });
                  palette.addEventListener('click', function(e){ if(e.target===palette) closePalette(); });
                })();
                </script>
                """.formatted(contextPath);
    }

    public static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
