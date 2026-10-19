const { request } = require('../../utils/request');

Page({
  data: {
    oldPassword: '', newPassword: '', confirmPassword: '',
    showOldPassword: false, showNewPassword: false, showConfirmPassword: false,
    oldError: '', newError: '', confirmError: '', apiError: '', loading: false
  },
  onLoad() {
    const app = getApp();
    if (!(app.globalData.token || wx.getStorageSync('token'))) {
      wx.redirectTo({ url: '/pages/login/index' });
    }
  },
  onUnload() {
    this.setData({ oldPassword: '', newPassword: '', confirmPassword: '' });
  },
  onOld(e) { this.setData({ oldPassword: e.detail.value, oldError: '', apiError: '' }); },
  onNew(e) { this.setData({ newPassword: e.detail.value, newError: '', apiError: '' }); },
  onConfirm(e) { this.setData({ confirmPassword: e.detail.value, confirmError: '', apiError: '' }); },
  toggleOldVisibility() { this.setData({ showOldPassword: !this.data.showOldPassword }); },
  toggleNewVisibility() { this.setData({ showNewPassword: !this.data.showNewPassword }); },
  toggleConfirmVisibility() { this.setData({ showConfirmPassword: !this.data.showConfirmPassword }); },
  async changePassword() {
    if (this.data.loading) return;
    const { oldPassword, newPassword, confirmPassword } = this.data;
    const oldError = oldPassword ? '' : '请输入原密码';
    const newError = !newPassword ? '请输入新密码' : newPassword.length < 6 ? '新密码至少 6 位' : '';
    const confirmError = !confirmPassword ? '请确认新密码'
      : confirmPassword !== newPassword ? '两次输入的新密码不一致' : '';
    this.setData({ oldError, newError, confirmError, apiError: '' });
    if (oldError || newError || confirmError) return;

    this.setData({ loading: true });
    try {
      await request({ url: '/auth/password', method: 'PUT', data: { oldPassword, newPassword } });
      this.setData({ oldPassword: '', newPassword: '', confirmPassword: '' });
      getApp().clearSession();
      wx.showModal({
        title: '修改成功', content: '密码已修改，请重新登录', showCancel: false,
        complete: () => wx.redirectTo({ url: '/pages/login/index' })
      });
    } catch (error) {
      if (error.statusCode !== 401) {
        this.setData({ apiError: error.code === 'AUTH_005' ? '原密码错误' : (error.message || '修改密码失败，请重试') });
      }
    } finally {
      this.setData({ loading: false });
    }
  }
});
