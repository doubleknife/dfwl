const { request, uploadFile } = require('../../utils/request');

const expenseTypes = ['PENALTY', 'TEMP_ELECTRIC', 'REPAIR', 'WATER', 'TOLL'];

Page({
  data: {
    expenseTypes,
    routes: [],
    vehicles: [],
    form: {
      expenseType: 'PENALTY',
      businessDate: '',
      vehicleId: '',
      attributionType: 'DAILY',
      routeId: '',
      amount: '',
      remark: ''
    },
    imagePath: '',
    createdApprovalId: null,
    uploadStatus: '',
    loading: false
  },
  onShow() { this.load(); },
  async load() {
    const [routes, vehicles] = await Promise.all([
      request({ url: '/routes?pageNo=1&pageSize=100' }).catch(() => ({ records: [] })),
      request({ url: '/vehicles?pageNo=1&pageSize=100' }).catch(() => ({ records: [] }))
    ]);
    this.setData({ routes: routes.records || [], vehicles: vehicles.records || [] });
  },
  setField(e) {
    this.setData({ [`form.${e.currentTarget.dataset.field}`]: e.detail.value });
  },
  chooseType(e) {
    this.setData({ 'form.expenseType': expenseTypes[e.detail.value] });
  },
  chooseVehicle(e) {
    const vehicle = this.data.vehicles[e.detail.value];
    this.setData({ 'form.vehicleId': vehicle ? vehicle.id : '' });
  },
  chooseRoute(e) {
    const route = this.data.routes[e.detail.value];
    this.setData({ 'form.routeId': route ? route.id : '' });
  },
  chooseAttribution(e) {
    this.setData({ 'form.attributionType': e.detail.value === '0' ? 'DAILY' : 'ROUTE' });
  },
  chooseImage() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => this.setData({ imagePath: res.tempFiles[0].tempFilePath, uploadStatus: '已选择图片' })
    });
  },
  async uploadAfterCreate(approvalId) {
    if (!this.data.imagePath || !approvalId) return null;
    this.setData({ uploadStatus: '上传中' });
    const file = await uploadFile({
      url: '/attachments',
      filePath: this.data.imagePath,
      formData: { ownerType: 'APPROVAL', ownerId: approvalId, purpose: 'APPROVAL_APPLICATION' }
    });
    this.setData({ uploadStatus: '上传完成' });
    return file;
  },
  async submit() {
    const form = this.data.form;
    if (form.attributionType === 'ROUTE' && !form.routeId) {
      wx.showToast({ title: '线路费用必须选择线路', icon: 'none' });
      return;
    }
    this.setData({ loading: true });
    try {
      const snapshot = {
        expenseType: form.expenseType,
        businessDate: form.businessDate,
        vehicleId: Number(form.vehicleId),
        attributionType: form.attributionType,
        routeId: form.attributionType === 'ROUTE' ? Number(form.routeId) : null,
        amount: form.amount,
        remark: form.remark,
        sourceType: 'APPROVAL'
      };
      const approval = await request({
        url: '/approvals',
        method: 'POST',
        data: {
          approvalType: 'EXPENSE',
          businessType: 'EXPENSE',
          businessId: null,
          businessSnapshot: snapshot
        }
      });
      this.setData({ createdApprovalId: approval.id });
      try {
        const file = await this.uploadAfterCreate(approval.id);
        if (file) wx.showToast({ title: '申请已提交，图片已上传' });
        else wx.showToast({ title: '申请已提交' });
      } catch (error) {
        wx.showToast({ title: '申请已提交，图片上传失败', icon: 'none' });
      }
    } finally {
      this.setData({ loading: false });
    }
  }
});
