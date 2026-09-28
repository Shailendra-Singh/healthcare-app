// Care Tasks frontend: one Alpine.js component, talking to the api-gateway on the same origin.
// The gateway keeps the Keycloak session in a cookie and applies each role's access, so this code never handles
// tokens and never decides what a role may see: it shows whatever the gateway returns.

const ROLE_LABELS = { admin: 'Admin', scheduler: 'Scheduler', 'clinical-team': 'Clinical team' };
const TYPE_LABELS = { SCHEDULING: 'Scheduling', REFERRAL: 'Referral' };
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

    // Patients
    patients: [],
    patientPage: 1,
    patientPageSize: 25,
    patientSearch: '',
    searching: false,
    loadingPatients: false,
    selected: null, // { patient, programs, programsForbidden, tasks }

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
      const view = location.hash.replace(/^#\//, '');
      this.view = view === 'patients' ? 'patients' : 'worklist';
      if (this.view === 'worklist') {
        this.loadTasks();
      } else if (this.patients.length === 0) {
        this.loadPatients();
      }
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
      const change = { status };
      if (status === 'IN_PROGRESS') {
        change.assignee = this.me.username;
      }
      if (status === 'CANCELLED') {
        const reason = prompt('Why cancel this task?');
        if (reason === null) return;
        change.reason = reason;
      }
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
        this.showError(programs.reason);
      }
      if (tasks.status === 'fulfilled') {
        this.selected.tasks = tasks.value;
      } else {
        this.showError(tasks.reason);
      }
    },

    // --- Display helpers ---

    roleLabel: (role) => ROLE_LABELS[role] || role,
    typeLabel: (type) => TYPE_LABELS[type] || type,
    statusLabel: (status) => STATUS_LABELS[status] || status,

    /** diabetes-management -> Diabetes Management */
    programLabel: (id) => id.split('-').map((word) => word.charAt(0).toUpperCase() + word.slice(1)).join(' '),

    roleHint() {
      const roles = this.me?.roles || [];
      if (roles.includes('admin')) return 'Showing every task.';
      if (roles.includes('clinical-team')) return 'Scheduling and referral tasks: review whether each referral is appropriate.';
      if (roles.includes('scheduler')) return 'Scheduling tasks: patients who need an appointment booked.';
      return 'Your account has no role in this app; ask an administrator.';
    },

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
