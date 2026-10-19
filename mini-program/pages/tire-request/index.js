const { request, uploadFile } = require('../../utils/request');
const { hasPermission } = require('../../utils/permissions');

Page({
  data: { loading: false, busy: false, error: '', denied: false, stage: 'PHOTO',
    templates: [], templateIndex: -1, driverName: '', plateNo: '', photoPath: '', photoAttachmentId: null,
    ocrRecordId: null, recognizedText: '', ocrHint: '', confirmedTireNo: '', match: null, requestKey: '', result: null },
  onShow() { if (!this.data.result) this.load(); },
  async load() {
    this.setData({ loading: true, error: '' });
    try {
      const user = await request({ url: '/me' });
      if (user.driverType !== 'INTERNAL' || !hasPermission(user, 'BUTTON_MINI_TIRE_REQUEST_SUBMIT')
          || !user.currentVehicleId) {
        this.setData({ denied: true, error: '当前账号没有换胎申请资格或未绑定公司车辆' }); return;
      }
      const templates = await request({ url: '/approval-templates/available' });
      const available = (templates || []).filter((item) => item.business_type === 'TIRE_REPLACEMENT');
      this.setData({ templates: available, driverName: user.driverName || user.name || '本人',
        plateNo: user.currentPlateNo || '当前公司车辆', denied: false,
        error: available.length ? '' : '当前没有可用的换胎审批流程，请联系管理员' });
    } catch (error) { this.setData({ error: error.message || '换胎申请加载失败，请重试' }); }
    finally { this.setData({ loading: false }); }
  },
  async takePhoto() {
    if (this.data.busy) return;
    try {
      const selection = await new Promise((resolve, reject) => wx.chooseMedia({
        count: 1, mediaType: ['image'], sourceType: ['camera', 'album'],
        success: resolve, fail: reject
      }));
      const path = selection.tempFiles && selection.tempFiles[0] && selection.tempFiles[0].tempFilePath;
      if (!path) return;
      this.setData({ busy: true, error: '', photoPath: path, photoAttachmentId: null,
        ocrRecordId: null, recognizedText: '', ocrHint: '', confirmedTireNo: '', match: null, stage: 'PHOTO' });
      const uploaded = await uploadFile({ url: '/attachments', filePath: path,
        formData: { ownerType: 'TIRE_OCR', ownerId: '0', purpose: 'TIRE_OCR' } });
      this.setData({ photoAttachmentId: uploaded.id });
      const ocr = await request({ url: '/tire-requests/ocr', method: 'POST', data: { attachmentId: uploaded.id } });
      this.setData({ ocrRecordId: ocr.id, recognizedText: ocr.candidateText || '',
        ocrHint: ocr.ocrStatus === 'FAILED' ? '识别暂不可用，请照着照片手动核对胎号。'
          : ocr.candidateText ? '请对照照片核对识别结果。' : '未识别出胎号，请照着照片手动填写。',
        confirmedTireNo: ocr.candidateText || '', stage: 'CONFIRM' });
    } catch (error) {
      this.setData({ error: error.message || '照片上传或识别失败，请重试' });
    } finally { this.setData({ busy: false }); }
  },
  onTireNo(event) { this.setData({ confirmedTireNo: event.detail.value, match: null }); },
  async confirmNumber() {
    if (this.data.busy || !this.data.ocrRecordId || !this.data.confirmedTireNo.trim()) {
      this.setData({ error: '请先拍照并确认胎号' }); return;
    }
    this.setData({ busy: true, error: '' });
    try {
      const number = this.data.confirmedTireNo.trim();
      await request({ url: `/tire-requests/ocr/${this.data.ocrRecordId}/confirm`, method: 'POST',
        data: { confirmedText: number } });
      const match = await request({ url: `/tires/request-target?tireNumber=${encodeURIComponent(number)}` });
      this.setData({ match, confirmedTireNo: number, stage: 'FORM' });
    } catch (error) { this.setData({ error: error.message || '没有找到可用轮胎，请核对胎号' }); }
    finally { this.setData({ busy: false }); }
  },
  onTemplate(event) { this.setData({ templateIndex: Number(event.detail.value), error: '' }); },
  review() {
    if (!this.data.match || this.data.templateIndex < 0) {
      this.setData({ error: '请选择换胎审批流程并确认库存轮胎' }); return;
    }
    this.setData({ stage: 'REVIEW', error: '',
      requestKey: this.data.requestKey || `tire-${Date.now()}-${Math.random().toString(36).slice(2)}` });
  },
  backForm() { this.setData({ stage: 'FORM', error: '' }); },
  async submit() {
    if (this.data.busy || this.data.stage !== 'REVIEW') return;
    this.setData({ busy: true, error: '' });
    try {
      const result = await request({ url: '/tire-requests', method: 'POST', data: {
        templateId: this.data.templates[this.data.templateIndex].id,
        ocrRecordId: this.data.ocrRecordId, confirmedTireNo: this.data.confirmedTireNo,
        requestKey: this.data.requestKey
      } });
      this.setData({ result, stage: 'DONE' });
    } catch (error) { this.setData({ error: error.message || '提交失败，请重试；不要重复拍照' }); }
    finally { this.setData({ busy: false }); }
  },
  previewPhoto() {
    if (this.data.photoPath) wx.previewImage({ current: this.data.photoPath, urls: [this.data.photoPath] });
  }
});
