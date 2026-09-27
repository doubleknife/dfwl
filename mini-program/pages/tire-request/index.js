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
    uploadStatus: '', step: 1, working: false, uploadProgress: null, mediaError: ''
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
        this.setData({ imagePath: res.tempFiles[0].tempFilePath, uploadStatus: '图片已选择',
          attachmentId: null, ocrRecordId: null, tireId: '', ocrCandidates: [], step: 1, mediaError: '' });
      }
    });
  },
  async uploadImage() {
    if (!this.data.imagePath) {
      wx.showToast({ title: '请选择图片', icon: 'none' });
      return;
    }
    this.setData({ uploadStatus: '图片上传中', mediaError: '', uploadProgress: null });
    try {
      const file = await uploadFile({
        url: '/attachments', filePath: this.data.imagePath,
        formData: { ownerType: 'TIRE_OCR', ownerId: 0, purpose: 'TIRE_OCR' },
        onProgress: (percent) => this.setData({ uploadProgress: percent })
      });
      this.setData({ attachmentId: file.id, uploadStatus: '图片上传成功' });
      return file.id;
    } catch (error) {
      this.setData({ uploadStatus: '图片上传失败，可重试', mediaError: error.message || '请检查网络后重试' });
      return null;
    }
  },
  async recognize() {
    if (this.data.working || !this.data.imagePath) return;
    this.setData({ working: true, step: 2, ocrError: '', mediaError: '' });
    try {
      const attachmentId = this.data.attachmentId || await this.uploadImage();
      if (!attachmentId) return;
      this.setData({ uploadStatus: '正在识别胎号' });
      const ocr = await request({ url: '/ocr/tire-number', method: 'POST',
        data: { attachmentId: Number(attachmentId) } });
      const candidates = ocr.candidates || [];
      const nextTireNo = candidates.length ? candidates[0].candidate : this.data.confirmedTireNo;
      this.setData({ ocrRecordId: ocr.id, ocrCandidates: candidates, ocrStatus: ocr.ocrStatus,
        ocrError: ocr.errorMessage || '', confirmedTireNo: nextTireNo, tireId: '', matchStatus: '',
        uploadStatus: candidates.length ? '识别完成，请确认胎号' : '未识别到胎号，可手动填写', step: 3 });
    } catch (error) {
      this.setData({ ocrError: error.message || '识别失败，可重试', uploadStatus: '识别失败，可重试' });
    } finally { this.setData({ working: false }); }
  },
  async confirmOcr() {
    if (this.data.working) return;
    if (!this.data.ocrRecordId) {
      wx.showToast({ title: '请先识别或上传图片', icon: 'none' });
      return;
    }
    if (!this.data.confirmedTireNo) {
      wx.showToast({ title: '请输入胎号', icon: 'none' });
      return;
    }
    this.setData({ working: true, step: 4, matchStatus: '正在匹配库存轮胎', mediaError: '' });
    try {
      const confirmed = await request({ url: `/ocr/${this.data.ocrRecordId}/confirm`,
        method: 'POST', data: { confirmedText: this.data.confirmedTireNo } });
      this.setData({ ocrRecordId: confirmed.id, tireId: '' });
      const target = await request({
        url: `/tires/request-target?tireNumber=${encodeURIComponent(confirmed.confirmedText)}`
      });
      this.setData({ tireId: target.tireId, confirmedTireNo: target.tireNumber,
        matchStatus: `已匹配可申请库存轮胎 ${target.tireNumber}`, uploadStatus: '胎号已确认', step: 5 });
    } catch (error) {
      this.setData({ tireId: '', matchStatus: error.message || '未找到可申请的库存轮胎', step: 4 });
    } finally { this.setData({ working: false }); }
  },
  retryMatch() {
    this.setData({ step: 3, matchStatus: '' });
    this.confirmOcr();
  },
  backToPhoto() {
    this.setData({ step: 1, tireId: '', confirmedTireNo: '', ocrRecordId: null,
      attachmentId: null, ocrCandidates: [], imagePath: '', matchStatus: '', uploadStatus: '' });
  },
  currentItem() {
    if (!this.data.tireId || !this.data.confirmedTireNo || !this.data.ocrRecordId) return null;
    return { tireId: Number(this.data.tireId), confirmedTireNo: this.data.confirmedTireNo,
      ocrRecordId: this.data.ocrRecordId };
  },
  addItem() {
    if (!this.currentItem()) {
      wx.showToast({ title: '请先确认胎号并匹配库存轮胎', icon: 'none' });
      return;
    }
    const exists = this.data.items.some((item) => String(item.tireId) === String(this.data.tireId));
    if (exists) {
      wx.showToast({ title: '该轮胎已添加', icon: 'none' });
      return;
    }
    this.setData({ items: this.data.items.concat(this.currentItem()) });
    this.backToPhoto();
  },
  async submit() {
    if (this.data.loading) return;
    const current = this.currentItem();
    const items = current ? this.data.items.concat(current) : this.data.items;
    if (!items.length || !this.data.vehicleId) {
      wx.showToast({ title: '请先确认胎号与当前车辆', icon: 'none' });
      return;
    }
    this.setData({ loading: true, error: '' });
    try {
      await request({
        url: '/tire-requests',
        method: 'POST',
        data: {
          driverId: Number(this.data.driver.id),
          vehicleId: Number(this.data.vehicleId),
          items
        }
      });
      wx.showToast({ title: '已提交' });
      this.setData({ items: [] });
      this.backToPhoto();
      await this.load();
    } catch (error) {
      this.setData({ error: error.message || '提交失败，请检查网络后重试' });
    } finally {
      this.setData({ loading: false });
    }
  }
});
