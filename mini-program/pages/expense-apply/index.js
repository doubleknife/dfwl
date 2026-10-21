const { request } = require('../../utils/request');
const { hasPermission } = require('../../utils/permissions');
const { requiredEnergyType, eligibleVehicles, eligibleTasks, energyHint } = require('../../utils/expenseEnergy');
const { chooseApprovalImages, singleExpenseItem } = require('../../utils/approvalImages');

function today() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

function displayTasks(tasks) {
  return (tasks || []).map((task) => {
    const route = [task.loadingPlace, task.unloadingPlace].filter(Boolean).join(' → ');
    const taskLabel = [String(task.businessDate || '').slice(0, 10), task.templateName, route, task.plateNo]
      .filter(Boolean).join(' · ');
    return { ...task, taskLabel: taskLabel || '运输任务' };
  });
}

Page({
  data: { scope: 'DAILY', templates: [], templateIndex: -1, drivers: [], driverIndex: -1, ownDriver: false,
    vehicles: [], visibleVehicles: [], vehicleIndex: -1,
    tasks: [], visibleTasks: [], taskIndex: -1, driverId: null, driverName: '', businessDate: today(), amount: '',
    description: '', receiptNo: '', invoiceNo: '', remark: '', stationName: '', step: 1,
    loading: false, busy: false, uploading: false, images: [], userId: null,
    denied: false, error: '', energyHint: '', submittedNo: '', requestKey: '', resubmitId: null,
    initialized: false },
  onLoad(query) { if (query.resubmitId) this.setData({ resubmitId: Number(query.resubmitId) }); },
  onShow() {
    if (this.data.initialized) return;
    this.setData({ initialized: true });
    return this.load();
  },
  async load() {
    this.setData({ loading: true, denied: false, error: '' });
    try {
      const user = await request({ url: '/me' });
      if (user.driverType === 'OUTSOURCED' || !hasPermission(user, 'BUTTON_MINI_EXPENSE_APPLY_SUBMIT')) {
        this.setData({ denied: true, error: '当前账号不能发起费用申请' }); return;
      }
      const [templates, options] = await Promise.all([
        request({ url: '/approval-templates/available' }),
        request({ url: '/expense-applications/options' })
      ]);
      const drivers = options.drivers || [];
      const driver = drivers.find((row) => Number(row.id) === Number(user.driverId));
      const driverIndex = drivers.findIndex((row) => Number(row.id) === Number(user.driverId));
      const vehicles = options.vehicles || [];
      const expenseTemplates = (templates || []).filter((row) => row.business_type === 'EXPENSE');
      const selectedType = expenseTemplates[this.data.templateIndex]?.expense_type;
      const visibleVehicles = eligibleVehicles(selectedType, vehicles);
      const vehicleIndex = visibleVehicles.some((row) => Number(row.id) === Number(user.currentVehicleId))
        ? vehicles.findIndex((row) => Number(row.id) === Number(user.currentVehicleId)) : -1;
      this.setData({ userId: user.id, templates: expenseTemplates,
        drivers, driverIndex, ownDriver: !!user.driverId, vehicles, visibleVehicles,
        visibleTasks: eligibleTasks(selectedType, this.data.tasks, vehicles),
        driverId: driver?.id || null, driverName: driver?.name || user.driverName || '',
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
        const templateIndex = chosenTemplates.findIndex((entry) => Number(entry.id) === Number(previous.approval.template_id));
        const previousType = chosenTemplates[templateIndex]?.expense_type;
        const visibleVehicles = eligibleVehicles(previousType, vehicles);
        const previousVehicleIndex = vehicles.findIndex((entry) => Number(entry.id) === Number(row.vehicle_id));
        this.setData({ scope: previousScope, visibleVehicles,
          templateIndex, energyHint: energyHint(chosenTemplates[templateIndex]?.expense_type),
          driverIndex: drivers.findIndex((entry) => Number(entry.id) === Number(row.driver_id)),
          driverId: Number(row.driver_id),
          driverName: drivers.find((entry) => Number(entry.id) === Number(row.driver_id))?.name || '',
          vehicleIndex: visibleVehicles.some((entry) => Number(entry.id) === Number(row.vehicle_id)) ? previousVehicleIndex : -1,
          businessDate: String(row.business_date).slice(0, 10), amount: String(row.amount),
          description: row.description || '', receiptNo: row.receipt_no || '', invoiceNo: row.invoice_no || '',
          remark: row.remark || '', stationName: row.station_name || '' });
        if (previousScope === 'TASK') {
          const tasks = displayTasks(await request({ url: `/expense-applications/task-candidates?driverId=${row.driver_id}` }));
          const visibleTasks = eligibleTasks(previousType, tasks, vehicles);
          this.setData({ tasks, visibleTasks,
            taskIndex: visibleTasks.some((entry) => Number(entry.taskId) === Number(row.target_task_id))
              ? (tasks || []).findIndex((entry) => Number(entry.taskId) === Number(row.target_task_id)) : -1 });
        }
      }
    } catch (error) { this.setData({ error: error.message || '费用申请加载失败' }); }
    finally { this.setData({ loading: false }); }
  },
  onScope(e) {
    if (this.data.resubmitId) return;
    const scope = e.currentTarget.dataset.scope;
    this.setData({ scope, taskIndex: -1, tasks: [], visibleTasks: [], vehicleIndex: -1, step: 1, error: '' });
    if (scope === 'TASK' && this.data.driverId) this.loadTasks();
  },
  async loadTasks() {
    const driverId = this.data.driverId;
    try {
      const tasks = displayTasks(await request({ url: `/expense-applications/task-candidates?driverId=${driverId}` }));
      if (this.data.scope !== 'TASK' || Number(this.data.driverId) !== Number(driverId)) return;
      const type = this.data.templates[this.data.templateIndex]?.expense_type;
      this.setData({ tasks, visibleTasks: eligibleTasks(type, tasks, this.data.vehicles), taskIndex: -1 });
    } catch (error) { this.setData({ error: error.message || '参与任务加载失败' }); }
  },
  onTemplate(e) {
    if (this.data.resubmitId) return;
    const templateIndex = Number(e.detail.index);
    const type = this.data.templates[templateIndex]?.expense_type;
    const changed = templateIndex !== this.data.templateIndex;
    const resetSelection = changed && !!requiredEnergyType(type);
    this.setData({ templateIndex, energyHint: energyHint(type),
      visibleVehicles: eligibleVehicles(type, this.data.vehicles),
      visibleTasks: eligibleTasks(type, this.data.tasks, this.data.vehicles),
      vehicleIndex: resetSelection ? -1 : this.data.vehicleIndex,
      taskIndex: resetSelection && this.data.scope === 'TASK' ? -1 : this.data.taskIndex,
      step: 1, error: '' });
  },
  onDriver(e) {
    if (this.data.ownDriver || this.data.resubmitId) return;
    const driverIndex = Number(e.detail.index);
    const driver = this.data.drivers[driverIndex];
    this.setData({ driverIndex, driverId: driver?.id || null, driverName: driver?.name || '',
      taskIndex: -1, tasks: [], visibleTasks: [], vehicleIndex: -1, error: '' });
    if (this.data.scope === 'TASK' && driver) this.loadTasks();
  },
  onVehicle(e) {
    const vehicle = this.data.visibleVehicles[Number(e.detail.index)];
    const vehicleIndex = this.data.vehicles.findIndex((entry) => Number(entry.id) === Number(vehicle?.id));
    this.setData({ vehicleIndex, error: '' });
  },
  onTask(e) {
    const task = this.data.visibleTasks[Number(e.detail.index)];
    const taskIndex = this.data.tasks.findIndex((entry) => Number(entry.taskId) === Number(task?.taskId));
    const vehicleIndex = this.data.vehicles.findIndex((row) => Number(row.id) === Number(task?.vehicleId));
    this.setData({ taskIndex, vehicleIndex, error: '' });
  },
  onDate(e) { this.setData({ businessDate: e.detail.value }); },
  onField(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value }); },
  async addImages() {
    if (this.data.busy || this.data.uploading) return;
    if (this.data.images.length >= 20) { this.setData({ error: '一次最多选择 20 张图片' }); return; }
    this.setData({ uploading: true, error: '' });
    try {
      await chooseApprovalImages(this.data.images, this.data.userId, 'APPROVAL_APPLICATION',
        (images) => this.setData({ images }));
    } catch (error) { this.setData({ error: error.message || '图片上传失败，请重新选择' }); }
    finally { this.setData({ uploading: false }); }
  },
  removeImage(e) {
    if (this.data.busy || this.data.uploading) return;
    this.setData({ images: this.data.images.filter((_, index) => index !== Number(e.currentTarget.dataset.index)) });
  },
  previewImage(e) {
    const images = this.data.images;
    wx.previewImage({ current: images[Number(e.currentTarget.dataset.index)].path,
      urls: images.map((image) => image.path) });
  },
  review() {
    if (this.data.uploading) return;
    const amount = Number(this.data.amount);
    if (this.data.templateIndex < 0 || this.data.vehicleIndex < 0 || !this.data.driverId
        || !this.data.businessDate || !/^\d+(\.\d{1,2})?$/.test(String(this.data.amount))
        || !Number.isFinite(amount) || amount <= 0 || (this.data.scope === 'TASK' && this.data.taskIndex < 0)) {
      this.setData({ error: '请补齐费用大类、车牌、日期、正数金额和目标任务' }); return;
    }
    const type = this.data.templates[this.data.templateIndex]?.expense_type;
    const selectedVehicle = this.data.vehicles[this.data.vehicleIndex];
    const selectedTask = this.data.tasks[this.data.taskIndex];
    if (!eligibleVehicles(type, this.data.vehicles).some((entry) => Number(entry.id) === Number(selectedVehicle?.id))
        || (this.data.scope === 'TASK' && (!eligibleTasks(type, this.data.tasks, this.data.vehicles)
          .some((entry) => Number(entry.taskId) === Number(selectedTask?.taskId))
          || Number(selectedTask?.vehicleId) !== Number(selectedVehicle?.id)))) {
      this.setData({ vehicleIndex: -1, taskIndex: -1, error: '请重新选择符合当前费用大类的车辆或任务' }); return;
    }
    this.setData({ step: 2, error: '', requestKey: this.data.requestKey || `MP-${Date.now()}-${Math.random().toString(36).slice(2)}` });
  },
  edit() { this.setData({ step: 1 }); },
  async submit() {
    if (this.data.busy || this.data.uploading) return;
    this.setData({ busy: true, error: '' });
    const d = this.data;
    try {
      const item = { businessDate: d.businessDate, driverId: d.driverId,
          vehicleId: d.vehicles[d.vehicleIndex].id,
          targetTaskId: d.scope === 'TASK' ? d.tasks[d.taskIndex].taskId : null,
          amount: d.amount, description: d.description || null, receiptNo: d.receiptNo || null,
          invoiceNo: d.invoiceNo || null, remark: d.remark || null, stationName: d.stationName || null };
      const items = singleExpenseItem(item);
      const result = await request({ url: d.resubmitId ? `/approvals/${d.resubmitId}/resubmit` : '/approvals',
        method: 'POST', data: d.resubmitId ? { items, attachmentIds: d.images.map((image) => image.id) }
          : { templateId: d.templates[d.templateIndex].id, applicationScope: d.scope,
            requestKey: d.requestKey, items, attachmentIds: d.images.map((image) => image.id) } });
      this.setData({ step: 3, submittedNo: result.approval.approval_no });
    } catch (error) { this.setData({ error: error.message || '提交失败，填写内容已保留，请重试' }); }
    finally { this.setData({ busy: false }); }
  },
  openApprovals() { wx.navigateTo({ url: '/pages/approvals/index' }); }
});
