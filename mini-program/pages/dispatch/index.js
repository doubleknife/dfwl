const { request } = require('../../utils/request');
const { hasPermission, isDriver } = require('../../utils/permissions');
const { routePresentation } = require('../../utils/homePresentation');
const { formatDateTime } = require('../../utils/dateTime');
const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; };
const money = (v) => /^(0|[1-9]\d*)(\.\d{1,2})?$/.test(String(v));
const key = () => `mini-${Date.now()}-${Math.random().toString(36).slice(2)}`;
const confirm = (title, content) => new Promise((resolve) => wx.showModal({ title, content,
  success: (r) => resolve(!!r.confirm), fail: () => resolve(false) }));
const api = (url, data) => request({ url: `/mini-dispatch${url}`, ...(data ? { method: 'POST', data } : {}) });
Page({
  data: { tab: 'new', loading: false, listLoading: false, busy: false, denied: true, error: '', listError: '', notice: '',
    canPublish: false, canTransfer: false, canCancel: false, templates: [], drivers: [], driverPage: 0, driverTotal: 0, driversLoading: false, driversFresh: false, driverError: '',
    form: { businessDate: today(), templateStableCode: '', driverId: '', outsourcedPlateNo: '',
      actualUnitPrice: '', actualInformationFee: '', initialSalary: '' }, template: null, driver: null,
    statusIndex: 0, statusOptions: ['全部状态', '等待发车', '运输中', '等待接手发车', '已完成', '已取消'],
    statusValues: ['', 'PUBLISHED', 'IN_TRANSIT', 'TRANSFER_PENDING', 'COMPLETED', 'CANCELLED'],
    detailOpen: false, detailId: '', detail: null, detailLoading: false, detailError: '',
    date: today(), tasks: [], pageNo: 1, total: 0, highlight: '', action: null, transferDriver: null,
    transferDriverId: '', transferPlate: '', newSalary: '', reason: '', participants: [], context: null },
  async onShow() {
    this.setData({ loading: true, denied: true, error: '' });
    try {
      const user = await request({ url: '/me' });
      getApp().globalData.user = user;
      const denied = isDriver(user) || !!user.driverId || !hasPermission(user, 'PAGE_MINI_DISPATCH');
      this.setData({ denied, canPublish: hasPermission(user, 'BUTTON_MINI_DISPATCH_PUBLISH'),
        canTransfer: hasPermission(user, 'BUTTON_MINI_DISPATCH_TRANSFER'), canCancel: hasPermission(user, 'BUTTON_MINI_DISPATCH_CANCEL') });
      if (denied) { this.closeDetail(); return; }
      const templates = await api('/templates');
      this.setData({ templates: templates.map(t => ({ ...t, label: `${t.name} · ${t.loadingPlace} → ${t.unloadingPlace}` })) });
      await this.refreshDrivers();
      if (this.data.tab === 'sent') await this.loadTasks();
    } catch (e) { this.setData({ error: e.message }); }
    finally { this.setData({ loading: false }); }
  },
  async refreshDrivers() {
    if (this.data.busy) return;
    const sequence = this.driverSequence = (this.driverSequence || 0) + 1;
    this.setData({ driversLoading: true, driversFresh: false, driverError: '' });
    try {
      let page = 0, total = 0;
      const candidates = [];
      do {
        const result = await api(`/driver-options?pageNo=${++page}&pageSize=100`);
        if (sequence !== this.driverSequence) return;
        total = Number(result.total);
        if (!Array.isArray(result.records) || !Number.isFinite(total)
            || (result.records.length === 0 && candidates.length < total))
          throw new Error('司机候选数据不完整，请重新刷新');
        candidates.push(...result.records);
      } while (candidates.length < total);
      const drivers = [...new Map(candidates.map(d => [String(d.id), { ...d,
        label: `${d.name} · ${d.phone} · ${d.driverType === 'INTERNAL' ? '自有' : '外协'}` }])).values()];
      const usable = d => d && d.status === 1 && (d.driverType === 'OUTSOURCED'
        || (d.driverType === 'INTERNAL' && d.currentVehicleId && d.currentVehiclePlateNo));
      const patch = { drivers, driverPage: page, driverTotal: total, driversFresh: true };
      const invalid = [];
      for (const [id, field, idField, plateField, label] of [
        [this.data.form.driverId, 'driver', 'form.driverId', 'form.outsourcedPlateNo', '派单司机'],
        [this.data.transferDriverId, 'transferDriver', 'transferDriverId', 'transferPlate', '接手司机']
      ]) {
        if (!id) continue;
        const current = drivers.find(d => String(d.id) === String(id));
        if (usable(current)) {
          patch[field] = current;
          if (current.driverType !== this.data[field]?.driverType) patch[plateField] = '';
        } else {
          patch[field] = null; patch[idField] = ''; patch[plateField] = '';
          invalid.push(label);
        }
      }
      if (invalid.length) patch.driverError = `${invalid.join('、')}已停用、不存在或无有效车辆绑定，请重新选择。`;
      this.setData(patch);
    } catch (e) {
      if (sequence === this.driverSequence) this.setData({ driversFresh: false,
        driverError: `刷新失败，当前司机信息尚未更新，暂不能派单或转单。${e.message || '请重试'}` });
    } finally {
      if (sequence === this.driverSequence) this.setData({ driversLoading: false });
    }
  },
  loadDrivers() { return this.refreshDrivers(); },
  async switchTab(e) {
    if (this.data.busy) return;
    this.setData({ tab: e.currentTarget.dataset.tab });
    if (this.data.tab === 'sent') await this.loadTasks();
  },
  input(e) { this.setData({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value }); },
  chooseTemplate(e) {
    const t = this.data.templates.find(t => String(t.stableCode) === String(e.detail.value));
    if (!t) return;
    this.setData({ template: t, 'form.templateStableCode': t.stableCode,
      'form.actualUnitPrice': String(t.defaultUnitPrice), 'form.actualInformationFee': String(t.defaultInformationFee),
      'form.initialSalary': String(t.defaultDriverSalary) });
  },
  chooseDriver(e) {
    const driver = this.data.drivers.find(d => String(d.id) === String(e.detail.value));
    this.setData({ driver, 'form.driverId': driver ? driver.id : '', 'form.outsourcedPlateNo': '' });
  },
  async publish() {
    if (this.data.busy || this.data.denied || !this.data.canPublish) return;
    if (!this.data.driversFresh || this.data.driversLoading) return this.setData({ driverError: '请先刷新司机候选。' });
    const f = this.data.form, d = this.data.driver;
    if (!f.businessDate || !f.templateStableCode || !d) return this.setData({ error: '请选择运输日期、线路模板和司机。' });
    if (d.driverType === 'INTERNAL' && !d.currentVehicleId) return this.setData({ error: '该自有司机未绑定公司车辆，请先在管理端绑定。' });
    if (d.driverType === 'OUTSOURCED' && !f.outsourcedPlateNo.trim()) return this.setData({ error: '请填写本次实际外协车牌。' });
    if (![f.actualUnitPrice, f.actualInformationFee, f.initialSalary].every(money))
      return this.setData({ error: '三项金额均须填写非负数字，最多两位小数，允许0。' });
    const batch = { businessDate: f.businessDate, rows: [{ templateStableCode: f.templateStableCode,
      driverId: d.id, outsourcedPlateNo: f.outsourcedPlateNo.trim() || null,
      actualUnitPrice: f.actualUnitPrice, actualInformationFee: f.actualInformationFee, initialSalary: f.initialSalary, remark: null }] };
    this.setData({ busy: true, error: '', notice: '' });
    try {
      const fingerprint = JSON.stringify(batch);
      if (this.pending?.fingerprint !== fingerprint) this.pending = { fingerprint, requestKey: key() };
      const preview = await api('/preview', batch);
      if (!preview.valid) throw new Error(preview.rows.flatMap(r => r.errors).join('；'));
      const row = preview.rows[0];
      if (!await confirm('核对并发布一趟任务', `${f.businessDate}\n${row.templateName}\n${d.name} ${d.phone}\n${row.vehiclePlate || d.currentVehiclePlateNo || f.outsourcedPlateNo}\n单价 ${f.actualUnitPrice}元/吨，信息费 ${f.actualInformationFee}元，工资 ${f.initialSalary}元`)) return;
      const result = await api('/confirm', { previewId: preview.previewId, requestKey: this.pending.requestKey, batch });
      const task = result.rows[0]; this.pending = null;
      this.setData({ tab: 'sent', statusIndex: 0, date: f.businessDate, highlight: String(task.taskId),
        notice: `已发布任务 ${task.taskNo}`, 'form.driverId': '', 'form.outsourcedPlateNo': '', driver: null });
      await this.loadTasks();
    } catch (e) { this.setData({ error: e.message }); }
    finally { this.setData({ busy: false }); }
  },
  async changeDate(e) { this.setData({ date: e.detail.value, highlight: '' }); await this.loadTasks(); },
  async changeStatus(e) {
    if (this.data.busy) return;
    this.setData({ statusIndex: Number(e.detail.value), highlight: '' });
    await this.loadTasks(1);
  },
  async openDetail(e) {
    if (this.data.busy || this.data.denied) return;
    const id = e.currentTarget.dataset.id;
    if (!this.data.tasks.some(t => String(t.id) === String(id))) return;
    this.setData({ detailOpen: true, detailId: String(id), detail: null });
    await this.loadDetail();
  },
  async loadDetail() {
    if (this.data.denied || !this.data.detailOpen) return;
    const sequence = this.detailSequence = (this.detailSequence || 0) + 1;
    this.setData({ detailLoading: true, detailError: '', detail: null });
    try {
      const result = await api(`/tasks/${this.data.detailId}`);
      if (sequence !== this.detailSequence) return;
      const value = v => v === null || v === undefined || v === '' ? '—' : String(v);
      this.setData({ detail: { ...result, ...result.task,
        statusLabel: routePresentation(result.task).statusLabel,
        actualUnitPrice: value(result.actualUnitPrice), actualInformationFee: value(result.actualInformationFee),
        grossWeight: value(result.grossWeight), tareWeight: value(result.tareWeight), netWeight: value(result.netWeight),
        completedAt: formatDateTime(result.completedAt) || '—' } });
    } catch (e) {
      if (sequence === this.detailSequence) this.setData({ detailError: e.message || '详情加载失败，请重试' });
    } finally { if (sequence === this.detailSequence) this.setData({ detailLoading: false }); }
  },
  closeDetail() {
    this.detailSequence = (this.detailSequence || 0) + 1;
    this.setData({ detailOpen: false, detail: null, detailError: '', detailLoading: false });
  },
  async loadTasks(page = 1) {
    // bindtap passes an event, whereas pagination passes a number.
    if (typeof page !== 'number') page = 1;
    const sequence = this.listSequence = (this.listSequence || 0) + 1;
    this.setData({ listError: '', listLoading: true });
    try {
      const result = await api(`/tasks?businessDate=${this.data.date}&status=${this.data.statusValues[this.data.statusIndex]}&pageNo=${page}&pageSize=20`);
      if (sequence !== this.listSequence) return;
      this.setData({ pageNo: page, total: result.total, tasks: result.records.map(t => ({ ...t,
        statusLabel: routePresentation(t).statusLabel, highlighted: String(t.id) === this.data.highlight })) });
      if (this.data.highlight && this.data.tasks.some(t => t.highlighted))
        wx.pageScrollTo({ selector: `#task-${this.data.highlight}`, duration: 200 });
    } catch (e) { if (sequence === this.listSequence) this.setData({ listError: e.message }); }
    finally { if (sequence === this.listSequence) this.setData({ listLoading: false }); }
  },
  previous() { if (this.data.pageNo > 1) this.loadTasks(this.data.pageNo - 1); },
  next() { if (this.data.pageNo * 20 < this.data.total) this.loadTasks(this.data.pageNo + 1); },
  async openAction(e) {
    if (this.data.busy) return;
    const task = this.data.tasks.find(t => String(t.id) === String(e.currentTarget.dataset.id));
    const type = e.currentTarget.dataset.type;
    if (!task || (type === 'transfer' ? !this.data.canTransfer : !this.data.canCancel)) return;
    this.setData({ busy: true, error: '' });
    try {
      const context = type === 'transfer' ? await api(`/tasks/${task.id}/transfer-context`) : null;
      this.setData({ action: { type, task }, reason: '', context,
        participants: context ? context.participants.map(p => ({ ...p, amount: String(p.salary) })) : [],
        transferDriver: null, transferDriverId: '', transferPlate: '', newSalary: '' });
      wx.pageScrollTo({ selector: '#action-panel', duration: 200 });
    } catch (e) { this.setData({ error: e.message }); }
    finally { this.setData({ busy: false }); }
  },
  actionInput(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value }); },
  salaryInput(e) { this.setData({ [`participants[${e.currentTarget.dataset.index}].amount`]: e.detail.value }); },
  chooseTransferDriver(e) {
    const d = this.data.drivers.find(d => String(d.id) === String(e.detail.value));
    this.setData({ transferDriver: d, transferDriverId: d ? d.id : '', transferPlate: '' });
  },
  closeAction() { if (!this.data.busy) this.setData({ action: null }); },
  async submitAction() {
    if (this.data.busy || !this.data.action) return;
    const { type, task } = this.data.action;
    if (!this.data.reason.trim()) return this.setData({ error: '请填写操作原因。' });
    let body;
    if (type === 'transfer') {
      if (!this.data.driversFresh || this.data.driversLoading) return this.setData({ driverError: '请先刷新司机候选。' });
      const d = this.data.transferDriver, c = this.data.context;
      if (!d || (d.driverType === 'INTERNAL' && !d.currentVehicleId) ||
          (d.driverType === 'OUTSOURCED' && !this.data.transferPlate.trim()))
        return this.setData({ error: '请选择合法接手司机；自有须绑定车辆，外协须填写实际车牌。' });
      if (![this.data.newSalary, ...this.data.participants.map(p => p.amount)].every(money))
        return this.setData({ error: '请核定全部参与人工资，金额非负且最多两位小数。' });
      body = { newDriverId: d.id, outsourcedPlateNo: this.data.transferPlate.trim() || null,
        newSalary: this.data.newSalary, allParticipantSalaries: this.data.participants.map(p => ({ participantId: p.id, amount: p.amount })),
        reason: this.data.reason.trim(), expectedVersion: c.expectedVersion, expectedCurrentParticipantId: c.expectedCurrentParticipantId };
    } else body = { reason: this.data.reason.trim(), expectedVersion: task.version,
      requestKey: this.cancelKey || (this.cancelKey = key()) };
    this.setData({ busy: true, error: '' });
    try {
      const detail = type === 'transfer' ? `转给 ${this.data.transferDriver.name}，车牌 ${this.data.transferDriver.driverType === 'OUTSOURCED' ? this.data.transferPlate : this.data.transferDriver.currentVehiclePlateNo}，接手工资 ${this.data.newSalary}元` : '取消后该任务不能发车';
      if (!await confirm(type === 'transfer' ? '确认转单' : '确认取消任务', `${task.taskNo}\n${detail}\n原因：${this.data.reason}`)) return;
      await api(`/tasks/${task.id}/${type}`, body);
      this.cancelKey = null;
      this.setData({ action: null, notice: type === 'transfer' ? '转单成功，等待接手司机发车' : '任务已取消', highlight: String(task.id) });
      await this.loadTasks(this.data.pageNo);
    } catch (e) { this.setData({ error: e.message }); }
    finally { this.setData({ busy: false }); }
  }
});
