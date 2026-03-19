import "https://esm.sh/@angular/compiler@20?bundle&target=es2022";
import { CommonModule } from "https://esm.sh/@angular/common@20?bundle&target=es2022";
import { Component, computed, provideZonelessChangeDetection, signal } from "https://esm.sh/@angular/core@20?bundle&target=es2022";
import { bootstrapApplication } from "https://esm.sh/@angular/platform-browser@20?bundle&target=es2022";

const statusNode = document.querySelector("[data-sample-status]");
const noteNode = document.querySelector("[data-sample-note]");
const numberFormat = new Intl.NumberFormat("ko-KR");

function setStatus(status, note) {
  statusNode.textContent = status;
  statusNode.setAttribute("data-sample-status", status);
  noteNode.textContent = note;
}

class AngularSampleComponent {
  dashboard = signal(null);
  showHealthyOnly = signal(false);
  sortDescending = signal(true);
  logEntries = signal(["Angular runtime attached"]);

  summary = computed(() => {
    const dashboard = this.dashboard();
    if (!dashboard) {
      return { totalServices: 0, healthyCount: 0, deploysToday: 0, latencyAverage: 0 };
    }
    const services = dashboard.services;
    const healthyCount = services.filter((service) => service.healthy).length;
    const deploysToday = services.reduce((sum, service) => sum + service.deploysToday, 0);
    const latencyAverage = Math.round(services.reduce((sum, service) => sum + service.latencyMs, 0) / services.length);
    return {
      totalServices: services.length,
      healthyCount,
      deploysToday,
      latencyAverage
    };
  });

  visibleServices = computed(() => {
    const dashboard = this.dashboard();
    if (!dashboard) {
      return [];
    }
    return dashboard.services
      .filter((service) => !this.showHealthyOnly() || service.healthy)
      .sort((left, right) => {
        const delta = left.latencyMs - right.latencyMs;
        return this.sortDescending() ? -delta : delta;
      });
  });

  formattedGeneratedAt = computed(() => {
    const dashboard = this.dashboard();
    return dashboard ? new Date(dashboard.generatedAt).toLocaleString("ko-KR") : "";
  });

  ngOnInit() {
    this.bootstrap();
  }

  formatNumber(value) {
    return numberFormat.format(value);
  }

  appendLog(message) {
    const stamp = new Date().toLocaleTimeString("ko-KR");
    this.logEntries.update((entries) => [`${stamp} ${message}`, ...entries].slice(0, 6));
  }

  toggleHealthy() {
    const next = !this.showHealthyOnly();
    this.showHealthyOnly.set(next);
    this.appendLog(next ? "정상 서비스 필터 활성화" : "정상 서비스 필터 해제");
  }

  toggleSort() {
    const next = !this.sortDescending();
    this.sortDescending.set(next);
    this.appendLog(next ? "지연시간 내림차순 정렬" : "지연시간 오름차순 정렬");
  }

  async bootstrap() {
    setStatus("LOADING", "Angular fetch + standalone bootstrap 준비 중");
    const response = await fetch("./data/dashboard.json", { cache: "no-store" });
    if (!response.ok) {
      throw new Error(`dashboard fetch failed: ${response.status}`);
    }
    const dashboard = await response.json();
    this.dashboard.set(dashboard);
    this.appendLog(`${dashboard.services.length}개 서비스를 Angular signal로 로드했다`);
    setStatus("READY", "Angular 렌더링 완료");
  }
}

Component({
  selector: "sample-angular-app",
  standalone: true,
  imports: [CommonModule],
  template: `
    <div *ngIf="!dashboard()" class="loading">Angular 대시보드를 초기화하는 중이다.</div>
    <ng-container *ngIf="dashboard() as dashboard">
      <div class="toolbar">
        <button type="button" (click)="toggleHealthy()">{{ showHealthyOnly() ? "전체 서비스 보기" : "정상 서비스만 보기" }}</button>
        <button type="button" class="secondary" (click)="toggleSort()">{{ sortDescending() ? "지연시간 오름차순" : "지연시간 내림차순" }}</button>
        <span class="caption">팀 {{ dashboard.team }} · {{ formattedGeneratedAt() }}</span>
      </div>
      <div class="cards">
        <article class="metric-card">
          <h2>Services</h2>
          <div class="metric-value">{{ summary().totalServices }}</div>
          <div class="metric-detail">전체 서비스 수</div>
        </article>
        <article class="metric-card">
          <h2>Healthy</h2>
          <div class="metric-value">{{ summary().healthyCount }}</div>
          <div class="metric-detail">정상 상태 인스턴스</div>
        </article>
        <article class="metric-card">
          <h2>Avg Latency</h2>
          <div class="metric-value">{{ formatNumber(summary().latencyAverage) }} ms</div>
          <div class="metric-detail">서비스 평균 응답 시간</div>
        </article>
        <article class="metric-card">
          <h2>Deploys</h2>
          <div class="metric-value">{{ formatNumber(summary().deploysToday) }}</div>
          <div class="metric-detail">오늘 배포 횟수</div>
        </article>
      </div>
      <div class="dashboard-grid">
        <section class="panel">
          <h2>Angular Service Radar</h2>
          <div class="service-grid">
            <article class="service-card" *ngFor="let service of visibleServices()">
              <header>
                <div>
                  <h3>{{ service.name }}</h3>
                  <div class="metric-detail">{{ service.owner }} · {{ service.region }}</div>
                </div>
                <span class="badge" [class.healthy]="service.healthy" [class.degraded]="!service.healthy">{{ service.healthy ? "healthy" : "degraded" }}</span>
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
            <li *ngFor="let alert of dashboard.alerts"><span>{{ alert.level.toUpperCase() }}</span><strong>{{ alert.message }}</strong></li>
          </ul>
          <h2 style="margin-top: 22px;">Activity</h2>
          <ul class="log-list">
            <li *ngFor="let entry of logEntries()"><span>{{ entry }}</span></li>
          </ul>
        </aside>
      </div>
    </ng-container>
  `
})(AngularSampleComponent);

bootstrapApplication(AngularSampleComponent, {
  providers: [provideZonelessChangeDetection()]
}).catch((error) => {
  setStatus("ERROR", error.message || "Angular bootstrap failed");
  throw error;
});
