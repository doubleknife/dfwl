const { request, uploadFile } = require('../../utils/request');
const { hasPermission, isInternalDriver } = require('../../utils/permissions');

Page({
  data: {
    driver: null,
    canRequest: false,
    error: '',
    vehicleId: '',
    requests: [],
    tireId: '',
    confirmedTireNo: '',
    imagePath: '',
    attachmentId: null,
    ocrRecordId: null,
    ocrCandidates: [],
    ocrStatus: '',
    ocrError: '',
    matchStatus: '',
    items: [],
    loading: false,
    uploadStatus: ''
  },
  onShow() { this.load(); },
  async load() {
    try {
      const user = await request({ url: '/me' });
      const driver = user.driverId ? {
        id: user.driverId,
        name: user.driverName,
        driverType: user.driverType,
        currentVehicleId: user.currentVehicleId,
        currentPlateNo: user.currentPlateNo
      } : null;
      const canRequest = isInternalDriver(driver) && hasPermission(user, 'tire:request');
      this.setData({ driver, canRequest,
        vehicleId: canRequest ? driver.currentVehicleId || '' : '',
        error: canRequest ? '' : '当前账号无换胎申请权限' });
    } catch (error) {
      this.setData({ canRequest: false, error: error.message || '账号信息加载失败' });
    }
  },
  onVehicle(e) { this.setData({ vehicleId: e.detail.value }); },
  onTireNo(e) { this.setData({ confirmedTireNo: e.detail.value, tireId: '', matchStatus: '' }); },
  chooseCandidate(e) {
    const candidate = this.data.ocrCandidates[e.currentTarget.dataset.index];
    if (candidate) this.setData({ confirmedTireNo: candidate.candidate, tireId: '', matchStatus: '' });
  },
  async chooseImage() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        this.setData({ imagePath: res.tempFiles[0].tempFilePath, uploadStatus: '已选择图片' });
      }
    });
  },
  async uploadImage() {
    if (!this.data.imagePath) {
      wx.showToast({ title: '请选择图片', icon: 'none' });
      return;
    }
    this.setData({ uploadStatus: '上传中' });
    const file = await uploadFile({
      url: '/attachments',
      filePath: this.data.imagePath,
      formData: {
        ownerType: 'TIRE_OCR',
        ownerId: 0,
        purpose: 'TIRE_OCR'
      }
    });
    this.setData({ attachmentId: file.id, uploadStatus: '上传完成' });
  },
  async recognize() {
    if (!this.data.attachmentId) await this.uploadImage();
    if (!this.data.attachmentId) return;
    this.setData({ uploadStatus: '正在识别', ocrError: '' });
    const ocr = await request({
      url: '/ocr/tire-number',
      method: 'POST',
      data: { attachmentId: Number(this.data.attachmentId) }
    });
    const candidates = ocr.candidates || [];
    const nextTireNo = candidates.length ? candidates[0].candidate : this.data.confirmedTireNo;
    this.setData({
      ocrRecordId: ocr.id,
      ocrCandidates: candidates,
      ocrStatus: ocr.ocrStatus,
      ocrError: ocr.errorMessage || '',
      confirmedTireNo: nextTireNo,
      tireId: '', matchStatus: '',
      uploadStatus: candidates.length ? `识别到胎号：${nextTireNo}` : '未识别到胎号，可手动输入'
    });
  },
  async confirmOcr() {
    if (!this.data.ocrRecordId) {
      wx.showToast({ title: '请先识别或上传图片', icon: 'none' });
      return;
    }
    if (!this.data.confirmedTireNo) {
      wx.showToast({ title: '请输入胎号', icon: 'none' });
      return;
    }
    const confirmed = await request({
      url: `/ocr/${this.data.ocrRecordId}/confirm`,
      method: 'POST',
      data: { confirmedText: this.data.confirmedTireNo }
    });
    this.setData({ ocrRecordId: confirmed.id, tireId: '', matchStatus: '正在匹配库存轮胎' });
    try {
      const target = await request({
        url: `/tires/request-target?tireNumber=${encodeURIComponent(confirmed.confirmedText)}`
      });
      this.setData({ tireId: target.tireId, confirmedTireNo: target.tireNumber,
        matchStatus: `已匹配库存轮胎 ${target.tireNumber}`, uploadStatus: '胎号已确认' });
    } catch (error) {
      this.setData({ tireId: '', matchStatus: error.message || '未找到可申请的库存轮胎' });
    }
  },
  addItem() {
    if (!this.data.tireId || !this.data.confirmedTireNo) {
      wx.showToast({ title: '请先确认胎号并匹配库存轮胎', icon: 'none' });
      return;
    }
    const exists = this.data.items.some((item) => String(item.tireId) === String(this.data.tireId));
    if (exists) {
      wx.showToast({ title: '该轮胎已添加', icon: 'none' });
      return;
    }
    this.setData({
      items: this.data.items.concat({
        tireId: Number(this.data.tireId),
        confirmedTireNo: this.data.confirmedTireNo,
        ocrRecordId: this.data.ocrRecordId
      }),
      tireId: '',
      confirmedTireNo: '',
      imagePath: '',
      attachmentId: null,
      ocrRecordId: null,
      ocrCandidates: [],
      ocrStatus: '',
      ocrError: '',
      matchStatus: '',
      uploadStatus: ''
    });
  },
  async submit() {
    if (!this.data.items.length) {
      wx.showToast({ title: '请添加轮胎', icon: 'none' });
      return;
    }
    this.setData({ loading: true });
    try {
      await request({
        url: '/tire-requests',
        method: 'POST',
        data: {
          driverId: Number(this.data.driver.id),
          vehicleId: Number(this.data.vehicleId),
          items: this.data.items
        }
      });
      wx.showToast({ title: '已提交' });
      this.setData({ items: [] });
      await this.load();
    } finally {
      this.setData({ loading: false });
    }
  }
});
