const { uploadFile } = require('./request');

async function chooseApprovalImages(existing, userId, purpose, onUploaded) {
  const selected = await new Promise((resolve, reject) => wx.chooseMedia({
    count: Math.min(9, 20 - existing.length), mediaType: ['image'],
    sourceType: ['camera', 'album'], success: resolve,
    fail: (error) => /cancel/i.test(error?.errMsg || '') ? resolve({ tempFiles: [] }) : reject(error)
  }));
  const images = existing.slice();
  for (const file of selected.tempFiles || []) {
    if (!file.tempFilePath || file.size > 30 * 1024 * 1024)
      throw new Error('单张图片不能超过 30 MB');
    const uploaded = await uploadFile({ url: '/attachments', filePath: file.tempFilePath,
      formData: { ownerType: 'APPROVAL_UPLOAD', ownerId: String(userId), purpose } });
    images.push({ id: uploaded.id, path: file.tempFilePath });
    onUploaded(images.slice());
  }
  return images;
}

function singleExpenseItem(item) {
  if (!item || Array.isArray(item) || typeof item !== 'object')
    throw new Error('小程序每次只能提交一笔费用明细');
  return [item];
}

module.exports = { chooseApprovalImages, singleExpenseItem };
