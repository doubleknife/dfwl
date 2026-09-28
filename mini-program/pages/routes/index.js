const { request } = require('../../utils/request');
const { sanitizeRoute } = require('../../utils/business');
const { routePresentation } = require('../../utils/homePresentation');

const PAGE_SIZE = 20;
const TABS = [
  { key: 'PUBLISHED', label: '待发车' },
  { key: 'TRANSFER_PENDING', label: '等待接手' },
  { key: 'IN_TRANSIT', label: '运输中' },
  { key: 'COMPLETED', label: '已完成' },
  { key: '', label: '全部' }
];

Page({
  data: {
    tabs: TABS,
    activeStatus: 'PUBLISHED',
    records: [],
    total: 0,
    pageNo: 0,
    hasMore: false,
    loading: false,
    loadingMore: false,
    error: '',
    moreError: '',
    initialized: false
  },
  onShow() { this.refresh(); },
  onPullDownRefresh() { this.refresh(); },
  onReachBottom() { this.loadMore(); },
  onTab(e) {
    const status = e.currentTarget.dataset.status || '';
    if (status === this.data.activeStatus) return;
    this.setData({ activeStatus: status });
    this.refresh();
  },
  refresh() {
    this._queryVersion = (this._queryVersion || 0) + 1;
    this.setData({ loading: true, loadingMore: false, error: '', moreError: '', initialized: false });
    return this.loadPage(1, this._queryVersion, true);
  },
  loadMore() {
    if (this.data.loading || this.data.loadingMore || !this.data.hasMore) return;
    this.setData({ loadingMore: true, moreError: '' });
    return this.loadPage(this.data.pageNo + 1, this._queryVersion, false);
  },
  async loadPage(pageNo, version, replace) {
    const status = this.data.activeStatus;
    const filter = status ? `&status=${encodeURIComponent(status)}` : '';
    try {
      const page = await request({ url: `/routes?pageNo=${pageNo}&pageSize=${PAGE_SIZE}${filter}` });
      if (version !== this._queryVersion || status !== this.data.activeStatus) return;
      const incoming = (page.records || []).map((item) => routePresentation(sanitizeRoute(item)));
      const records = replace ? incoming : this.data.records.concat(incoming);
      const total = Number(page.total) || 0;
      this.setData({ records, total, pageNo, hasMore: records.length < total,
        loading: false, loadingMore: false, initialized: true, error: '', moreError: '' });
    } catch (error) {
      if (version !== this._queryVersion || status !== this.data.activeStatus) return;
      const message = error.statusCode === 403
        ? '当前账号没有查看线路的权限，请联系管理员'
        : (error.message || '线路加载失败，请检查网络后重试');
      this.setData(replace
        ? { records: [], total: 0, pageNo: 0, hasMore: false, loading: false, initialized: true, error: message }
        : { loadingMore: false, moreError: message });
    } finally {
      if (typeof wx.stopPullDownRefresh === 'function') wx.stopPullDownRefresh();
    }
  },
  open(e) {
    wx.navigateTo({ url: `/pages/route-detail/index?id=${e.currentTarget.dataset.id}` });
  }
});
