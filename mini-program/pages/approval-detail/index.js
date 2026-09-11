const { request, uploadFile } = require('../../utils/request');

Page({
  data: { id: null, detail: null, attachments: [], comment: '', targetNodeOrder: '', imagePath: '', uploadStatus: '', loading: false },
  onLoad(query) {
    this.setData({ id: query.id });
    this.load();
  },
  async load() {
    const [detail, attachments] = await Promise.all([
      request({ url: `/approvals/${this.data.id}` }),
      request({ url: `/attachments?ownerType=APPROVAL&ownerId=${this.data.id}` }).catch(() => [])
    ]);
    this.setData({ detail, attachments });
  },
  onComment(e) { this.setData({ comment: e.detail.value }); },
  onTarget(e) { this.setData({ targetNodeOrder: e.detail.value }); },
  chooseImage() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => this.setData({ imagePath: res.tempFiles[0].tempFilePath, uploadStatus: '已选择图片' })
    });
  },
  async uploadActionImage() {
    if (!this.data.imagePath) return;
    this.setData({ uploadStatus: '上传中' });
    await uploadFile({
      url: '/attachments',
      filePath: this.data.imagePath,
      formData: { ownerType: 'APPROVAL', ownerId: this.data.id, purpose: 'APPROVAL_ACTION' }
    });
    this.setData({ uploadStatus: '上传完成' });
  },
  async approve() {
    await this.process(`/approvals/${this.data.id}/approve`, { comment: this.data.comment });
  },
  async returnApplicant() {
    await this.process(`/approvals/${this.data.id}/return-applicant`, { comment: this.data.comment });
  },
  async returnNode() {
    await this.process(`/approvals/${this.data.id}/return-node`, {
      comment: this.data.comment,
      targetNodeOrder: this.data.targetNodeOrder ? Number(this.data.targetNodeOrder) : null
    });
  },
  async process(url, data) {
    this.setData({ loading: true });
    try {
      if (this.data.imagePath) await this.uploadActionImage();
      await request({ url, method: 'POST', data });
      wx.showToast({ title: '已处理' });
      await this.load();
    } finally {
      this.setData({ loading: false });
    }
  }
});
