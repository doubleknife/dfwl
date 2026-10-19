function apiError(payload, fallback, statusCode) {
  const message = payload?.message || fallback;
  const requestId = payload?.requestId;
  const error = new Error(message);
  error.statusCode = statusCode;
  error.code = payload?.code;
  error.requestId = requestId;
  return error;
}

function isCurrentSession(app, token) {
  return token === (app.globalData.token || wx.getStorageSync('token'));
}

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
          const isLogin = options.url === '/auth/login';
          if (!isLogin && options.url !== '/auth/logout' && isCurrentSession(app, token)) {
            app.clearSession();
            wx.redirectTo({ url: '/pages/login/index' });
          }
          reject(apiError(res.data, isLogin ? '手机号或密码不正确' : '登录已失效，请重新登录', 401));
          return;
        }
        if (res.statusCode >= 200 && res.statusCode < 300 && res.data && res.data.code === '0') {
          resolve(res.data.data);
          return;
        }
        const error = apiError(res.data, res.statusCode === 403 ? '无权访问' : '请求失败', res.statusCode);
        wx.showToast({ title: error.message, icon: 'none' });
        reject(error);
      },
      fail(err) {
        wx.showToast({ title: '网络不可用', icon: 'none' });
        reject(apiError(null, '网络不可用'));
      }
    });
  });
}

function uploadFile(options) {
  const app = getApp();
  const token = app.globalData.token || wx.getStorageSync('token');
  return new Promise((resolve, reject) => {
    const task = wx.uploadFile({
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
        if (res.statusCode === 401) {
          if (isCurrentSession(app, token)) {
            app.clearSession();
            wx.redirectTo({ url: '/pages/login/index' });
          }
        }
        const error = apiError(payload, '上传失败', res.statusCode);
        wx.showToast({ title: error.message, icon: 'none' });
        reject(error);
      },
      fail(err) {
        wx.showToast({ title: '上传失败', icon: 'none' });
        reject(apiError(null, '上传失败'));
      }
    });
    if (options.onProgress && task && typeof task.onProgressUpdate === 'function') {
      task.onProgressUpdate((progress) => options.onProgress(progress.progress));
    }
  });
}

function downloadAttachment(id) {
  const app = getApp();
  const token = app.globalData.token || wx.getStorageSync('token');
  return new Promise((resolve, reject) => {
    wx.downloadFile({
      url: `${app.globalData.baseUrl}/attachments/${id}`,
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success(res) {
        if (res.statusCode === 200) { resolve(res.tempFilePath); return; }
        const error = apiError(null, res.statusCode === 403 ? '无权查看图片' : '图片下载失败', res.statusCode);
        wx.showToast({ title: error.message, icon: 'none' });
        reject(error);
      },
      fail() {
        const error = apiError(null, '图片下载失败');
        wx.showToast({ title: error.message, icon: 'none' });
        reject(error);
      }
    });
  });
}

module.exports = { request, uploadFile, downloadAttachment };
