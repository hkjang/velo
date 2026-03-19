const app = document.getElementById("app");
const statusNode = document.querySelector("[data-sample-status]");
const noteNode = document.querySelector("[data-sample-note]");
const numberFormat = new Intl.NumberFormat("ko-KR");

const state = {
  dashboard: null,
  showHealthyOnly: false,
  sortDescending: true,
  logEntries: ["Vanilla runtime attached"]
};

function setStatus(status, note) {
  statusNode.textContent = status;
  statusNode.setAttribute("data-sample-status", status);
  noteNode.textContent = note;
}

function appendLog(message) {
  const stamp = new Date().toLocaleTimeString("ko-KR");
  state.logEntries = [`${stamp} ${message}`, ...state.logEntries].slice(0, 6);
}

function visibleServices() {
  if (!state.dashboard) {
    return [];
  }
  const filtered = state.dashboard.services.filter((service) => !state.showHealthyOnly || service.healthy);
  return filtered.sort((left, right) => {
    const delta = left.latencyMs - right.latencyMs;
    return state.sortDescending ? -delta : delta;
  });
}

function summary() {
  const services = state.dashboard.services;
  const healthyCount = services.filter((service) => service.healthy).length;
  const deploysToday = services.reduce((sum, service) => sum + service.deploysToday, 0);
  const latencyAverage = Math.round(services.reduce((sum, service) => sum + service.latencyMs, 0) / services.length);
  return {
    totalServices: services.length,
    healthyCount,
    deploysToday,
    latencyAverage
  };
}

function serviceCard(service) {
  return `
    <article class="service-card">
      <header>
        <div>
          <h3>${service.name}</h3>
          <div class="metric-detail">${service.owner} · ${service.region}</div>
        </div>
        <span class="badge ${service.healthy ? "healthy" : "degraded"}">${service.healthy ? "healthy" : "degraded"}</span>
      </header>
      <ul class="service-meta">
        <li><span>Latency</span><strong>${numberFormat.format(service.latencyMs)} ms</strong></li>
        <li><span>Error Rate</span><strong>${service.errorRate.toFixed(2)}%</strong></li>
        <li><span>Deploys Today</span><strong>${numberFormat.format(service.deploysToday)}</strong></li>
      </ul>
    </article>
  `;
}

function render() {
  if (!state.dashboard) {
    app.innerHTML = `<div class="loading">대시보드 데이터를 읽는 중이다.</div>`;
    return;
  }

  const totals = summary();
  const services = visibleServices();
  app.innerHTML = `
    <div class="toolbar">
      <button type="button" data-action="toggleHealthy">${state.showHealthyOnly ? "전체 서비스 보기" : "정상 서비스만 보기"}</button>
      <button type="button" class="secondary" data-action="toggleSort">${state.sortDescending ? "지연시간 오름차순" : "지연시간 내림차순"}</button>
      <span class="caption">팀 ${state.dashboard.team} · ${new Date(state.dashboard.generatedAt).toLocaleString("ko-KR")}</span>
    </div>
    <div class="cards">
      <article class="metric-card">
        <h2>Services</h2>
        <div class="metric-value">${totals.totalServices}</div>
        <div class="metric-detail">전체 서비스 수</div>
      </article>
      <article class="metric-card">
        <h2>Healthy</h2>
        <div class="metric-value">${totals.healthyCount}</div>
        <div class="metric-detail">정상 상태 인스턴스</div>
      </article>
      <article class="metric-card">
        <h2>Avg Latency</h2>
        <div class="metric-value">${numberFormat.format(totals.latencyAverage)} ms</div>
        <div class="metric-detail">서비스 평균 응답 시간</div>
      </article>
      <article class="metric-card">
        <h2>Deploys</h2>
        <div class="metric-value">${numberFormat.format(totals.deploysToday)}</div>
        <div class="metric-detail">오늘 배포 횟수</div>
      </article>
    </div>
    <div class="dashboard-grid">
      <section class="panel">
        <h2>Service Radar</h2>
        <div class="service-grid">${services.map(serviceCard).join("")}</div>
      </section>
      <aside class="panel">
        <h2>Alerts</h2>
        <ul class="alert-list">
          ${state.dashboard.alerts.map((alert) => `<li><span>${alert.level.toUpperCase()}</span><strong>${alert.message}</strong></li>`).join("")}
        </ul>
        <h2 style="margin-top: 22px;">Activity</h2>
        <ul class="log-list">
          ${state.logEntries.map((entry) => `<li><span>${entry}</span></li>`).join("")}
        </ul>
      </aside>
    </div>
  `;
}

app.addEventListener("click", (event) => {
  const target = event.target.closest("[data-action]");
  if (!target || !state.dashboard) {
    return;
  }
  if (target.dataset.action === "toggleHealthy") {
    state.showHealthyOnly = !state.showHealthyOnly;
    appendLog(state.showHealthyOnly ? "healthy 서비스만 보기 활성화" : "전체 서비스 보기로 복귀");
  }
  if (target.dataset.action === "toggleSort") {
    state.sortDescending = !state.sortDescending;
    appendLog(state.sortDescending ? "지연시간 내림차순 정렬" : "지연시간 오름차순 정렬");
  }
  render();
});

async function bootstrap() {
  setStatus("LOADING", "dashboard.json fetch 중");
  render();
  const response = await fetch("./data/dashboard.json", { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`dashboard fetch failed: ${response.status}`);
  }
  state.dashboard = await response.json();
  appendLog(`${state.dashboard.services.length}개 서비스를 로드했다`);
  setStatus("READY", "Vanilla JavaScript 렌더링 완료");
  render();
}

bootstrap();
