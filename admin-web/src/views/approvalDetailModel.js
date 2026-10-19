import { parse } from 'lossless-json';

const actionNames = {
  APPROVE: '通过', RETURN_APPLICANT: '打回申请人', RETURN_NODE: '打回指定节点'
};
const statusNames = {
  PENDING: '待处理', APPROVED: '已通过', RETURNED: '已打回',
  RETURNED_TO_APPLICANT: '已打回申请人', INVALIDATED: '已失效',
  REJECTED: '已拒绝'
};

export const actionName = (value) => actionNames[value] || value || '-';
export const statusName = (value) => statusNames[value] || value || '-';
export const displayTime = (value) => value ? String(value).replace('T', ' ') : '-';

export function detailError(error, resource = '审批') {
  const prefix = error?.status === 401 ? '登录已失效，请重新登录'
    : error?.status === 403 ? `无权查看${resource}`
      : error?.status === 404 ? `${resource}不存在` : (error?.message || '请求失败');
  const message = error?.message && prefix !== error.message ? `：${error.message}` : '';
  return `${prefix}${message}`;
}

export function parseSnapshot(text) {
  try {
    const value = parse(String(text || '{}'), undefined, { parseNumber: (number) => number });
    if (value && typeof value === 'object' && !Array.isArray(value)) return { value, error: '' };
    return { value: {}, error: '申请快照格式不是对象' };
  } catch {
    return { value: {}, error: '申请快照无法解析' };
  }
}

export function prepareApprovalDetail(detail) {
  const submissions = [...(detail?.submissions || [])]
    .sort((left, right) => left.versionNo - right.versionNo)
    .map((submission) => ({
      ...submission,
      ...parseSnapshot(submission.businessSnapshotJson),
      attachments: submission.attachments || []
    }));
  return {
    ...detail,
    submissions,
    currentSubmission: submissions.at(-1) || null,
    tasks: (detail?.tasks || []).map((task) => ({
      ...task,
      actions: (task.actions || []).map((action) => ({
        ...action, attachments: action.attachments || []
      }))
    }))
  };
}

export function approvalKind(approval) {
  if (approval?.approvalType === 'EXPENSE') return 'EXPENSE';
  if (approval?.approvalType === 'TIRE_REQUEST' || approval?.businessType === 'TIRE_REQUEST') return 'TIRE';
  return 'OTHER';
}

export function snapshotSummary(approval, submission) {
  if (!submission) return '暂无申请版本';
  const snapshot = submission.value;
  if (approvalKind(approval) === 'EXPENSE') {
    const attribution = snapshot.attributionType === 'ROUTE' ? `线路 #${snapshot.routeId || '-'} ` : '日常费用 ';
    return `${attribution}· ${Array.isArray(snapshot.items) ? snapshot.items.length : 0} 条明细`;
  }
  if (approvalKind(approval) === 'TIRE') {
    return `换胎申请 #${snapshot.requestId || approval?.businessId || '-'} · ${Array.isArray(snapshot.items) ? snapshot.items.length : 0} 条轮胎项`;
  }
  return `版本 V${submission.versionNo}`;
}

const detailNames = {
  energyDetail: '能源明细', penaltyDetail: '罚款明细', repairDetail: '维修明细', tollDetail: '过路费明细'
};
export function expenseExtensions(item) {
  return Object.entries(detailNames)
    .filter(([key]) => item?.[key] && typeof item[key] === 'object')
    .map(([key, name]) => ({ name, text: JSON.stringify(item[key]) }));
}

export function missingTireOcrEvidence(submission) {
  const attached = new Set((submission?.attachments || []).map((file) => String(file.id)));
  return (submission?.value?.items || []).some((item) =>
    item.ocrAttachmentId && !attached.has(String(item.ocrAttachmentId)));
}
