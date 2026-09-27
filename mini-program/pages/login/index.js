const { request } = require('../../utils/request');

Page({
  data: { phone: '', password: '', loading: false, error: '' },
  onPhone(e) { this.setData({ phone: e.detail.value }); },
  onPassword(e) { this.setData({ password: e.detail.value }); },
  async login() {
    if (this.data.loading) return;
    this.setData({ loading: true, error: '' });
    try {
      const data = await request({
        url: '/auth/login',
        method: 'POST',
        data: { phone: this.data.phone, password: this.data.password }
      });
      getApp().setSession(data.accessToken, data.user);
      wx.redirectTo({ url: '/pages/home/index' });
    } catch (error) {
      this.setData({ error: error.message || '登录失败，请重试' });
    } finally {
      this.setData({ loading: false });
    }
  }
});
