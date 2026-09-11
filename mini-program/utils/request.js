function request(options) {
  const app = getApp();
  const token = app.globalData.token || wx.getStorageSync('token');
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${app.globalData.baseUrl}${options.url}`,
      method: options.method || 'GET',
      data: options.data || {},
      header: {
        'content-type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success(res) {
        if (res.statusCode === 401) {
          wx.redirectTo({ url: '/pages/login/index' });
          reject(res);
          return;
        }
        if (res.statusCode >= 200 && res.statusCode < 300 && res.data && res.data.code === '0') {
          resolve(res.data.data);
          return;
        }
        wx.showToast({ title: res.data?.message || '请求失败', icon: 'none' });
        reject(res);
      },
      fail(err) {
        wx.showToast({ title: '网络不可用', icon: 'none' });
        reject(err);
      }
    });
  });
}

function uploadFile(options) {
  const app = getApp();
  const token = app.globalData.token || wx.getStorageSync('token');
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${app.globalData.baseUrl}${options.url}`,
      filePath: options.filePath,
      name: options.name || 'file',
      formData: options.formData || {},
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success(res) {
        let payload = {};
        try {
          payload = JSON.parse(res.data || '{}');
        } catch (error) {
          wx.showToast({ title: '上传响应异常', icon: 'none' });
          reject(error);
          return;
        }
        if (res.statusCode >= 200 && res.statusCode < 300 && payload.code === '0') {
          resolve(payload.data);
          return;
        }
        wx.showToast({ title: payload.message || '上传失败', icon: 'none' });
        reject(payload);
      },
      fail(err) {
        wx.showToast({ title: '上传失败', icon: 'none' });
        reject(err);
      }
    });
  });
}

module.exports = { request, uploadFile };
