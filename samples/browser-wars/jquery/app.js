(function () {
  const $app = $("#app");
  const $status = $("[data-sample-status]");
  const $note = $("[data-sample-note]");
  const numberFormat = new Intl.NumberFormat("ko-KR");

  const state = {
    dashboard: null,
    showHealthyOnly: false,
    sortDescending: true,
    logEntries: ["jQuery runtime attached"]
  };

  function setStatus(status, note) {
    $status.text(status).attr("data-sample-status", status);
    $note.text(note);
  }

  function appendLog(message) {
    const stamp = new Date().toLocaleTimeString("ko-KR");
    state.logEntries = [`${stamp} ${message}`].concat(state.logEntries).slice(0, 6);
  }

  function visibleServices() {
    if (!state.dashboard) {
      return [];
    }
    return state.dashboard.services
      .filter((service) => !state.showHealthyOnly || service.healthy)
      .sort((left, right) => {
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

  function render() {
    if (!state.dashboard) {
      $app.html('<div class="loading">jQuery 대시보드를 초기화하는 중이다.</div>');
      return;
    }

    const totals = summary();
    const servicesHtml = visibleServices().map((service) => `
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
    `).join("");

    const alertsHtml = state.dashboard.alerts.map((alert) => `
      <li><span>${alert.level.toUpperCase()}</span><strong>${alert.message}</strong></li>
    `).join("");

    const logHtml = state.logEntries.map((entry) => `<li><span>${entry}</span></li>`).join("");

    $app.html(`
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
          <h2>jQuery Service Radar</h2>
          <div class="service-grid">${servicesHtml}</div>
        </section>
        <aside class="panel">
          <h2>Alerts</h2>
          <ul class="alert-list">${alertsHtml}</ul>
          <h2 style="margin-top: 22px;">Activity</h2>
          <ul class="log-list">${logHtml}</ul>
        </aside>
      </div>
    `);
  }

  $app.on("click", "[data-action]", function () {
    if (!state.dashboard) {
      return;
    }
    const action = $(this).data("action");
    if (action === "toggleHealthy") {
      state.showHealthyOnly = !state.showHealthyOnly;
      appendLog(state.showHealthyOnly ? "정상 서비스 필터 활성화" : "정상 서비스 필터 해제");
    }
    if (action === "toggleSort") {
      state.sortDescending = !state.sortDescending;
      appendLog(state.sortDescending ? "지연시간 내림차순 정렬" : "지연시간 오름차순 정렬");
    }
    render();
  });

  function bootstrap() {
    setStatus("LOADING", "jQuery getJSON 호출 중");
    render();
    $.getJSON("./data/dashboard.json")
      .done((dashboard) => {
        state.dashboard = dashboard;
        appendLog(`${dashboard.services.length}개 서비스를 jQuery DOM으로 구성했다`);
        setStatus("READY", "jQuery 렌더링 완료");
        render();
      })
      .fail((jqxhr) => {
        throw new Error(`dashboard fetch failed: ${jqxhr.status}`);
      });
  }

  bootstrap();
})();
