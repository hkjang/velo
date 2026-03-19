(function () {
  const { createApp } = Vue;
  const statusNode = document.querySelector("[data-sample-status]");
  const noteNode = document.querySelector("[data-sample-note]");
  const numberFormat = new Intl.NumberFormat("ko-KR");

  function setStatus(status, note) {
    statusNode.textContent = status;
    statusNode.setAttribute("data-sample-status", status);
    noteNode.textContent = note;
  }

  createApp({
    template: `
      <div>
        <div v-if="!dashboard" class="loading">Vue 대시보드를 초기화하는 중이다.</div>
        <template v-else>
          <div class="toolbar">
            <button type="button" @click="toggleHealthy">{{ showHealthyOnly ? "전체 서비스 보기" : "정상 서비스만 보기" }}</button>
            <button type="button" class="secondary" @click="toggleSort">{{ sortDescending ? "지연시간 오름차순" : "지연시간 내림차순" }}</button>
            <span class="caption">팀 {{ dashboard.team }} · {{ formattedGeneratedAt }}</span>
          </div>
          <div class="cards">
            <article class="metric-card">
              <h2>Services</h2>
              <div class="metric-value">{{ summary.totalServices }}</div>
              <div class="metric-detail">전체 서비스 수</div>
            </article>
            <article class="metric-card">
              <h2>Healthy</h2>
              <div class="metric-value">{{ summary.healthyCount }}</div>
              <div class="metric-detail">정상 상태 인스턴스</div>
            </article>
            <article class="metric-card">
              <h2>Avg Latency</h2>
              <div class="metric-value">{{ formatNumber(summary.latencyAverage) }} ms</div>
              <div class="metric-detail">서비스 평균 응답 시간</div>
            </article>
            <article class="metric-card">
              <h2>Deploys</h2>
              <div class="metric-value">{{ formatNumber(summary.deploysToday) }}</div>
              <div class="metric-detail">오늘 배포 횟수</div>
            </article>
          </div>
          <div class="dashboard-grid">
            <section class="panel">
              <h2>Vue Service Radar</h2>
              <div class="service-grid">
                <article class="service-card" v-for="service in visibleServices" :key="service.name">
                  <header>
                    <div>
                      <h3>{{ service.name }}</h3>
                      <div class="metric-detail">{{ service.owner }} · {{ service.region }}</div>
                    </div>
                    <span :class="'badge ' + (service.healthy ? 'healthy' : 'degraded')">{{ service.healthy ? 'healthy' : 'degraded' }}</span>
                  </header>
                  <ul class="service-meta">
                    <li><span>Latency</span><strong>{{ formatNumber(service.latencyMs) }} ms</strong></li>
                    <li><span>Error Rate</span><strong>{{ service.errorRate.toFixed(2) }}%</strong></li>
                    <li><span>Deploys Today</span><strong>{{ formatNumber(service.deploysToday) }}</strong></li>
                  </ul>
                </article>
              </div>
            </section>
            <aside class="panel">
              <h2>Alerts</h2>
              <ul class="alert-list">
                <li v-for="alert in dashboard.alerts" :key="alert.message"><span>{{ alert.level.toUpperCase() }}</span><strong>{{ alert.message }}</strong></li>
              </ul>
              <h2 style="margin-top: 22px;">Activity</h2>
              <ul class="log-list">
                <li v-for="entry in logEntries" :key="entry"><span>{{ entry }}</span></li>
              </ul>
            </aside>
          </div>
        </template>
      </div>
    `,
    data() {
      return {
        dashboard: null,
        showHealthyOnly: false,
        sortDescending: true,
        logEntries: ["Vue runtime attached"]
      };
    },
    computed: {
      formattedGeneratedAt() {
        return this.dashboard ? new Date(this.dashboard.generatedAt).toLocaleString("ko-KR") : "";
      },
      summary() {
        if (!this.dashboard) {
          return { totalServices: 0, healthyCount: 0, deploysToday: 0, latencyAverage: 0 };
        }
        const services = this.dashboard.services;
        const healthyCount = services.filter((service) => service.healthy).length;
        const deploysToday = services.reduce((sum, service) => sum + service.deploysToday, 0);
        const latencyAverage = Math.round(services.reduce((sum, service) => sum + service.latencyMs, 0) / services.length);
        return {
          totalServices: services.length,
          healthyCount,
          deploysToday,
          latencyAverage
        };
      },
      visibleServices() {
        if (!this.dashboard) {
          return [];
        }
        return this.dashboard.services
          .filter((service) => !this.showHealthyOnly || service.healthy)
          .sort((left, right) => {
            const delta = left.latencyMs - right.latencyMs;
            return this.sortDescending ? -delta : delta;
          });
      }
    },
    methods: {
      formatNumber(value) {
        return numberFormat.format(value);
      },
      appendLog(message) {
        const stamp = new Date().toLocaleTimeString("ko-KR");
        this.logEntries = [`${stamp} ${message}`, ...this.logEntries].slice(0, 6);
      },
      toggleHealthy() {
        this.showHealthyOnly = !this.showHealthyOnly;
        this.appendLog(this.showHealthyOnly ? "정상 서비스 필터 활성화" : "정상 서비스 필터 해제");
      },
      toggleSort() {
        this.sortDescending = !this.sortDescending;
        this.appendLog(this.sortDescending ? "지연시간 내림차순 정렬" : "지연시간 오름차순 정렬");
      }
    },
    async mounted() {
      setStatus("LOADING", "Vue fetch + reactive render 준비 중");
      const response = await fetch("./data/dashboard.json", { cache: "no-store" });
      if (!response.ok) {
        throw new Error(`dashboard fetch failed: ${response.status}`);
      }
      this.dashboard = await response.json();
      this.appendLog(`${this.dashboard.services.length}개 서비스를 Vue reactivity로 로드했다`);
      setStatus("READY", "Vue 렌더링 완료");
    }
  }).mount("#app");
})();
