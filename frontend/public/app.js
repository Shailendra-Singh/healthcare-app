// Care Tasks frontend: one Alpine.js component, talking to the api-gateway on the same origin.
// The gateway keeps the Keycloak session in a cookie and applies each role's access, so this code never handles
// tokens and never decides what a role may see: it shows whatever the gateway returns.

const ROLE_LABELS = { admin: 'Admin', scheduler: 'Scheduler', 'clinical-team': 'Clinical team' };
const TYPE_LABELS = { SCHEDULING: 'Scheduling', REFERRAL: 'Referral' };
const TRIGGER_LABELS = { SCHEDULED: 'Scheduled', MANUAL: 'Manual', STARTUP: 'Startup', DATA_CHANGED: 'New data' };
const ADMIN_VIEWS = ['etl', 'evaluations'];
const STATUS_LABELS = {
  OPEN: 'Open', IN_PROGRESS: 'In progress', COMPLETED: 'Completed', CANCELLED: 'Cancelled', RESOLVED: 'Resolved',
  MET: 'Met', SCHEDULED: 'Scheduled', OVERDUE: 'Overdue',
};

/** An API error with its HTTP status. */
class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

document.addEventListener('alpine:init', () => {
  Alpine.data('app', () => ({
    state: 'loading', // loading | signedOut | signedIn
    me: null,
    view: 'worklist',
    error: null,

    // Worklist
    taskTypes: [],
    specialties: [],
    filters: { taskType: '', specialty: '', status: 'ACTIVE', mine: false },
    tasks: [],
    page: 1,
    pageSize: 50,
    loadingTasks: false,
    cancelling: null, // the task in the cancel dialog
    cancelReason: '',

    // Patients
    patients: [],
    patientPage: 1,
    patientPageSize: 25,
    patientSearch: '',
    searching: false,
    loadingPatients: false,
    // Admin pages; notice: { kind: 'info' | 'success' | 'error', text }
    etl: { heartbeat: null, runs: [], loading: false, busy: false, force: false, notice: null },
    evaluations: { runs: [], programs: [], loading: false, busy: false, notice: null },

    selected: null, // { patient, programs, programsForbidden, tasks }; programs and tasks: null while loading, false on error

    async init() {
      // The gateway's logout returns to "/?state=…"; keep the address bar clean
      if (location.search) history.replaceState(null, '', location.pathname + location.hash);
      try {
        this.me = await this.api('/api/v1/me');
      } catch (e) {
        this.state = 'signedOut';
        return;
      }
      this.state = 'signedIn';
      window.addEventListener('hashchange', () => this.route());
      await this.loadFilterOptions();
      this.route();
    },

    route() {
      let view = location.hash.replace(/^#\//, '');
      if (!['worklist', 'patients', ...ADMIN_VIEWS].includes(view) || (ADMIN_VIEWS.includes(view) && !this.isAdmin())) {
        view = 'worklist';
      }
      this.view = view;
      if (view === 'worklist') {
        this.loadTasks();
      } else if (view === 'patients') {
        if (this.patients.length === 0 && !this.searching) this.loadPatients();
      } else if (view === 'etl') {
        this.loadEtl();
      } else {
        this.loadEvaluations();
      }
    },

    isAdmin() {
      return (this.me?.roles || []).includes('admin');
    },

    /**
     * Calls the gateway. Without a session the gateway answers 499 to JavaScript requests (401 in some cases);
     * both mean "show the login page".
     */
    async api(path, options = {}) {
      const response = await fetch(path, {
        ...options,
        credentials: 'same-origin',
        headers: { 'X-Requested-With': 'JavaScript', Accept: 'application/json', ...(options.headers || {}) },
      });
      if (response.status === 499 || response.status === 401) {
        this.state = 'signedOut';
        this.me = null;
        throw new ApiError(response.status, 'Your session has ended; sign in again.');
      }
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new ApiError(response.status, body.message || `${response.status} ${response.statusText}`);
      }
      return response.status === 204 ? null : response.json();
    },

    async loadFilterOptions() {
      try {
        this.taskTypes = this.me.taskTypes.includes('ALL')
          ? (await this.api('/task-generation/api/v1/task-types')).map((t) => t.code)
          : this.me.taskTypes;
        this.specialties = (await this.api('/clinical-data/api/v1/specialties')).map((s) => s.name);
      } catch (e) {
        this.showError(e);
      }
    },

    // --- Worklist ---

    async loadTasks() {
      this.loadingTasks = true;
      const query = new URLSearchParams({ status: this.filters.status, page: this.page, size: this.pageSize });
      if (this.filters.taskType) query.set('taskType', this.filters.taskType);
      if (this.filters.specialty) query.set('specialty', this.filters.specialty);
      if (this.filters.mine) query.set('assignee', this.me.username);
      try {
        this.tasks = await this.api(`/task-generation/api/v1/tasks?${query}`);
        this.error = null;
      } catch (e) {
        this.tasks = [];
        this.showError(e);
      } finally {
        this.loadingTasks = false;
      }
    },

    /** Start (and take) a task, send it back to open, complete it or cancel it. The gateway records who did it. */
    async changeTask(task, status) {
      if (status === 'CANCELLED') {
        this.cancelReason = '';
        this.cancelling = task; // the dialog asks for a reason, then calls confirmCancel()
        return;
      }
      const change = { status };
      if (status === 'IN_PROGRESS') {
        change.assignee = this.me.username;
      }
      await this.patchTask(task, change);
    },

    async confirmCancel() {
      const task = this.cancelling;
      this.cancelling = null;
      await this.patchTask(task, { status: 'CANCELLED', reason: this.cancelReason.trim() || null });
    },

    async patchTask(task, change) {
      try {
        await this.api(`/task-generation/api/v1/tasks/${task.taskId}`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(change),
        });
        await this.loadTasks();
      } catch (e) {
        this.showError(e);
      }
    },

    // --- Patients ---

    async loadPatients() {
      this.loadingPatients = true;
      this.searching = false;
      try {
        this.patients = await this.api(`/clinical-data/api/v1/patients?page=${this.patientPage}&size=${this.patientPageSize}`);
        this.error = null;
      } catch (e) {
        this.patients = [];
        this.showError(e);
      } finally {
        this.loadingPatients = false;
      }
    },

    async searchPatient() {
      if (!this.patientSearch) {
        this.patientPage = 1;
        return this.loadPatients();
      }
      this.searching = true;
      try {
        const patient = await this.api(`/clinical-data/api/v1/patients/source/${encodeURIComponent(this.patientSearch)}`);
        this.patients = [patient];
        this.selectPatient(patient);
      } catch (e) {
        this.patients = [];
        if (e.status !== 404) this.showError(e);
      }
    },

    /** From the worklist: open the patient's detail on the Patients view. */
    async openPatient(sourcePatientId) {
      this.patientSearch = sourcePatientId;
      this.searching = true; // so switching to the Patients view does not load the full list over the result
      location.hash = '#/patients';
      await this.searchPatient();
    },

    async selectPatient(patient) {
      this.selected = { patient, programs: null, programsForbidden: false, tasks: null };
      const id = encodeURIComponent(patient.sourcePatientId);
      const [programs, tasks] = await Promise.allSettled([
        this.api(`/rules-engine/api/v1/patients/${id}/programs`),
        this.api(`/task-generation/api/v1/patients/${id}/tasks`),
      ]);
      if (this.selected?.patient.id !== patient.id) return; // another patient was picked meanwhile
      if (programs.status === 'fulfilled') {
        this.selected.programs = programs.value;
      } else if (programs.reason.status === 403) {
        this.selected.programsForbidden = true; // schedulers do not see care needs
      } else {
        this.selected.programs = false;
        this.showError(programs.reason);
      }
      if (tasks.status === 'fulfilled') {
        this.selected.tasks = tasks.value;
      } else {
        this.selected.tasks = false;
        this.showError(tasks.reason);
      }
    },

    // --- ETL (admins) ---

    async loadEtl() {
      this.etl.loading = true;
      try {
        const [heartbeat, runs] = await Promise.all([
          this.api('/clinical-data/api/v1/etl-heartbeat').catch((e) => (e.status === 404 ? null : Promise.reject(e))),
          this.api('/clinical-data/api/v1/etl-runs?size=20'),
        ]);
        this.etl.heartbeat = heartbeat;
        this.etl.runs = runs;
      } catch (e) {
        this.showError(e);
      } finally {
        this.etl.loading = false;
      }
    },

    /** Asks the ETL to check the data folder now, then follows its heartbeat until it reports back. */
    async runEtl() {
      const { force } = this.etl;
      const before = this.etl.heartbeat?.checkedAt;
      this.etl.busy = true;
      this.etl.notice = { kind: 'info', text: force ? 'Reload requested; waiting for the ETL…' : 'Check requested; waiting for the ETL…' };
      try {
        await this.api('/etl/api/v1/runs', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ force }),
        });
        const heartbeat = await this.poll(
          () => this.api('/clinical-data/api/v1/etl-heartbeat').catch((e) => (e.status === 404 ? null : Promise.reject(e))),
          (beat) => beat && beat.checkedAt !== before,
          120_000,
        );
        await this.loadEtl();
        this.etl.notice = !heartbeat
          ? { kind: 'error', text: 'The ETL has not reported back yet; refresh this page in a moment.' }
          : {
              kind: heartbeat.outcome === 'failed' ? 'error' : 'success',
              text: heartbeat.detail + (heartbeat.outcome === 'loaded' ? '. The rules-engine re-evaluates within a minute.' : ''),
            };
      } catch (e) {
        this.etl.notice = { kind: 'error', text: e.message };
      } finally {
        this.etl.busy = false;
      }
    },

    heartbeatClass(outcome) {
      return { loaded: 'completed', unchanged: 'met', waiting: 'open', busy: 'open', failed: 'overdue' }[outcome] || '';
    },

    // --- Evaluations (admins) ---

    async loadEvaluations() {
      this.evaluations.loading = true;
      try {
        const [runs, catalog] = await Promise.all([
          this.api('/rules-engine/api/v1/evaluations?size=20'),
          this.evaluations.programs.length ? null : this.api('/rules-engine/api/v1/programs'),
        ]);
        this.evaluations.runs = runs;
        if (catalog) this.evaluations.programs = catalog.programs;
      } catch (e) {
        this.showError(e);
      } finally {
        this.evaluations.loading = false;
      }
    },

    /** Starts an evaluation (or follows the one already running) until it finishes. */
    async runEvaluation() {
      this.evaluations.busy = true;
      try {
        let run;
        try {
          run = await this.api('/rules-engine/api/v1/evaluations', { method: 'POST' });
        } catch (e) {
          if (e.status !== 409) throw e;
          run = await this.api('/rules-engine/api/v1/evaluations/latest');
        }
        this.evaluations.notice = { kind: 'info', text: `Evaluation run ${run.runId} is running…` };
        const done = await this.poll(
          () => this.api(`/rules-engine/api/v1/evaluations/${run.runId}`),
          (latest) => latest.status !== 'RUNNING',
          300_000,
        );
        await this.loadEvaluations();
        if (!done) {
          this.evaluations.notice = { kind: 'info', text: `Run ${run.runId} is still running; refresh this page later.` };
        } else if (done.status === 'SUCCEEDED') {
          const overdue = done.summary?.needsByStatus?.OVERDUE ?? 0;
          this.evaluations.notice = {
            kind: 'success',
            text: `Run ${done.runId} finished: ${done.patientsEvaluated} patients, ${overdue} overdue care needs. Tasks follow within a few minutes.`,
          };
        } else {
          this.evaluations.notice = { kind: 'error', text: `Run ${done.runId} failed: ${done.errorMessage || 'see the rules-engine log'}` };
        }
      } catch (e) {
        this.evaluations.notice = { kind: 'error', text: e.message };
      } finally {
        this.evaluations.busy = false;
      }
    },

    /** The newest successful run: the stats and tiers describe it. */
    latestEvaluation() {
      return this.evaluations.runs.find((run) => run.status === 'SUCCEEDED') || null;
    },

    /** Care needs of the latest successful run with this status, or all of them. */
    needs(status) {
      const counts = this.latestEvaluation()?.summary?.needsByStatus || {};
      return status ? counts[status] || 0 : Object.values(counts).reduce((a, b) => a + b, 0);
    },

    /** Patients per tier, grouped by program, in the order the programs define their tiers. */
    tierSummary() {
      const counts = this.latestEvaluation()?.summary?.tiers || [];
      const programIds = [...new Set(counts.map((count) => count.programId))];
      return programIds.map((programId) => {
        const program = this.evaluations.programs.find((p) => p.id === programId);
        const order = (program?.tiers || []).map((tier) => tier.id);
        const tiers = counts
          .filter((count) => count.programId === programId)
          .map((count) => ({
            tierId: count.tierId,
            name: count.tierId === null ? 'No tier' : program?.tiers.find((t) => t.id === count.tierId)?.name || count.tierId,
            patients: count.patients,
          }))
          .sort((a, b) => (order.indexOf(a.tierId) + 1 || 99) - (order.indexOf(b.tierId) + 1 || 99));
        return {
          programId,
          name: program?.name || this.programLabel(programId),
          tiers,
          total: tiers.reduce((sum, tier) => sum + tier.patients, 0),
          max: Math.max(1, ...tiers.map((tier) => tier.patients)),
        };
      });
    },

    triggerLabel: (trigger) => TRIGGER_LABELS[trigger] || trigger,

    /** Calls fetch() every 2 seconds until done(result) holds; null after the timeout. */
    async poll(fetch, done, timeoutMs) {
      const deadline = Date.now() + timeoutMs;
      while (Date.now() < deadline) {
        await new Promise((resolve) => setTimeout(resolve, 2000));
        const result = await fetch();
        if (done(result)) return result;
      }
      return null;
    },

    // --- Display helpers ---

    roleLabel: (role) => ROLE_LABELS[role] || role,
    typeLabel: (type) => TYPE_LABELS[type] || type,
    statusLabel: (status) => STATUS_LABELS[status] || status,

    /** diabetes-management -> Diabetes Management */
    programLabel: (id) => id.split('-').map((word) => word.charAt(0).toUpperCase() + word.slice(1)).join(' '),
    /** high-risk -> High risk */
    tierLabel: (id) => (id.charAt(0).toUpperCase() + id.slice(1)).replaceAll('-', ' '),

    /** scheduler.user -> SU */
    initials() {
      const parts = (this.me?.username || '?').split(/[.\-_@ ]+/).filter(Boolean);
      return parts.slice(0, 2).map((part) => part.charAt(0).toUpperCase()).join('');
    },

    overdueCount() {
      return this.tasks.filter((task) => this.isOverdue(task)).length;
    },

    roleHint() {
      const roles = this.me?.roles || [];
      if (roles.includes('admin')) return 'Showing every task.';
      if (roles.includes('clinical-team')) return 'Scheduling and referral tasks: review whether each referral is appropriate.';
      if (roles.includes('scheduler')) return 'Scheduling tasks: patients who need an appointment booked.';
      return 'Your account has no role in this app; ask an administrator.';
    },

    sum: (items, field) => (items || []).reduce((total, item) => total + (item[field] || 0), 0),

    /** 2026-09-28T08:11:46Z -> "Sep 28, 2026, 8:11 AM" in the browser's locale and time zone */
    dateTime: (iso) => (iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—'),

    /** "3 minutes ago", "in 4 minutes" */
    relativeTime(iso) {
      if (!iso) return '—';
      const seconds = Math.round((new Date(iso) - Date.now()) / 1000);
      const format = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
      for (const [unit, size] of [['day', 86400], ['hour', 3600], ['minute', 60]]) {
        if (Math.abs(seconds) >= size) return format.format(Math.round(seconds / size), unit);
      }
      return format.format(seconds, 'second');
    },

    /** 40 -> "< 0.1 s", 1500 -> "1.5 s", 125000 -> "2 min 5 s" */
    duration(ms) {
      if (ms < 100) return '< 0.1 s';
      if (ms < 60_000) return `${(ms / 1000).toFixed(ms < 10_000 ? 1 : 0)} s`;
      const minutes = Math.floor(ms / 60_000);
      const seconds = Math.round((ms % 60_000) / 1000);
      return seconds ? `${minutes} min ${seconds} s` : `${minutes} min`;
    },

    fileSize: (bytes) => (bytes < 1024 ? `${bytes} B` : bytes < 1048576 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1048576).toFixed(1)} MB`),

    isOverdue(task) {
      return (task.status === 'OPEN' || task.status === 'IN_PROGRESS') && task.dueDate < this.today();
    },

    today() {
      const now = new Date();
      return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
    },

    age(dateOfBirth) {
      const born = new Date(dateOfBirth);
      const now = new Date();
      let years = now.getFullYear() - born.getFullYear();
      if (now < new Date(now.getFullYear(), born.getMonth(), born.getDate())) years--;
      return years;
    },

    /** {"age": 67, "HbA1c": {"value": 9.4, "date": "2026-05-02"}} -> "age 67 · HbA1c 9.4 (2026-05-02)" */
    evidenceText(evidence) {
      return Object.entries(evidence || {})
        .map(([key, value]) => {
          if (Array.isArray(value)) return `${key} ${value.join(', ')}`;
          if (value && typeof value === 'object') return `${key} ${value.value} (${value.date})`;
          return `${key} ${value}`;
        })
        .join(' · ');
    },

    showError(e) {
      if (e.status === 499 || e.status === 401) return; // the login page is showing
      this.error = e.message;
    },
  }));
});
