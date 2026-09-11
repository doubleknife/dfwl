const { request } = require('../../utils/request');

Page({
  data: { businessMonth: '', records: [], total: 0, loading: false },
  onShow() { this.load(); },
  onMonth(e) { this.setData({ businessMonth: e.detail.value }); this.load(); },
  async load() {
    this.setData({ loading: true });
    try {
      const query = this.data.businessMonth ? `&businessMonth=${this.data.businessMonth}` : '';
      const page = await request({ url: `/driver-salaries/me?pageNo=1&pageSize=100${query}` });
      const records = page.records || [];
      const total = records.reduce((sum, item) => sum + Number(item.salaryAmount || 0), 0);
      this.setData({ records, total: total.toFixed(2) });
    } finally {
      this.setData({ loading: false });
    }
  }
});
