package io.velo.was.webadmin.servlet.page;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.servlet.AdminPageLayout;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * System settings page.
 * Web Admin self-management: theme, session policy, license, configuration editor, and i18n.
 */
public class SettingsPageServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public SettingsPageServlet(ServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        ServerConfiguration.Server server = configuration.getServer();
        String ctx = req.getContextPath();

        String body = """
                <div class="page-header">
                  <div>
                    <div class="page-title" data-i18n="settings.title">Settings</div>
                    <div class="page-subtitle" data-i18n="settings.subtitle">Web Admin console configuration and preferences</div>
                  </div>
                </div>

                <div class="tabs" id="setTabs">
                  <div class="tab active" data-tab="general" data-i18n="settings.tab.general">General</div>
                  <div class="tab" data-tab="config" data-i18n="settings.tab.config">Configuration</div>
                  <div class="tab" data-tab="appearance" data-i18n="settings.tab.appearance">Appearance</div>
                  <div class="tab" data-tab="notifications" data-i18n="settings.tab.notifications">Notifications</div>
                  <div class="tab" data-tab="about" data-i18n="settings.tab.about">About</div>
                </div>

                <!-- General Tab -->
                <div class="tab-panel active" id="tab-general">
                  <div class="card">
                    <div class="card-title" data-i18n="settings.general.webAdminConfig">Web Admin Configuration</div>
                    <table class="info-table" style="margin-top:12px;">
                      <tr><td data-i18n="settings.general.contextPath">Context Path</td><td><code>%s</code></td></tr>
                      <tr><td data-i18n="settings.general.status">Status</td><td><span class="badge badge-success" data-i18n="settings.general.enabled">Enabled</span></td></tr>
                      <tr><td data-i18n="settings.general.sessionTimeout">Session Timeout</td><td>%d <span data-i18n="settings.general.seconds">seconds</span></td></tr>
                    </table>
                  </div>
                  <div class="card" style="margin-top:16px;">
                    <div class="card-title" data-i18n="settings.general.preferences">Preferences</div>
                    <div class="form-group" style="margin-top:12px;">
                      <label class="form-label" data-i18n="settings.general.language">Language</label>
                      <select class="form-select" id="langSelect" style="width:200px;">
                        <option value="ko" selected>Korean (한국어)</option>
                        <option value="en">English</option>
                      </select>
                    </div>
                    <div class="form-group">
                      <label class="form-label" data-i18n="settings.general.autoRefresh">Auto-refresh Interval</label>
                      <select class="form-select" id="refreshSelect" style="width:200px;">
                        <option>3 seconds</option>
                        <option selected>5 seconds</option>
                        <option>10 seconds</option>
                        <option>30 seconds</option>
                        <option>Disabled</option>
                      </select>
                    </div>
                    <div class="form-group">
                      <label class="form-label" data-i18n="settings.general.defaultPage">Default Page</label>
                      <select class="form-select" id="defaultPageSelect" style="width:200px;">
                        <option selected>Dashboard</option>
                        <option>Servers</option>
                        <option>Monitoring</option>
                        <option>Console</option>
                      </select>
                    </div>
                  </div>
                </div>

                <!-- Configuration Tab -->
                <div class="tab-panel" id="tab-config">
                  <div class="card">
                    <div class="card-title" data-i18n="settings.config.yamlEditor">YAML Configuration Editor</div>
                    <div style="display:flex;gap:8px;margin:12px 0;">
                      <button class="btn btn-primary" onclick="validateYaml()" data-i18n="settings.config.validate">Validate</button>
                      <button class="btn btn-primary" onclick="saveConfigDraft()" data-i18n="settings.config.saveDraft">Save as Draft</button>
                      <button class="btn btn-secondary" onclick="toggleCompare()" data-i18n="settings.config.compare">Compare</button>
                      <button class="btn btn-secondary" onclick="loadConfig()" data-i18n="settings.config.reload">Reload</button>
                    </div>
                    <div id="configValidation" style="display:none;padding:8px 12px;border-radius:var(--radius-sm);margin-bottom:8px;font-size:13px;"></div>
                    <div id="configEditorWrap" style="display:flex;gap:12px;">
                      <div style="flex:1;display:flex;flex-direction:column;">
                        <div style="font-size:12px;color:var(--text2);margin-bottom:4px;" data-i18n="settings.config.editLabel">Edit</div>
                        <textarea id="configEditor" spellcheck="false" style="
                          flex:1;min-height:420px;width:100%%;padding:14px;
                          font-family:'JetBrains Mono','Fira Code','Cascadia Code',Consolas,monospace;
                          font-size:13px;line-height:1.6;tab-size:2;
                          background:#0d1017;color:#e6e1cf;border:1px solid var(--border);
                          border-radius:var(--radius-sm);resize:vertical;
                          white-space:pre;overflow-wrap:normal;overflow-x:auto;
                        "></textarea>
                      </div>
                      <div id="configComparePane" style="flex:1;display:none;flex-direction:column;">
                        <div style="font-size:12px;color:var(--text2);margin-bottom:4px;" data-i18n="settings.config.currentLabel">Current (server)</div>
                        <div id="configDiff" style="
                          flex:1;min-height:420px;width:100%%;padding:14px;
                          font-family:'JetBrains Mono','Fira Code','Cascadia Code',Consolas,monospace;
                          font-size:13px;line-height:1.6;
                          background:#0d1017;color:#e6e1cf;border:1px solid var(--border);
                          border-radius:var(--radius-sm);overflow:auto;white-space:pre;
                        "></div>
                      </div>
                    </div>
                    <div class="form-hint" style="margin-top:8px;" data-i18n="settings.config.hint">Changes are saved as drafts. Go to History &gt; Drafts to review, approve, and apply.</div>
                  </div>
                </div>

                <!-- Appearance Tab -->
                <div class="tab-panel" id="tab-appearance">
                  <div class="card">
                    <div class="card-title" data-i18n="settings.appearance.theme">Theme</div>
                    <div style="display:flex;gap:16px;margin-top:16px;">
                      <div id="themeDark" style="padding:16px 24px;border-radius:8px;border:2px solid var(--primary);
                           background:#0f1117;color:#e4e4e7;cursor:pointer;text-align:center;" onclick="setTheme('dark')">
                        <div style="font-weight:600;" data-i18n="settings.appearance.dark">Dark</div>
                        <div style="font-size:12px;color:#9ca3af;" id="themeDarkLabel">Current</div>
                      </div>
                      <div id="themeLight" style="padding:16px 24px;border-radius:8px;border:1px solid var(--border);
                           background:#f8f9fa;color:#1a1d27;cursor:pointer;text-align:center;" onclick="setTheme('light')">
                        <div style="font-weight:600;" data-i18n="settings.appearance.light">Light</div>
                        <div style="font-size:12px;" id="themeLightLabel"></div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Notifications Tab -->
                <div class="tab-panel" id="tab-notifications">
                  <div class="card">
                    <div class="card-title">Notification Preferences</div>
                    <p style="color:var(--text2);font-size:13px;margin:12px 0;">Configure how you receive alerts and notifications from the admin console.</p>
                    <div style="display:flex;flex-direction:column;gap:16px;margin-top:16px;">
                      <div style="display:flex;justify-content:space-between;align-items:center;padding:12px;background:var(--surface2);border-radius:8px;">
                        <div>
                          <div style="font-weight:600;font-size:14px;">Browser Notifications</div>
                          <div style="font-size:12px;color:var(--text2);margin-top:2px;">Show desktop notifications for critical alerts</div>
                        </div>
                        <label style="position:relative;display:inline-block;width:44px;height:24px;">
                          <input type="checkbox" id="notifBrowser" onchange="saveNotifPrefs()" style="opacity:0;width:0;height:0;">
                          <span style="position:absolute;cursor:pointer;top:0;left:0;right:0;bottom:0;background:var(--surface3);border-radius:12px;transition:0.3s;" class="toggle-slider"></span>
                        </label>
                      </div>
                      <div style="display:flex;justify-content:space-between;align-items:center;padding:12px;background:var(--surface2);border-radius:8px;">
                        <div>
                          <div style="font-weight:600;font-size:14px;">Sound Alerts</div>
                          <div style="font-size:12px;color:var(--text2);margin-top:2px;">Play a sound when critical alerts occur</div>
                        </div>
                        <label style="position:relative;display:inline-block;width:44px;height:24px;">
                          <input type="checkbox" id="notifSound" onchange="saveNotifPrefs()" style="opacity:0;width:0;height:0;">
                          <span style="position:absolute;cursor:pointer;top:0;left:0;right:0;bottom:0;background:var(--surface3);border-radius:12px;transition:0.3s;" class="toggle-slider"></span>
                        </label>
                      </div>
                      <div style="display:flex;justify-content:space-between;align-items:center;padding:12px;background:var(--surface2);border-radius:8px;">
                        <div>
                          <div style="font-weight:600;font-size:14px;">Auto-refresh Dashboard</div>
                          <div style="font-size:12px;color:var(--text2);margin-top:2px;">Automatically refresh dashboard every 10 seconds</div>
                        </div>
                        <label style="position:relative;display:inline-block;width:44px;height:24px;">
                          <input type="checkbox" id="notifAutoRefresh" checked onchange="saveNotifPrefs()" style="opacity:0;width:0;height:0;">
                          <span style="position:absolute;cursor:pointer;top:0;left:0;right:0;bottom:0;background:var(--surface3);border-radius:12px;transition:0.3s;" class="toggle-slider"></span>
                        </label>
                      </div>
                    </div>
                  </div>
                  <div class="card" style="margin-top:16px;">
                    <div class="card-title">Alert Severity Filter</div>
                    <p style="color:var(--text2);font-size:13px;margin:12px 0;">Choose which severity levels trigger notifications.</p>
                    <div style="display:flex;gap:12px;margin-top:12px;">
                      <label style="display:flex;align-items:center;gap:6px;padding:8px 16px;background:var(--danger-bg);border:1px solid var(--danger);border-radius:8px;cursor:pointer;">
                        <input type="checkbox" id="notifCritical" checked onchange="saveNotifPrefs()"> <span style="color:var(--danger);font-weight:600;">Critical</span>
                      </label>
                      <label style="display:flex;align-items:center;gap:6px;padding:8px 16px;background:var(--warning-bg);border:1px solid var(--warning);border-radius:8px;cursor:pointer;">
                        <input type="checkbox" id="notifWarning" checked onchange="saveNotifPrefs()"> <span style="color:var(--warning);font-weight:600;">Warning</span>
                      </label>
                      <label style="display:flex;align-items:center;gap:6px;padding:8px 16px;background:var(--info-bg);border:1px solid var(--info);border-radius:8px;cursor:pointer;">
                        <input type="checkbox" id="notifInfo" onchange="saveNotifPrefs()"> <span style="color:var(--info);font-weight:600;">Info</span>
                      </label>
                    </div>
                  </div>
                  <div class="card" style="margin-top:16px;">
                    <div class="card-title">Notification History Retention</div>
                    <div style="display:flex;align-items:center;gap:12px;margin-top:12px;">
                      <span style="font-size:13px;color:var(--text2);">Keep notifications for:</span>
                      <select class="form-input" id="notifRetention" onchange="saveNotifPrefs()" style="width:160px;">
                        <option value="1">1 hour</option>
                        <option value="6">6 hours</option>
                        <option value="24" selected>24 hours</option>
                        <option value="168">7 days</option>
                      </select>
                      <button class="btn btn-sm btn-danger" onclick="if(confirm('Clear all notification history?')){localStorage.removeItem('velo-notif-history');showToast('Notification history cleared','success');}">Clear History</button>
                    </div>
                  </div>
                </div>

                <!-- About Tab -->
                <div class="tab-panel" id="tab-about">
                  <div class="card">
                    <div class="card-title" data-i18n="settings.about.title">About Velo Web Admin</div>
                    <table class="info-table" style="margin-top:12px;">
                      <tr><td data-i18n="settings.about.product">Product</td><td>Velo WAS</td></tr>
                      <tr><td data-i18n="settings.about.version">Version</td><td>0.1.0-SNAPSHOT</td></tr>
                      <tr><td data-i18n="settings.about.module">Web Admin Module</td><td>was-webadmin</td></tr>
                      <tr><td>Java</td><td>%s</td></tr>
                      <tr><td>OS</td><td>%s %s (%s)</td></tr>
                      <tr><td data-i18n="settings.about.servletCompat">Servlet Compatibility</td><td>Jakarta Servlet 6.1</td></tr>
                      <tr><td data-i18n="settings.about.transport">Transport</td><td>Netty</td></tr>
                    </table>
                  </div>

                  <div class="card" style="margin-top:16px;">
                    <div class="card-title" data-i18n="settings.about.systemInfo">System Information</div>
                    <table class="info-table" style="margin-top:12px;" id="systemInfoTable">
                      <tr><td data-i18n="settings.about.loading" colspan="2">Loading system information...</td></tr>
                    </table>
                  </div>

                  <div class="card" style="margin-top:16px;">
                    <div class="card-title" data-i18n="settings.about.jvmInfo">JVM Information</div>
                    <table class="info-table" style="margin-top:12px;" id="jvmInfoTable">
                      <tr><td data-i18n="settings.about.loading" colspan="2">Loading JVM information...</td></tr>
                    </table>
                  </div>
                </div>

                <style>
                  .toggle-slider::before {
                    position:absolute;content:"";height:18px;width:18px;left:3px;bottom:3px;
                    background:white;border-radius:50%%;transition:0.3s;
                  }
                  input:checked + .toggle-slider { background:var(--primary); }
                  input:checked + .toggle-slider::before { transform:translateX(20px); }
                </style>
                <script>
                var CTX = '%s';

                // ===== Notification Preferences =====
                function loadNotifPrefs() {
                  try {
                    var prefs = JSON.parse(localStorage.getItem('velo-notif-prefs') || '{}');
                    if (prefs.browser) document.getElementById('notifBrowser').checked = true;
                    if (prefs.sound) document.getElementById('notifSound').checked = true;
                    if (prefs.autoRefresh !== false) document.getElementById('notifAutoRefresh').checked = true;
                    else document.getElementById('notifAutoRefresh').checked = false;
                    if (prefs.critical !== false) document.getElementById('notifCritical').checked = true;
                    if (prefs.warning !== false) document.getElementById('notifWarning').checked = true;
                    if (prefs.info) document.getElementById('notifInfo').checked = true;
                    if (prefs.retention) document.getElementById('notifRetention').value = prefs.retention;
                  } catch(e) {}
                }
                function saveNotifPrefs() {
                  var prefs = {
                    browser: document.getElementById('notifBrowser').checked,
                    sound: document.getElementById('notifSound').checked,
                    autoRefresh: document.getElementById('notifAutoRefresh').checked,
                    critical: document.getElementById('notifCritical').checked,
                    warning: document.getElementById('notifWarning').checked,
                    info: document.getElementById('notifInfo').checked,
                    retention: document.getElementById('notifRetention').value
                  };
                  localStorage.setItem('velo-notif-prefs', JSON.stringify(prefs));
                  showToast('Notification preferences saved', 'success');
                  if (prefs.browser && Notification && Notification.permission !== 'granted') {
                    Notification.requestPermission();
                  }
                }
                loadNotifPrefs();

                // ===== I18N =====
                var I18N = {
                  en: {
                    'settings.title': 'Settings',
                    'settings.subtitle': 'Web Admin console configuration and preferences',
                    'settings.tab.general': 'General',
                    'settings.tab.config': 'Configuration',
                    'settings.tab.appearance': 'Appearance',
                    'settings.tab.notifications': 'Notifications',
                    'settings.tab.about': 'About',
                    'settings.general.webAdminConfig': 'Web Admin Configuration',
                    'settings.general.contextPath': 'Context Path',
                    'settings.general.status': 'Status',
                    'settings.general.enabled': 'Enabled',
                    'settings.general.sessionTimeout': 'Session Timeout',
                    'settings.general.seconds': 'seconds',
                    'settings.general.preferences': 'Preferences',
                    'settings.general.language': 'Language',
                    'settings.general.autoRefresh': 'Auto-refresh Interval',
                    'settings.general.defaultPage': 'Default Page',
                    'settings.config.yamlEditor': 'YAML Configuration Editor',
                    'settings.config.validate': 'Validate',
                    'settings.config.saveDraft': 'Save as Draft',
                    'settings.config.compare': 'Compare',
                    'settings.config.reload': 'Reload',
                    'settings.config.editLabel': 'Edit',
                    'settings.config.currentLabel': 'Current (server)',
                    'settings.config.hint': 'Changes are saved as drafts. Go to History > Drafts to review, approve, and apply.',
                    'settings.appearance.theme': 'Theme',
                    'settings.appearance.dark': 'Dark',
                    'settings.appearance.light': 'Light',
                    'settings.about.title': 'About Velo Web Admin',
                    'settings.about.product': 'Product',
                    'settings.about.version': 'Version',
                    'settings.about.module': 'Web Admin Module',
                    'settings.about.servletCompat': 'Servlet Compatibility',
                    'settings.about.transport': 'Transport',
                    'settings.about.systemInfo': 'System Information',
                    'settings.about.jvmInfo': 'JVM Information',
                    'settings.about.loading': 'Loading...',
                    'settings.about.heapMemory': 'Heap Memory',
                    'settings.about.nonHeapMemory': 'Non-Heap Memory',
                    'settings.about.cpuCores': 'Available CPU Cores',
                    'settings.about.uptime': 'Uptime',
                    'settings.about.processId': 'Process ID',
                    'settings.about.jvmName': 'JVM Name',
                    'settings.about.jvmVendor': 'JVM Vendor',
                    'settings.about.jvmVersion': 'JVM Version',
                    'settings.about.jvmArgs': 'JVM Arguments',
                    'settings.about.classpath': 'Classpath',
                    'settings.about.gcCollectors': 'GC Collectors',
                    'settings.about.threadCount': 'Thread Count',
                    'settings.toast.prefsSaved': 'Preferences saved',
                    'settings.toast.themeChanged': 'Theme changed to',
                    'settings.toast.configLoaded': 'Configuration loaded',
                    'settings.toast.draftSaved': 'Draft saved successfully',
                    'settings.toast.validOk': 'YAML syntax looks valid',
                    'settings.toast.validFail': 'YAML syntax error'
                  },
                  ko: {
                    'settings.title': '설정',
                    'settings.subtitle': '웹 관리 콘솔 구성 및 환경 설정',
                    'settings.tab.general': '일반',
                    'settings.tab.config': '구성',
                    'settings.tab.appearance': '외관',
                    'settings.tab.notifications': '알림',
                    'settings.tab.about': '정보',
                    'settings.general.webAdminConfig': '웹 관리자 구성',
                    'settings.general.contextPath': '컨텍스트 경로',
                    'settings.general.status': '상태',
                    'settings.general.enabled': '활성화됨',
                    'settings.general.sessionTimeout': '세션 타임아웃',
                    'settings.general.seconds': '초',
                    'settings.general.preferences': '환경 설정',
                    'settings.general.language': '언어',
                    'settings.general.autoRefresh': '자동 새로고침 간격',
                    'settings.general.defaultPage': '기본 페이지',
                    'settings.config.yamlEditor': 'YAML 구성 편집기',
                    'settings.config.validate': '검증',
                    'settings.config.saveDraft': '초안 저장',
                    'settings.config.compare': '비교',
                    'settings.config.reload': '다시 불러오기',
                    'settings.config.editLabel': '편집',
                    'settings.config.currentLabel': '현재 (서버)',
                    'settings.config.hint': '변경 사항은 초안으로 저장됩니다. 기록 > 초안에서 검토, 승인 및 적용하세요.',
                    'settings.appearance.theme': '테마',
                    'settings.appearance.dark': '다크',
                    'settings.appearance.light': '라이트',
                    'settings.about.title': 'Velo 웹 관리자 정보',
                    'settings.about.product': '제품',
                    'settings.about.version': '버전',
                    'settings.about.module': '웹 관리자 모듈',
                    'settings.about.servletCompat': '서블릿 호환성',
                    'settings.about.transport': '전송 계층',
                    'settings.about.systemInfo': '시스템 정보',
                    'settings.about.jvmInfo': 'JVM 정보',
                    'settings.about.loading': '로딩 중...',
                    'settings.about.heapMemory': '힙 메모리',
                    'settings.about.nonHeapMemory': '비힙 메모리',
                    'settings.about.cpuCores': '사용 가능한 CPU 코어',
                    'settings.about.uptime': '가동 시간',
                    'settings.about.processId': '프로세스 ID',
                    'settings.about.jvmName': 'JVM 이름',
                    'settings.about.jvmVendor': 'JVM 공급업체',
                    'settings.about.jvmVersion': 'JVM 버전',
                    'settings.about.jvmArgs': 'JVM 인수',
                    'settings.about.classpath': '클래스패스',
                    'settings.about.gcCollectors': 'GC 수집기',
                    'settings.about.threadCount': '스레드 수',
                    'settings.toast.prefsSaved': '환경 설정이 저장되었습니다',
                    'settings.toast.themeChanged': '테마가 변경되었습니다:',
                    'settings.toast.configLoaded': '구성을 불러왔습니다',
                    'settings.toast.draftSaved': '초안이 저장되었습니다',
                    'settings.toast.validOk': 'YAML 구문이 유효합니다',
                    'settings.toast.validFail': 'YAML 구문 오류'
                  }
                };

                var currentLang = localStorage.getItem('velo-lang') || 'ko';

                function t(key) {
                  var lang = I18N[currentLang] || I18N['en'];
                  return lang[key] || (I18N['en'] && I18N['en'][key]) || key;
                }

                function applyI18n() {
                  document.querySelectorAll('[data-i18n]').forEach(function(el) {
                    var key = el.getAttribute('data-i18n');
                    var translated = t(key);
                    if (translated !== key) {
                      el.textContent = translated;
                    }
                  });
                }

                // ===== Tab Switching =====
                document.querySelectorAll('#setTabs .tab').forEach(function(tab){
                  tab.addEventListener('click', function(){
                    document.querySelectorAll('#setTabs .tab').forEach(function(t){t.classList.remove('active');});
                    document.querySelectorAll('.tab-panel').forEach(function(p){p.classList.remove('active');});
                    tab.classList.add('active');
                    document.getElementById('tab-' + tab.dataset.tab).classList.add('active');
                  });
                });

                // ===== Preferences =====
                var PREF_KEY = 'velo-admin-prefs';
                function loadPrefs() {
                  try {
                    var prefs = JSON.parse(localStorage.getItem(PREF_KEY) || '{}');
                    var langSel = document.getElementById('langSelect');
                    var refreshSel = document.getElementById('refreshSelect');
                    var pageSel = document.getElementById('defaultPageSelect');
                    if (prefs.language && langSel) langSel.value = prefs.language;
                    if (prefs.refreshInterval && refreshSel) refreshSel.value = prefs.refreshInterval;
                    if (prefs.defaultPage && pageSel) pageSel.value = prefs.defaultPage;
                    // Sync lang from localStorage
                    var savedLang = localStorage.getItem('velo-lang');
                    if (savedLang && langSel) langSel.value = savedLang;
                  } catch(e) {}
                }
                function savePrefs() {
                  var langSel = document.getElementById('langSelect');
                  var refreshSel = document.getElementById('refreshSelect');
                  var pageSel = document.getElementById('defaultPageSelect');
                  var prefs = {
                    language: langSel ? langSel.value : '',
                    refreshInterval: refreshSel ? refreshSel.value : '',
                    defaultPage: pageSel ? pageSel.value : ''
                  };
                  localStorage.setItem(PREF_KEY, JSON.stringify(prefs));
                  showToast(t('settings.toast.prefsSaved'), 'success');
                }
                document.querySelectorAll('#tab-general .form-select').forEach(function(sel){
                  sel.addEventListener('change', function() {
                    if (sel.id === 'langSelect') {
                      currentLang = sel.value;
                      localStorage.setItem('velo-lang', currentLang);
                      applyI18n();
                      if (typeof applyGlobalI18n === 'function') applyGlobalI18n();
                    }
                    savePrefs();
                  });
                });
                loadPrefs();

                // ===== Configuration Editor =====
                var originalConfig = '';

                function loadConfig() {
                  fetch(CTX + '/api/config').then(function(r){ return r.text(); }).then(function(txt){
                    originalConfig = txt;
                    document.getElementById('configEditor').value = txt;
                    showToast(t('settings.toast.configLoaded'), 'success');
                  }).catch(function(e){
                    document.getElementById('configEditor').value = '# Failed to load configuration\\n# ' + e.message;
                  });
                }

                function validateYaml() {
                  var text = document.getElementById('configEditor').value;
                  var lines = text.split('\\n');
                  var errors = [];
                  var indentStack = [];

                  for (var i = 0; i < lines.length; i++) {
                    var line = lines[i];
                    var trimmed = line.trim();

                    // Skip empty lines and comments
                    if (trimmed === '' || trimmed.charAt(0) === '#') continue;

                    // Check indentation consistency (spaces not tabs)
                    if (line.length > 0 && line.charAt(0) !== ' ' && line.charAt(0) !== '-' && trimmed !== line.trim()) {
                      // pass
                    }
                    var leadingSpaces = line.length - line.replace(/^ +/, '').length;
                    if (line.indexOf('\\t') !== -1) {
                      errors.push('Line ' + (i+1) + ': tabs are not allowed in YAML, use spaces');
                    }

                    // Check list items
                    if (trimmed.charAt(0) === '-') continue;

                    // Check key-value pairs
                    var colonIdx = trimmed.indexOf(':');
                    if (colonIdx === -1) {
                      // Not a key-value, not a list item, not a comment, not blank => likely a continuation or multiline value
                      // Only flag if it looks like an intended key
                      if (/^[a-zA-Z_][a-zA-Z0-9_.-]*[^:]$/.test(trimmed)) {
                        errors.push('Line ' + (i+1) + ': missing colon for key \\"' + trimmed + '\\"');
                      }
                      continue;
                    }

                    var key = trimmed.substring(0, colonIdx).trim();
                    if (key === '' ) {
                      errors.push('Line ' + (i+1) + ': empty key before colon');
                    }
                    // Check for duplicate colons that look wrong
                    if (key.indexOf(' ') !== -1 && key.charAt(0) !== '"' && key.charAt(0) !== "'") {
                      errors.push('Line ' + (i+1) + ': key \\"' + key + '\\" contains spaces; quote it if intentional');
                    }
                  }

                  var box = document.getElementById('configValidation');
                  box.style.display = 'block';
                  if (errors.length === 0) {
                    box.style.background = 'var(--success-bg)';
                    box.style.color = 'var(--success)';
                    box.style.border = '1px solid var(--success)';
                    box.innerHTML = '&#10003; ' + t('settings.toast.validOk') + ' (' + lines.length + ' lines)';
                  } else {
                    box.style.background = 'var(--danger-bg)';
                    box.style.color = 'var(--danger)';
                    box.style.border = '1px solid var(--danger)';
                    box.innerHTML = '&#10007; ' + t('settings.toast.validFail') + ':<br>' + errors.join('<br>');
                  }
                  return errors.length === 0;
                }

                function saveConfigDraft() {
                  var content = document.getElementById('configEditor').value;
                  fetch(CTX + '/api/config/save', {
                    method: 'POST',
                    headers: { 'Content-Type': 'text/plain' },
                    body: content
                  }).then(function(r){
                    if (r.ok) {
                      showToast(t('settings.toast.draftSaved'), 'success');
                    } else {
                      r.text().then(function(msg){ showToast('Error: ' + msg, 'danger'); });
                    }
                  }).catch(function(e){
                    showToast('Error: ' + e.message, 'danger');
                  });
                }

                function toggleCompare() {
                  var pane = document.getElementById('configComparePane');
                  if (pane.style.display === 'none' || pane.style.display === '') {
                    pane.style.display = 'flex';
                    renderDiff();
                  } else {
                    pane.style.display = 'none';
                  }
                }

                function renderDiff() {
                  var current = originalConfig.split('\\n');
                  var edited = document.getElementById('configEditor').value.split('\\n');
                  var maxLen = Math.max(current.length, edited.length);
                  var html = '';

                  for (var i = 0; i < maxLen; i++) {
                    var orig = i < current.length ? current[i] : '';
                    var edit = i < edited.length ? edited[i] : '';
                    var lineNum = String(i + 1).padStart(4, ' ');

                    if (orig === edit) {
                      html += '<span style="color:#6b7280;">' + lineNum + '</span>  ' + escapeHtmlJs(orig) + '\\n';
                    } else if (i >= current.length) {
                      html += '<span style="color:#6b7280;">' + lineNum + '</span> <span style="background:rgba(34,197,94,0.15);color:#22c55e;">+ ' + escapeHtmlJs(edit) + '</span>\\n';
                    } else if (i >= edited.length) {
                      html += '<span style="color:#6b7280;">' + lineNum + '</span> <span style="background:rgba(239,68,68,0.15);color:#ef4444;">- ' + escapeHtmlJs(orig) + '</span>\\n';
                    } else {
                      html += '<span style="color:#6b7280;">' + lineNum + '</span> <span style="background:rgba(239,68,68,0.15);color:#ef4444;">- ' + escapeHtmlJs(orig) + '</span>\\n';
                      html += '<span style="color:#6b7280;">' + lineNum + '</span> <span style="background:rgba(34,197,94,0.15);color:#22c55e;">+ ' + escapeHtmlJs(edit) + '</span>\\n';
                    }
                  }
                  document.getElementById('configDiff').innerHTML = html;
                }

                function escapeHtmlJs(s) {
                  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
                }

                // Load config on tab click
                document.querySelector('[data-tab="config"]').addEventListener('click', function() {
                  if (!document.getElementById('configEditor').value) loadConfig();
                });

                // ===== About Tab: System & JVM Info =====
                function loadSystemInfo() {
                  fetch(CTX + '/api/system').then(function(r){ return r.json(); }).then(function(data){
                    var tbl = document.getElementById('systemInfoTable');
                    var html = '';
                    html += '<tr><td>' + t('settings.about.heapMemory') + '</td><td>' + (data.heapUsed || '-') + ' / ' + (data.heapMax || '-') + '</td></tr>';
                    html += '<tr><td>' + t('settings.about.nonHeapMemory') + '</td><td>' + (data.nonHeapUsed || '-') + '</td></tr>';
                    html += '<tr><td>' + t('settings.about.cpuCores') + '</td><td>' + (data.availableProcessors || '-') + '</td></tr>';
                    html += '<tr><td>' + t('settings.about.uptime') + '</td><td>' + (data.uptime || '-') + '</td></tr>';
                    html += '<tr><td>' + t('settings.about.processId') + '</td><td>' + (data.pid || '-') + '</td></tr>';
                    tbl.innerHTML = html;
                  }).catch(function(e){
                    document.getElementById('systemInfoTable').innerHTML = '<tr><td colspan="2" style="color:var(--text3);">Failed to load system info: ' + escapeHtmlJs(e.message) + '</td></tr>';
                  });
                }

                function loadJvmInfo() {
                  fetch(CTX + '/api/jvm').then(function(r){ return r.json(); }).then(function(data){
                    var tbl = document.getElementById('jvmInfoTable');
                    var html = '';
                    html += '<tr><td>' + t('settings.about.jvmName') + '</td><td>' + (data.vmName || '-') + '</td></tr>';
                    html += '<tr><td>' + t('settings.about.jvmVendor') + '</td><td>' + (data.vmVendor || '-') + '</td></tr>';
                    html += '<tr><td>' + t('settings.about.jvmVersion') + '</td><td>' + (data.vmVersion || '-') + '</td></tr>';
                    html += '<tr><td>' + t('settings.about.threadCount') + '</td><td>' + (data.threadCount || '-') + '</td></tr>';
                    html += '<tr><td>' + t('settings.about.gcCollectors') + '</td><td>' + (data.gcCollectors || '-') + '</td></tr>';
                    if (data.jvmArgs) {
                      var argsHtml = '<div style="max-height:120px;overflow:auto;font-family:monospace;font-size:12px;padding:6px;background:var(--surface);border-radius:var(--radius-xs);word-break:break-all;">' + escapeHtmlJs(data.jvmArgs) + '</div>';
                      html += '<tr><td>' + t('settings.about.jvmArgs') + '</td><td>' + argsHtml + '</td></tr>';
                    }
                    if (data.classpath) {
                      var cpHtml = '<div style="max-height:120px;overflow:auto;font-family:monospace;font-size:11px;padding:6px;background:var(--surface);border-radius:var(--radius-xs);word-break:break-all;">' + escapeHtmlJs(data.classpath) + '</div>';
                      html += '<tr><td>' + t('settings.about.classpath') + '</td><td>' + cpHtml + '</td></tr>';
                    }
                    tbl.innerHTML = html;
                  }).catch(function(e){
                    document.getElementById('jvmInfoTable').innerHTML = '<tr><td colspan="2" style="color:var(--text3);">Failed to load JVM info: ' + escapeHtmlJs(e.message) + '</td></tr>';
                  });
                }

                // Load About info when tab is clicked
                document.querySelector('[data-tab="about"]').addEventListener('click', function() {
                  loadSystemInfo();
                  loadJvmInfo();
                });

                // ===== Theme Switching =====
                var lightThemeCss = ':root { --bg: #f8f9fa; --bg2: #ffffff; --bg3: #e9ecef; --text: #1a1d27; --text2: #495057; --text3: #868e96; --border: #dee2e6; --primary: #6366f1; --primary-hover: #4f46e5; --success: #22c55e; --warning: #f59e0b; --danger: #ef4444; }';
                var lightStyleEl = null;

                function setTheme(theme) {
                  localStorage.setItem('velo-theme', theme);
                  applyTheme(theme);
                  showToast(t('settings.toast.themeChanged') + ' ' + theme, 'success');
                }

                function applyTheme(theme) {
                  if (theme === 'light') {
                    if (!lightStyleEl) {
                      lightStyleEl = document.createElement('style');
                      lightStyleEl.id = 'light-theme';
                      lightStyleEl.textContent = lightThemeCss;
                    }
                    document.head.appendChild(lightStyleEl);
                    document.getElementById('themeDark').style.border = '1px solid var(--border)';
                    document.getElementById('themeLight').style.border = '2px solid var(--primary)';
                    document.getElementById('themeDarkLabel').textContent = '';
                    document.getElementById('themeLightLabel').textContent = 'Current';
                  } else {
                    if (lightStyleEl && lightStyleEl.parentNode) lightStyleEl.parentNode.removeChild(lightStyleEl);
                    document.getElementById('themeDark').style.border = '2px solid var(--primary)';
                    document.getElementById('themeLight').style.border = '1px solid var(--border)';
                    document.getElementById('themeDarkLabel').textContent = 'Current';
                    document.getElementById('themeLightLabel').textContent = '';
                  }
                }

                var savedTheme = localStorage.getItem('velo-theme') || 'dark';
                applyTheme(savedTheme);

                // Apply i18n on initial load
                applyI18n();
                </script>
                """.formatted(
                AdminPageLayout.escapeHtml(server.getWebAdmin().getContextPath()),
                server.getSession().getTimeoutSeconds(),
                System.getProperty("java.version"),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                AdminPageLayout.escapeHtml(ctx)
        );

        resp.getWriter().write(AdminPageLayout.page("Settings", server.getName(), server.getNodeId(),
                ctx, "settings", body));
    }
}
