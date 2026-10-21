const { request, uploadFile, downloadAttachment } = require('../../utils/request');
const { hasPermission } = require('../../utils/permissions');

Page({
  data: { loading: false, busy: false, error: '', denied: false, stage: 'FLOW',
    templates: [], templateIndex: -1, driverName: '', plateNo: '', items: [],
    candidateOptions: [], candidatePickerOpen: false, candidateDraft: '', candidateError: '',
    pendingPhoto: null, editingIndex: -1, requestKey: '', result: null, allValid: false,
    resubmitId: null, expectedVersion: null },
  onLoad(options) { if (options && options.resubmitId) this.setData({ resubmitId: Number(options.resubmitId) }); },
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
      if (this.data.resubmitId) {
        const detail = await request({ url: `/approvals/${this.data.resubmitId}` });
        if (detail.approval.status !== 'RETURNED_TO_APPLICANT'
            || detail.approval.business_type !== 'TIRE_REPLACEMENT'
            || Number(detail.approval.applicant_user_id) !== Number(user.id))
          throw new Error('当前换胎申请不允许重新提交');
        const template = available.find((item) => Number(item.id) === Number(detail.approval.template_id))
          || { id: detail.approval.template_id, name: detail.approval.template_name };
        const items = [];
        for (const tire of detail.tireRequests || []) {
          items.push({ photoPath: await downloadAttachment(tire.photoAttachmentId),
            attachmentId: tire.photoAttachmentId, ocrRecordId: tire.ocrRecordId,
            confirmedTireNo: tire.tireNo, valid: false, needsUpload: true, message: '请重新上传照片' });
        }
        this.setData({ templates: [template], templateIndex: 0, stage: 'ITEMS', items,
          expectedVersion: detail.approval.version, plateNo: items.length
            ? detail.tireRequests[0].vehiclePlate : this.data.plateNo, error: '' });
      }
    } catch (error) { this.setData({ error: error.message || '换胎申请加载失败，请重试' }); }
    finally { this.setData({ loading: false }); }
  },
  onTemplate(event) { this.setData({ templateIndex: Number(event.detail.index), error: '' }); },
  continueToPhotos() {
    if (this.data.templateIndex < 0) { this.setData({ error: '请先选择换胎审批流程' }); return; }
    this.setData({ stage: 'ITEMS', error: '' });
  },
  cancel() {
    if (this.data.busy) return;
    wx.navigateBack({ delta: 1, fail: () => wx.reLaunch({ url: '/pages/home/index' }) });
  },
  async takePhoto(event) {
    if (this.data.busy) return;
    const editingIndex = event.currentTarget && event.currentTarget.dataset.index !== undefined
      ? Number(event.currentTarget.dataset.index) : -1;
    if (editingIndex < 0 && this.data.items.length >= 20) {
      this.setData({ error: '一次申请最多上传 20 张照片' }); return;
    }
    try {
      const selection = await new Promise((resolve, reject) => wx.chooseMedia({
        count: 1, mediaType: ['image'], sourceType: ['camera', 'album'], success: resolve, fail: reject
      }));
      const path = selection.tempFiles && selection.tempFiles[0] && selection.tempFiles[0].tempFilePath;
      if (!path) return;
      this.setData({ busy: true, error: '' });
      const uploaded = await uploadFile({ url: '/attachments', filePath: path,
        formData: { ownerType: 'TIRE_OCR', ownerId: '0', purpose: 'TIRE_OCR' } });
      const ocr = await request({ url: '/tire-requests/ocr', method: 'POST', data: { attachmentId: uploaded.id } });
      const options = (Array.isArray(ocr.candidates) ? ocr.candidates : [])
        .filter((item) => item && typeof item.candidate === 'string' && item.candidate.trim()).slice(0, 8);
      this.setData({ pendingPhoto: { photoPath: path, attachmentId: uploaded.id, ocrRecordId: ocr.id },
        editingIndex, candidateOptions: options, candidatePickerOpen: true, candidateDraft: '', candidateError: '',
        allValid: false });
    } catch (error) { this.setData({ error: error.message || '照片上传或识别失败，请重试' }); }
    finally { this.setData({ busy: false }); }
  },
  chooseCandidate(event) {
    const item = this.data.candidateOptions[Number(event.currentTarget.dataset.index)];
    if (item) this.setData({ candidateDraft: item.candidate, candidateError: '' });
  },
  onCandidateDraft(event) { this.setData({ candidateDraft: event.detail.value, candidateError: '' }); },
  closeCandidatePicker() { this.setData({ candidatePickerOpen: false, pendingPhoto: null, candidateError: '' }); },
  confirmCandidate() {
    const number = String(this.data.candidateDraft || '').trim().toUpperCase();
    if (!number) { this.setData({ candidateError: '请选择或填写照片标签上的胎号' }); return; }
    const photo = this.data.pendingPhoto;
    if (!photo) return;
    const items = this.data.items.slice();
    const item = { ...photo, confirmedTireNo: number, valid: false, needsUpload: false, message: '' };
    if (this.data.editingIndex >= 0) items[this.data.editingIndex] = item;
    else items.push(item);
    this.setData({ items, candidatePickerOpen: false, pendingPhoto: null, candidateError: '',
      allValid: false, error: '', requestKey: '' });
  },
  onItemNumber(event) {
    const index = Number(event.currentTarget.dataset.index);
    const items = this.data.items.slice();
    items[index] = { ...items[index], confirmedTireNo: event.detail.value, valid: false,
      message: items[index].needsUpload ? '请重新上传照片' : '' };
    this.setData({ items, allValid: false, requestKey: '' });
  },
  removeItem(event) {
    const items = this.data.items.slice();
    items.splice(Number(event.currentTarget.dataset.index), 1);
    this.setData({ items, allValid: false, requestKey: '', error: '' });
  },
  previewPhoto(event) {
    const item = this.data.items[Number(event.currentTarget.dataset.index)];
    if (item) wx.previewImage({ current: item.photoPath, urls: this.data.items.map((row) => row.photoPath) });
  },
  payload() {
    return { templateId: this.data.templates[this.data.templateIndex].id,
      items: this.data.items.map((item) => ({ ocrRecordId: item.ocrRecordId,
        confirmedTireNo: String(item.confirmedTireNo || '').trim().toUpperCase() })),
      requestKey: this.data.requestKey || `tire-${Date.now()}-${Math.random().toString(36).slice(2)}` };
  },
  async validateItems() {
    if (!this.data.items.length) { this.setData({ error: '请至少上传一张轮胎照片' }); return false; }
    try {
      const checked = await request({ url: '/tire-requests/validate', method: 'POST', data: this.payload() });
      const items = this.data.items.map((item, index) => ({ ...item,
        confirmedTireNo: String(item.confirmedTireNo || '').trim().toUpperCase(),
        valid: checked.rows[index].valid, message: checked.rows[index].message }));
      this.setData({ items, allValid: checked.allValid,
        error: checked.allValid ? '' : '部分胎号未通过库存校验，请按每条提示修改、删除或重新上传照片' });
      return checked.allValid;
    } catch (error) { this.setData({ allValid: false, error: error.message || '库存校验失败，请重试' }); return false; }
  },
  async submit() {
    if (this.data.busy) return;
    if (this.data.templateIndex < 0) { this.setData({ error: '请先选择换胎审批流程' }); return; }
    if (!this.data.items.length) { this.setData({ error: '请至少上传一张轮胎照片' }); return; }
    if (this.data.resubmitId && this.data.items.some((item) => item.needsUpload)) {
      this.setData({ items: this.data.items.map((item) => item.needsUpload
        ? { ...item, valid: false, message: '请重新上传照片' } : item),
      error: '重新提交必须为每个胎号重新上传照片' });
      return;
    }
    const requestKey = this.data.requestKey || this.payload().requestKey;
    this.setData({ busy: true, error: '', requestKey });
    try {
      if (!await this.validateItems()) return;
      const result = this.data.resubmitId
        ? await request({ url: `/tire-requests/approvals/${this.data.resubmitId}/resubmit`, method: 'POST',
          data: { items: this.payload().items, expectedVersion: this.data.expectedVersion } })
        : await request({ url: '/tire-requests', method: 'POST', data: this.payload() });
      this.setData({ result, stage: 'DONE' });
    } catch (error) {
      const message = error.message || '提交失败，请核对列表后重试';
      this.setData({ error: message, allValid: false });
      if (await this.validateItems()) this.setData({ error: message });
    } finally { this.setData({ busy: false }); }
  }
});
