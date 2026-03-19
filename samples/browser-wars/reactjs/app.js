(function () {
  const html = htm.bind(React.createElement);
  const statusNode = document.querySelector("[data-sample-status]");
  const noteNode = document.querySelector("[data-sample-note]");
  const numberFormat = new Intl.NumberFormat("ko-KR");

  function setStatus(status, note) {
    statusNode.textContent = status;
    statusNode.setAttribute("data-sample-status", status);
    noteNode.textContent = note;
  }

  function summarize(services) {
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

  function MetricCard(props) {
    return html`
      <article className="metric-card">
        <h2>${props.label}</h2>
        <div className="metric-value">${props.value}</div>
        <div className="metric-detail">${props.detail}</div>
      </article>
    `;
  }

  function ServiceCard(props) {
    const service = props.service;
    return html`
      <article className="service-card">
        <header>
          <div>
            <h3>${service.name}</h3>
            <div className="metric-detail">${service.owner} · ${service.region}</div>
          </div>
          <span className=${`badge ${service.healthy ? "healthy" : "degraded"}`}>${service.healthy ? "healthy" : "degraded"}</span>
        </header>
        <ul className="service-meta">
          <li><span>Latency</span><strong>${numberFormat.format(service.latencyMs)} ms</strong></li>
          <li><span>Error Rate</span><strong>${service.errorRate.toFixed(2)}%</strong></li>
          <li><span>Deploys Today</span><strong>${numberFormat.format(service.deploysToday)}</strong></li>
        </ul>
      </article>
    `;
  }

  function App() {
    const [dashboard, setDashboard] = React.useState(null);
    const [showHealthyOnly, setShowHealthyOnly] = React.useState(false);
    const [sortDescending, setSortDescending] = React.useState(true);
    const [logEntries, setLogEntries] = React.useState(["React runtime attached"]);

    const appendLog = React.useCallback((message) => {
      const stamp = new Date().toLocaleTimeString("ko-KR");
      setLogEntries((current) => [`${stamp} ${message}`, ...current].slice(0, 6));
    }, []);

    React.useEffect(() => {
      let active = true;
      setStatus("LOADING", "React fetch + render 준비 중");
      fetch("./data/dashboard.json", { cache: "no-store" })
        .then((response) => {
          if (!response.ok) {
            throw new Error(`dashboard fetch failed: ${response.status}`);
          }
          return response.json();
        })
        .then((payload) => {
          if (!active) {
            return;
          }
          setDashboard(payload);
          appendLog(`${payload.services.length}개 서비스를 React 상태로 로드했다`);
          setStatus("READY", "React 렌더링 완료");
        })
        .catch((error) => {
          setStatus("ERROR", error.message || "React bootstrap failed");
          throw error;
        });
      return () => {
        active = false;
      };
    }, [appendLog]);

    const services = React.useMemo(() => {
      if (!dashboard) {
        return [];
      }
      return dashboard.services
        .filter((service) => !showHealthyOnly || service.healthy)
        .sort((left, right) => {
          const delta = left.latencyMs - right.latencyMs;
          return sortDescending ? -delta : delta;
        });
    }, [dashboard, showHealthyOnly, sortDescending]);

    if (!dashboard) {
      return html`<div className="loading">React가 대시보드를 초기화하는 중이다.</div>`;
    }

    const totals = summarize(dashboard.services);
    return html`
      <div>
        <div className="toolbar">
          <button type="button" onClick=${() => {
            setShowHealthyOnly((value) => {
              appendLog(!value ? "정상 서비스 필터 활성화" : "정상 서비스 필터 해제");
              return !value;
            });
          }}>${showHealthyOnly ? "전체 서비스 보기" : "정상 서비스만 보기"}</button>
          <button type="button" className="secondary" onClick=${() => {
            setSortDescending((value) => {
              appendLog(value ? "지연시간 오름차순 정렬" : "지연시간 내림차순 정렬");
              return !value;
            });
          }}>${sortDescending ? "지연시간 오름차순" : "지연시간 내림차순"}</button>
          <span className="caption">팀 ${dashboard.team} · ${new Date(dashboard.generatedAt).toLocaleString("ko-KR")}</span>
        </div>
        <div className="cards">
          <${MetricCard} label="Services" value=${totals.totalServices} detail="전체 서비스 수" />
          <${MetricCard} label="Healthy" value=${totals.healthyCount} detail="정상 상태 인스턴스" />
          <${MetricCard} label="Avg Latency" value=${`${numberFormat.format(totals.latencyAverage)} ms`} detail="서비스 평균 응답 시간" />
          <${MetricCard} label="Deploys" value=${numberFormat.format(totals.deploysToday)} detail="오늘 배포 횟수" />
        </div>
        <div className="dashboard-grid">
          <section className="panel">
            <h2>React Service Radar</h2>
            <div className="service-grid">
              ${services.map((service) => html`<${ServiceCard} key=${service.name} service=${service} />`)}
            </div>
          </section>
          <aside className="panel">
            <h2>Alerts</h2>
            <ul className="alert-list">
              ${dashboard.alerts.map((alert, index) => html`<li key=${index}><span>${alert.level.toUpperCase()}</span><strong>${alert.message}</strong></li>`)}
            </ul>
            <h2 style=${{ marginTop: "22px" }}>Activity</h2>
            <ul className="log-list">
              ${logEntries.map((entry, index) => html`<li key=${index}><span>${entry}</span></li>`)}
            </ul>
          </aside>
        </div>
      </div>
    `;
  }

  ReactDOM.createRoot(document.getElementById("app")).render(html`<${App} />`);
})();
