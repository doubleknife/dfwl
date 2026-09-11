App({
  globalData: {
    baseUrl: 'http://localhost:8080/api/v1',
    token: wx.getStorageSync('token') || '',
    user: wx.getStorageSync('user') || null
  },
  setSession(token, user) {
    this.globalData.token = token;
    this.globalData.user = user;
    wx.setStorageSync('token', token);
    wx.setStorageSync('user', user);
  },
  clearSession() {
    this.globalData.token = '';
    this.globalData.user = null;
    wx.removeStorageSync('token');
    wx.removeStorageSync('user');
  }
});
