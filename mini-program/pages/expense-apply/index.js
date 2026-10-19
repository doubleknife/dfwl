const { request } = require('../../utils/request');
const { hasPermission } = require('../../utils/permissions');

function today() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

Page({
  data: { scope: 'DAILY', templates: [], templateIndex: -1, drivers: [], vehicles: [], vehicleIndex: -1,
    tasks: [], taskIndex: -1, driverId: null, driverName: '', businessDate: today(), amount: '',
    description: '', receiptNo: '', invoiceNo: '', remark: '', stationName: '', step: 1,
    loading: false, busy: false, denied: false, error: '', submittedNo: '', requestKey: '', resubmitId: null },
  onLoad(query) { if (query.resubmitId) this.setData({ resubmitId: Number(query.resubmitId) }); },
  onShow() { if (!this.data.submittedNo) this.load(); },
  async load() {
    this.setData({ loading: true, denied: false, error: '' });
    try {
      const user = await request({ url: '/me' });
      if (user.driverType !== 'INTERNAL' || !hasPermission(user, 'BUTTON_MINI_EXPENSE_APPLY_SUBMIT')) {
        this.setData({ denied: true, error: '当前账号不能发起费用申请' }); return;
      }
      const [templates, options] = await Promise.all([
        request({ url: '/approval-templates/available' }),
        request({ url: '/expense-applications/options' })
      ]);
      const drivers = options.drivers || [];
      const driver = drivers.find((row) => Number(row.id) === Number(user.driverId));
      const vehicles = options.vehicles || [];
      const vehicleIndex = vehicles.findIndex((row) => Number(row.id) === Number(user.currentVehicleId));
      this.setData({ templates: (templates || []).filter((row) => row.business_type === 'EXPENSE'),
        drivers, vehicles, driverId: driver?.id || null, driverName: driver?.name || user.driverName || '',
        vehicleIndex });
      if (this.data.resubmitId) {
        const previous = await request({ url: `/approvals/${this.data.resubmitId}` });
        if (previous.approval.status !== 'RETURNED_TO_APPLICANT'
          || Number(previous.approval.applicant_user_id) !== Number(user.id))
          throw new Error('这条申请当前不能重新提交');
        const entries = previous.expenseItems || [];
        const latest = Math.max(0, ...entries.map((entry) => Number(entry.submission_version_id)));
        const rows = entries.filter((entry) => Number(entry.submission_version_id) === latest);
        if (rows.length !== 1) throw new Error('批量费用请在管理端修改后重新提交');
        const row = rows[0];
        const chosenTemplates = (templates || []).filter((entry) => entry.business_type === 'EXPENSE');
        const previousScope = previous.approval.application_scope;
        this.setData({ scope: previousScope,
          templateIndex: chosenTemplates.findIndex((entry) => Number(entry.id) === Number(previous.approval.template_id)),
          vehicleIndex: vehicles.findIndex((entry) => Number(entry.id) === Number(row.vehicle_id)),
          businessDate: String(row.business_date).slice(0, 10), amount: String(row.amount),
          description: row.description || '', receiptNo: row.receipt_no || '', invoiceNo: row.invoice_no || '',
          remark: row.remark || '', stationName: row.station_name || '' });
        if (previousScope === 'TASK') {
          const tasks = await request({ url: `/expense-applications/task-candidates?driverId=${driver.id}` });
          this.setData({ tasks: tasks || [],
            taskIndex: (tasks || []).findIndex((entry) => Number(entry.taskId) === Number(row.target_task_id)) });
        }
      }
    } catch (error) { this.setData({ error: error.message || '费用申请加载失败' }); }
    finally { this.setData({ loading: false }); }
  },
  onScope(e) {
    if (this.data.resubmitId) return;
    const scope = e.currentTarget.dataset.scope;
    this.setData({ scope, taskIndex: -1, tasks: [], step: 1, error: '' });
    if (scope === 'TASK' && this.data.driverId) this.loadTasks();
  },
  async loadTasks() {
    try {
      const tasks = await request({ url: `/expense-applications/task-candidates?driverId=${this.data.driverId}` });
      this.setData({ tasks: tasks || [], taskIndex: -1 });
    } catch (error) { this.setData({ error: error.message || '参与任务加载失败' }); }
  },
  onTemplate(e) { if (!this.data.resubmitId) this.setData({ templateIndex: Number(e.detail.value), error: '' }); },
  onVehicle(e) { this.setData({ vehicleIndex: Number(e.detail.value), error: '' }); },
  onTask(e) {
    const taskIndex = Number(e.detail.value);
    const task = this.data.tasks[taskIndex];
    const vehicleIndex = this.data.vehicles.findIndex((row) => Number(row.id) === Number(task?.vehicleId));
    this.setData({ taskIndex, vehicleIndex, error: '' });
  },
  onDate(e) { this.setData({ businessDate: e.detail.value }); },
  onField(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value }); },
  review() {
    const amount = Number(this.data.amount);
    if (this.data.templateIndex < 0 || this.data.vehicleIndex < 0 || !this.data.driverId
        || !this.data.businessDate || !/^\d+(\.\d{1,2})?$/.test(String(this.data.amount))
        || !Number.isFinite(amount) || amount <= 0 || (this.data.scope === 'TASK' && this.data.taskIndex < 0)) {
      this.setData({ error: '请补齐费用大类、车牌、日期、正数金额和目标任务' }); return;
    }
    this.setData({ step: 2, error: '', requestKey: this.data.requestKey || `MP-${Date.now()}-${Math.random().toString(36).slice(2)}` });
  },
  edit() { this.setData({ step: 1 }); },
  async submit() {
    if (this.data.busy) return;
    this.setData({ busy: true, error: '' });
    const d = this.data;
    try {
      const item = { businessDate: d.businessDate, driverId: d.driverId,
          vehicleId: d.vehicles[d.vehicleIndex].id,
          targetTaskId: d.scope === 'TASK' ? d.tasks[d.taskIndex].taskId : null,
          amount: d.amount, description: d.description || null, receiptNo: d.receiptNo || null,
          invoiceNo: d.invoiceNo || null, remark: d.remark || null, stationName: d.stationName || null };
      const result = await request({ url: d.resubmitId ? `/approvals/${d.resubmitId}/resubmit` : '/approvals',
        method: 'POST', data: d.resubmitId ? { items: [item], attachmentIds: [] }
          : { templateId: d.templates[d.templateIndex].id, applicationScope: d.scope,
            requestKey: d.requestKey, items: [item] } });
      this.setData({ step: 3, submittedNo: result.approval.approval_no });
    } catch (error) { this.setData({ error: error.message || '提交失败，填写内容已保留，请重试' }); }
    finally { this.setData({ busy: false }); }
  },
  openApprovals() { wx.navigateTo({ url: '/pages/approvals/index' }); }
});
