const { request } = require('../../utils/request');

Page({
  data: { phone: '', password: '', loading: false },
  onPhone(e) { this.setData({ phone: e.detail.value }); },
  onPassword(e) { this.setData({ password: e.detail.value }); },
  async login() {
    this.setData({ loading: true });
    try {
      const data = await request({
        url: '/auth/login',
        method: 'POST',
        data: { phone: this.data.phone, password: this.data.password }
      });
      getApp().setSession(data.accessToken, data.user);
      wx.redirectTo({ url: '/pages/home/index' });
    } finally {
      this.setData({ loading: false });
    }
  }
});
