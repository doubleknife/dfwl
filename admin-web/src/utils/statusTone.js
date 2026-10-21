const success = new Set([
  '启用', '有效', '可用', '保险完整', '已通过', '校验通过', '入库成功', '全部入库',
  '全部发布', '预览通过', '发布成功', '已完成', '已发布', '成功', 'ACTIVE', 'AVAILABLE', 'APPROVED',
  'COMPLETED', 'IMPORTED', 'VALID', 'SUCCESS', 'COMMITTED'
]);
const danger = new Set([
  '停用', '保险不完整', '已取消', '已拒绝', '已打回', '已打回申请人', '已撤回',
  '已失效', '已冲销', '冲销记录', '失败', '校验失败', '入库失败', '部分行失败',
  '部分发布', '预览不通过', '发布失败', '状态异常', 'CANCELLED', 'REJECTED', 'RETURNED', 'RETURNED_TO_APPLICANT',
  'WITHDRAWN', 'INVALIDATED', 'REVERSED', 'REVERSAL', 'FAILURE', 'FAILED', 'INVALID',
  'COMPLETED_WITH_ERRORS'
]);
const warning = new Set([
  '待处理', '待确认', '待发车', '待接手发车', '等待接手', '审批中', '审批预留',
  '运输中', '正在处理', '正在发布', '处理中', '维护中', '待确认归属', '未发布', '草稿',
  'PENDING', 'PREVIEWED', 'IN_PROGRESS', 'APPROVAL_RESERVED', 'TRANSFER_PENDING',
  'ASSIGNED', 'IN_TRANSIT', 'CONFIRMING', 'MAINTENANCE', 'PENDING_ATTRIBUTION',
  'DRAFT', 'PUBLISHED'
]);

export function statusTone(value, tone = '') {
  if (value === true || value === 1 || value === '1') return 'success';
  if (value === false || value === 0 || value === '0') return 'danger';
  if (success.has(value)) return 'success';
  if (danger.has(value)) return 'danger';
  if (warning.has(value)) return 'warning';
  return tone || 'info';
}
