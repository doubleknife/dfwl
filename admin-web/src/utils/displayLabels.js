// Display text only. API requests, filtering and persistence keep the original codes.
export const businessTypeLabels = {
  ROUTE: '线路', TIRE: '轮胎', EXPENSE: '费用', SALARY: '工资',
  TIRE_REQUEST: '换胎申请', DAILY_EXPENSE: '日常费用', OTHER: '其他'
};

export const expenseTypeLabels = {
  PENALTY: '罚款', CARRYING: '搬运费', REPAIR: '维修费', ELECTRIC: '电费', GAS: '气费',
  TEMP_ELECTRIC: '临时电费', WATER: '水费', TOLL: '过路费', CAR_WASH: '洗车费',
  INFORMATION: '信息费', DRIVER_SALARY: '司机工资', OTHER: '其他费用'
};

export const statusLabels = {
  UNPUBLISHED: '未发布', PUBLISHED: '已发布', TRANSFER_PENDING: '等待接手',
  ASSIGNED: '待发车', TRANSFERRED: '已转派', LEGACY_ENDED: '历史结束',
  IN_TRANSIT: '运输中', COMPLETED: '已完成',
  VOIDED: '已作废', CANCELLED: '已取消',
  IN_STOCK: '库存中', APPROVAL_RESERVED: '审批预留', CLAIMED: '已申领',
  ACTIVE: '有效', PENDING_ATTRIBUTION: '待确认归属', REVERSED: '已冲销', REVERSAL: '冲销记录',
  DRAFT: '草稿', MAINTENANCE: '维护中',
  PENDING: '待处理', IN_PROGRESS: '审批中', APPROVED: '已通过', RETURNED: '已打回',
  RETURNED_TO_APPLICANT: '已打回申请人', INVALIDATED: '已失效', REJECTED: '已拒绝',
  PREVIEWED: '已预览', COMMITTED: '已提交', SUCCESS: '成功', FAILURE: '失败',
  NO_CANDIDATE: '无识别候选'
};

export const attributionLabels = { ROUTE: '线路费用', DAILY: '日常费用' };
export const driverTypeLabels = { INTERNAL: '自有司机', OUTSOURCED: '外协司机' };
export const directionLabels = { OUTBOUND: '去程', RETURN: '返程' };
export const energyTypeLabels = { ELECTRIC: '电', GAS: '气', TEMP_ELECTRIC: '临时电' };
export const sourceTypeLabels = { MANUAL: '人工录入', IMPORT: '导入', APPROVAL: '审批', SYSTEM: '系统生成' };
export const permissionTypeLabels = { MENU: '菜单', BUTTON: '按钮', API: '接口', MINI_PROGRAM: '小程序' };
export const changeTypeLabels = {
  ADD: '新增', MODIFY: '修改', DELETE: '删除', WEIGHT_ADJUST: '重量调整',
  SALARY_ADJUST: '工资调整', REASSIGN: '重新归属', REVERSAL: '冲销',
  EXPENSE_ATTRIBUTION_ADJUST: '费用归属调整', ROUTE_VOID: '线路作废',
  ROUTE_REACTIVATE: '线路恢复', ROUTE_TRANSFER: '线路转派'
};

export function displayValue(key, value, row = {}) {
  if (value == null || value === '') return '-';
  const labels = {
    businessType: businessTypeLabels, approvalType: businessTypeLabels, category: businessTypeLabels,
    factType: businessTypeLabels, expenseType: expenseTypeLabels, energyType: energyTypeLabels,
    attributionType: attributionLabels, beforeAttributionType: attributionLabels,
    afterAttributionType: attributionLabels, driverType: driverTypeLabels, direction: directionLabels,
    sourceType: sourceTypeLabels, permissionType: permissionTypeLabels, changeType: changeTypeLabels,
    status: statusLabels, previewStatus: statusLabels, finalStatus: statusLabels,
    configurationStatus: statusLabels
  }[key];
  if (key === 'status' && value === 'ACTIVE' && (row.templateCode || row.flowName)) return '已发布';
  return labels?.[value] ?? value;
}
