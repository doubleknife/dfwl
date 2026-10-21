const { request } = require('../../utils/request');
const { formatDateTime } = require('../../utils/dateTime');

const presentRecord = (item) => ({ ...item, createdLabel: formatDateTime(item.createdAt) });

function targetUrl(item) {
  if (item.businessType === 'TASK') return `/pages/route-detail/index?id=${item.businessId}`;
  if (item.businessType === 'SALARY') return '/pages/salary/index';
  if (item.businessType === 'APPROVAL') return `/pages/approvals/index?id=${item.businessId}`;
  return '';
}

Page({
  data: { records: [], pageNo: 1, hasMore: false, unreadCount: 0, loading: false, loadingMore: false, error: '' },
  onShow() { this.load(); },
  async load() {
    if (this.data.loading) return;
    this.setData({ loading: true, error: '' });
    try {
      const [page, count] = await Promise.all([
        request({ url: '/notifications?pageNo=1&pageSize=20' }),
        request({ url: '/notifications/unread-count' })
      ]);
      this.setData({ records: (page.records || []).map(presentRecord), pageNo: 1, hasMore: (page.records || []).length < Number(page.total || 0),
        unreadCount: Number(count.unreadCount || 0) });
    } catch (error) {
      this.setData({ error: error.message || '通知加载失败，请重试' });
    } finally {
      this.setData({ loading: false });
    }
  },
  async loadMore() {
    if (!this.data.hasMore || this.data.loadingMore) return;
    this.setData({ loadingMore: true, error: '' });
    try {
      const next = this.data.pageNo + 1;
      const page = await request({ url: `/notifications?pageNo=${next}&pageSize=20` });
      const records = this.data.records.concat((page.records || []).map(presentRecord));
      this.setData({ records, pageNo: next, hasMore: records.length < Number(page.total || 0) });
    } catch (error) {
      this.setData({ error: error.message || '后续通知加载失败，请重试' });
    } finally {
      this.setData({ loadingMore: false });
    }
  },
  async open(e) {
    const item = this.data.records.find((entry) => entry.id === Number(e.currentTarget.dataset.id));
    if (!item) return;
    try {
      if (!item.readAt) await request({ url: `/notifications/${item.id}/read`, method: 'POST' });
      const url = targetUrl(item);
      if (url) wx.navigateTo({ url });
      else await this.load();
    } catch (error) {
      this.setData({ error: error.message || '打开通知失败，请重试' });
    }
  },
  async readAll() {
    try {
      await request({ url: '/notifications/read-all', method: 'POST' });
      await this.load();
    } catch (error) {
      this.setData({ error: error.message || '标记已读失败' });
    }
  }
});
