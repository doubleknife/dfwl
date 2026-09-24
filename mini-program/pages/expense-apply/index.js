const { request, uploadFile } = require('../../utils/request');
const { hasPermission, isOutsourcedDriver } = require('../../utils/permissions');
const { availableTemplates, launchReason, allowedAttributions, newItem,
  buildExpensePayload } = require('../../utils/approvalApplication');

const ENERGY_TYPES = ['ELECTRIC', 'GAS', 'TEMP_ELECTRIC'];

Page({
  data: {
    user: null, templates: [], templateIndex: -1, template: null, launchReason: '', canCompose: false,
    attributionTypes: [], attributionType: '', routes: [], routeId: '', vehicles: [], drivers: [],
    items: [], nextItemSequence: 1, imagePath: '', evidence: [], uploadStatus: '',
    loading: false, uploading: false, error: '', createdApprovalId: null, energyTemplate: false
  },
  onLoad() { this.load(); },
  async load() {
    try {
      const user = await request({ url: '/me' });
      if (!hasPermission(user, 'approval:create') || isOutsourcedDriver(user)) {
        this.setData({ error: '当前账号无发起审批权限' });
        return;
      }
      const templates = availableTemplates(await request({ url: '/approval-templates/available' }));
      this.setData({ user, templates, error: templates.length ? '' : '当前没有可发起的审批模板' });
    } catch (error) {
      this.setData({ error: error.message || '审批模板加载失败' });
    }
  },
  async chooseTemplate(e) {
    const index = Number(e.detail.value);
    const template = this.data.templates[index];
    const reason = launchReason(template);
    const attributionTypes = allowedAttributions(template);
    const attributionType = attributionTypes.length === 1 ? attributionTypes[0] : '';
    const itemNo = `m${Date.now().toString(36)}_${this.data.nextItemSequence}`;
    const item = newItem(itemNo, this.data.user);
    if (ENERGY_TYPES.includes(template?.expenseType)) item.energyType = template.expenseType;
    this.setData({ templateIndex: index, template, launchReason: reason, canCompose: !reason, attributionTypes,
      attributionType, routeId: '', items: reason ? [] : [item],
      nextItemSequence: this.data.nextItemSequence + 1, evidence: [], imagePath: '',
      uploadStatus: '', error: '', createdApprovalId: null,
      energyTemplate: ENERGY_TYPES.includes(template?.expenseType) });
    if (!reason) await this.loadResources();
  },
  async loadResources() {
    const user = this.data.user || {};
    try {
      const calls = [];
      const keys = [];
      if (this.data.attributionTypes.includes('ROUTE')) {
        if (!hasPermission(user, 'route:list')) throw new Error('缺少线路查询权限，无法选择线路');
        keys.push('routes');
        calls.push(request({ url: '/routes?pageNo=1&pageSize=100' }));
      }
      if (!user.driverId) {
        if (!hasPermission(user, 'vehicle:list')) throw new Error('缺少车辆查询权限，无法选择车辆');
        keys.push('vehicles');
        calls.push(request({ url: '/vehicles?pageNo=1&pageSize=100' }));
        if (hasPermission(user, 'driver:list')) {
          keys.push('drivers');
          calls.push(request({ url: '/drivers?pageNo=1&pageSize=100' }));
        }
      }
      const results = await Promise.all(calls);
      const next = {};
      results.forEach((page, at) => { next[keys[at]] = page.records || []; });
      this.setData(next);
    } catch (error) {
      this.setData({ error: error.message || '业务资料加载失败' });
    }
  },
  chooseAttribution(e) {
    this.setData({ attributionType: this.data.attributionTypes[Number(e.detail.value)], routeId: '' });
  },
  chooseRoute(e) {
    const route = this.data.routes[Number(e.detail.value)];
    this.setData({ routeId: route ? route.id : '' });
  },
  addItem() {
    const itemNo = `m${Date.now().toString(36)}_${this.data.nextItemSequence}`;
    const item = newItem(itemNo, this.data.user);
    if (this.data.energyTemplate) item.energyType = this.data.template.expenseType;
    this.setData({ items: [...this.data.items, item], nextItemSequence: this.data.nextItemSequence + 1 });
  },
  removeItem(e) {
    const index = Number(e.currentTarget.dataset.index);
    this.setData({ items: this.data.items.filter((_, at) => at !== index) });
  },
  setItemField(e) {
    const index = Number(e.currentTarget.dataset.index);
    const field = e.currentTarget.dataset.field;
    if (!this.data.items[index] || !['amount', 'businessDate', 'remark', 'orderNo',
      'startTime', 'quantity', 'stationId'].includes(field)) return;
    this.setData({ [`items[${index}].${field}`]: e.detail.value });
  },
  chooseVehicle(e) {
    const index = Number(e.currentTarget.dataset.index);
    const vehicle = this.data.vehicles[Number(e.detail.value)];
    if (vehicle && this.data.items[index]) this.setData({ [`items[${index}].vehicleId`]: vehicle.id });
  },
  chooseDriver(e) {
    const index = Number(e.currentTarget.dataset.index);
    const driver = this.data.drivers[Number(e.detail.value)];
    if (driver && this.data.items[index]) this.setData({ [`items[${index}].driverId`]: driver.id });
  },
  clearDriver(e) {
    const index = Number(e.currentTarget.dataset.index);
    if (this.data.items[index]) this.setData({ [`items[${index}].driverId`]: '' });
  },
  chooseImage() {
    wx.chooseMedia({ count: 1, mediaType: ['image'], sourceType: ['album', 'camera'],
      success: (res) => this.setData({ imagePath: res.tempFiles[0].tempFilePath, uploadStatus: '已选择图片' }) });
  },
  async uploadEvidence() {
    if (this.data.uploading || !this.data.imagePath) return;
    const user = this.data.user;
    if (!user?.id) throw new Error('当前账号信息缺失');
    this.setData({ uploading: true, uploadStatus: '上传中' });
    try {
      const file = await uploadFile({ url: '/attachments', filePath: this.data.imagePath,
        formData: { ownerType: 'APPROVAL_UPLOAD', ownerId: user.id, purpose: 'APPROVAL_APPLICATION' } });
      this.setData({ evidence: [...this.data.evidence, file], imagePath: '', uploadStatus: '上传完成' });
    } catch (error) {
      const message = error.message || '图片上传失败';
      this.setData({ error: message, uploadStatus: message });
    } finally {
      this.setData({ uploading: false });
    }
  },
  removeEvidence(e) {
    const index = Number(e.currentTarget.dataset.index);
    this.setData({ evidence: this.data.evidence.filter((_, at) => at !== index) });
  },
  async submit() {
    if (this.data.loading || this.data.uploading) return;
    let payload;
    try {
      payload = buildExpensePayload(this.data.template, this.data.attributionType, this.data.routeId,
        this.data.items, this.data.evidence.map((file) => file.id));
    } catch (error) {
      this.setData({ error: error.message });
      wx.showToast({ title: error.message, icon: 'none' });
      return;
    }
    this.setData({ loading: true, error: '' });
    try {
      const approval = await request({ url: '/approvals', method: 'POST', data: payload });
      this.setData({ createdApprovalId: approval.id });
      wx.showToast({ title: '申请已提交' });
      wx.navigateTo({ url: `/pages/approval-detail/index?id=${approval.id}` });
    } catch (error) {
      this.setData({ error: error.message || '提交失败' });
    } finally {
      this.setData({ loading: false });
    }
  }
});
