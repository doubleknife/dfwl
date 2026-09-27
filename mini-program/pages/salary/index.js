const { request } = require('../../utils/request');

function currentMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

Page({
  data: {
    businessMonth: currentMonth(), records: [], totalAmount: '0.00', entryCount: 0,
    pageNo: 1, pageSize: 20, hasMore: false, loading: false, loadingMore: false, error: ''
  },
  onShow() { this.load(); },
  onMonth(e) { this.setData({ businessMonth: e.detail.value }); this.load(); },
  async load() {
    if (this.data.loading) return;
    this.setData({ loading: true, error: '', records: [], totalAmount: '0.00', entryCount: 0, hasMore: false });
    try {
      const month = encodeURIComponent(this.data.businessMonth);
      const [summary, page] = await Promise.all([
        request({ url: `/driver-salaries/me/monthly-summary?businessMonth=${month}` }),
        request({ url: `/driver-salaries/me?businessMonth=${month}&pageNo=1&pageSize=20` })
      ]);
      this.setData({ totalAmount: String(summary.totalAmount ?? '0.00'), entryCount: Number(summary.entryCount || 0),
        records: page.records || [], pageNo: 1, hasMore: (page.records || []).length < Number(page.total || 0) });
    } catch (error) {
      this.setData({ error: error.message || '工资单加载失败，请重试', records: [] });
    } finally {
      this.setData({ loading: false });
    }
  },
  async loadMore() {
    if (!this.data.hasMore || this.data.loadingMore || this.data.loading) return;
    this.setData({ loadingMore: true, error: '' });
    try {
      const next = this.data.pageNo + 1;
      const page = await request({ url: `/driver-salaries/me?businessMonth=${encodeURIComponent(this.data.businessMonth)}&pageNo=${next}&pageSize=${this.data.pageSize}` });
      const records = this.data.records.concat(page.records || []);
      this.setData({ records, pageNo: next, hasMore: records.length < Number(page.total || 0) });
    } catch (error) {
      this.setData({ error: error.message || '后续明细加载失败，请重试' });
    } finally {
      this.setData({ loadingMore: false });
    }
  }
});
