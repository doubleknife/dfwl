const { request, uploadFile } = require('../../utils/request');

Page({
  data: {
    driver: null,
    vehicleId: '',
    tires: [],
    requests: [],
    tireId: '',
    confirmedTireNo: '',
    imagePath: '',
    attachmentId: null,
    ocrRecordId: null,
    items: [],
    loading: false,
    uploadStatus: ''
  },
  onShow() { this.load(); },
  async load() {
    const [user, tires] = await Promise.all([
      request({ url: '/me' }),
      request({ url: '/tires?pageNo=1&pageSize=100' })
    ]);
    const driver = user.driverId ? {
      id: user.driverId,
      name: user.driverName,
      driverType: user.driverType,
      currentVehicleId: user.currentVehicleId,
      currentPlateNo: user.currentPlateNo
    } : null;
    this.setData({
      driver,
      vehicleId: driver?.currentVehicleId || '',
      tires: (tires.records || []).filter((item) => item.status === 'IN_STOCK')
    });
  },
  onVehicle(e) { this.setData({ vehicleId: e.detail.value }); },
  onTire(e) {
    const tire = this.data.tires[e.detail.value];
    this.setData({ tireId: tire ? tire.id : '', confirmedTireNo: tire?.tireNo || this.data.confirmedTireNo });
  },
  onTireNo(e) { this.setData({ confirmedTireNo: e.detail.value }); },
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
    if (!this.data.imagePath || !this.data.tireId) {
      wx.showToast({ title: '请选择轮胎和图片', icon: 'none' });
      return;
    }
    this.setData({ uploadStatus: '上传中' });
    const file = await uploadFile({
      url: '/attachments',
      filePath: this.data.imagePath,
      formData: {
        ownerType: 'TIRE',
        ownerId: this.data.tireId,
        purpose: 'TIRE_OCR'
      }
    });
    this.setData({ attachmentId: file.id, uploadStatus: '上传完成' });
  },
  async recognize() {
    if (!this.data.attachmentId) await this.uploadImage();
    const ocr = await request({
      url: '/ocr/tire-number',
      method: 'POST',
      data: { attachmentId: Number(this.data.attachmentId), ocrProvider: 'WECHAT', recognizedText: this.data.confirmedTireNo, rawResultJson: '{}' }
    });
    const confirmed = await request({
      url: `/ocr/${ocr.id}/confirm`,
      method: 'POST',
      data: { confirmedText: this.data.confirmedTireNo }
    });
    this.setData({ ocrRecordId: confirmed.id });
  },
  addItem() {
    if (!this.data.tireId || !this.data.confirmedTireNo) {
      wx.showToast({ title: '请确认胎号', icon: 'none' });
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
