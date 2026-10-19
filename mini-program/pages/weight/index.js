const { request, uploadFile, downloadAttachment } = require('../../utils/request');
const { netWeight, validateWeights, sanitizeRoute } = require('../../utils/business');
const { hasPermission } = require('../../utils/permissions');
const { routePresentation } = require('../../utils/homePresentation');

Page({
  data: {
    id: null, route: null, grossWeight: '', tareWeight: '', netWeight: '',
    tickets: [], uploading: false, uploadProgress: null, loading: false, confirming: false, busy: false,
    canUnload: false, fieldError: '', mediaError: '', error: '', resultUnknown: false
  },
  onLoad(query) {
    this.setData({ id: query.id });
    this.load();
  },
  async load() {
    if (!this.data.id || this.data.busy) return;
    this.setData({ loading: true, error: '' });
    try {
      const [route, user] = await Promise.all([
        request({ url: `/driver/transport-tasks/${this.data.id}` }), request({ url: '/me' })
      ]);
      const safe = routePresentation(sanitizeRoute(route));
      this.setData({
        route: safe,
        canUnload: hasPermission(user, 'BUTTON_MINI_ROUTES_UNLOAD') && !!safe.currentParticipant,
        grossWeight: this._hasInput ? this.data.grossWeight : (safe.grossWeight || ''),
        tareWeight: this._hasInput ? this.data.tareWeight : (safe.tareWeight || ''),
        resultUnknown: false,
        fieldError: ''
      });
      this.recalculate();
      if (safe.status === 'COMPLETED') {
        const evidence = await request({ url: `/attachments?ownerType=TASK&ownerId=${this.data.id}&purpose=ROUTE_UNLOAD_WEIGHT_TICKET` });
        const tickets = await Promise.all((evidence || []).map(async (item) => ({
          id: item.id, path: await downloadAttachment(item.id), uploadedAt: item.uploadedAt
        })));
        this.setData({ tickets });
      }
    } catch (error) {
      this.setData({ error: error.statusCode === 403
        ? '当前账号不能查看或卸货这条线路，请联系调度'
        : (error.message || '线路加载失败，请检查网络后重试') });
    } finally { this.setData({ loading: false }); }
  },
  onGross(e) {
    this._hasInput = true;
    this.setData({ grossWeight: e.detail.value, fieldError: '' });
    this.recalculate();
  },
  onTare(e) {
    this._hasInput = true;
    this.setData({ tareWeight: e.detail.value, fieldError: '' });
    this.recalculate();
  },
  recalculate() {
    const validation = validateWeights(this.data.grossWeight, this.data.tareWeight);
    if (!validation.ok) {
      this.setData({ netWeight: '' });
      return;
    }
    const net = netWeight(this.data.grossWeight, this.data.tareWeight);
    this.setData({ netWeight: net });
  },
  chooseTickets() {
    if (this.data.busy || this.data.uploading || this.data.route?.status !== 'IN_TRANSIT') return;
    const remaining = 10 - this.data.tickets.length;
    if (remaining <= 0) { this.setData({ mediaError: '最多上传10张磅单' }); return; }
    wx.chooseMedia({
      count: Math.min(remaining, 9), mediaType: ['image'], sourceType: ['camera', 'album'],
      success: async (res) => {
        for (const file of res.tempFiles || []) {
          const path = file.tempFilePath;
          this.setData({ uploading: true, uploadProgress: 0, mediaError: '' });
          try {
            const attachment = await uploadFile({
              url: '/attachments', filePath: path,
              formData: { ownerType: 'TASK', ownerId: this.data.id, purpose: 'ROUTE_UNLOAD_WEIGHT_TICKET' },
              onProgress: (percent) => this.setData({ uploadProgress: percent })
            });
            this.setData({ tickets: this.data.tickets.concat({ id: attachment.id, path }) });
          } catch (error) {
            this.setData({ mediaError: '有图片上传失败。已成功的图片仍保留，请重新选择失败图片。' });
          } finally { this.setData({ uploading: false, uploadProgress: null }); }
        }
      },
      fail: (error) => {
        if (!String(error?.errMsg || '').includes('cancel')) this.setData({ mediaError: '选择图片失败，请重试' });
      }
    });
  },
  removeTicket(e) {
    if (this.data.busy || this.data.uploading || this.data.route?.status !== 'IN_TRANSIT') return;
    const index = Number(e.currentTarget.dataset.index);
    this.setData({ tickets: this.data.tickets.filter((_, i) => i !== index), mediaError: '' });
  },
  previewTicket(e) {
    const paths = this.data.tickets.map((item) => item.path).filter(Boolean);
    const selected = this.data.tickets[Number(e.currentTarget.dataset.index)];
    if (selected?.path) wx.previewImage({ current: selected.path, urls: paths });
  },
  submit() {
    if (this.data.busy || this.data.uploading || this.data.confirming || this.data.loading || this.data.resultUnknown
        || this.data.error || !this.data.canUnload || this.data.route?.status !== 'IN_TRANSIT') return;
    const validation = validateWeights(this.data.grossWeight, this.data.tareWeight);
    if (!validation.ok) {
      this.setData({ fieldError: validation.message });
      return;
    }
    if (!this.data.tickets.length) { this.setData({ mediaError: '请先拍照或选择至少一张磅单' }); return; }
    const { grossWeight, tareWeight, netWeight } = this.data;
    const evidenceAttachmentIds = this.data.tickets.map((item) => item.id);
    this.setData({ confirming: true, fieldError: '' });
    wx.showModal({
      title: '确认重量并卸货',
      content: `毛重 ${Number(grossWeight).toFixed(3)} 吨，皮重 ${Number(tareWeight).toFixed(3)} 吨，实际载重 ${netWeight} 吨，磅单 ${evidenceAttachmentIds.length} 张。提交后司机不能修改重量或磅单。`,
      success: async (res) => {
        if (!res.confirm) { this.setData({ confirming: false }); return; }
        this.setData({ busy: true, error: '' });
        try {
          this._unloadKey = this._unloadKey || `unload-${this.data.id}-${Date.now()}`;
          const completed = await request({
            url: `/transport-tasks/${this.data.id}/unload`, method: 'POST',
            data: { grossWeight, tareWeight, ticketAttachmentIds: evidenceAttachmentIds,
              requestKey: this._unloadKey }
          });
          this._unloadKey = null;
          this.setData({ route: routePresentation(sanitizeRoute(completed)) });
          wx.showToast({ title: '卸货已完成' });
        } catch (error) {
          this.setData({ resultUnknown: true,
            error: '卸货结果未确认。请先重新加载线路状态，确认尚未完成后再提交，勿重复点击。' });
        } finally { this.setData({ busy: false, confirming: false }); }
      },
      fail: () => this.setData({ confirming: false })
    });
  }
});
