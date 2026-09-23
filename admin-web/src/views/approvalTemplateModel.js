import {
  attributionTypes, evidenceRequirements, expenseTypes, inputModes, templateCategories
} from '../generated/approvalOptions.js';

export { attributionTypes, evidenceRequirements, expenseTypes, inputModes, templateCategories };

export const categoryLabels = { TIRE: '换胎', EXPENSE: '费用', OTHER: '其他' };
export const evidenceLabels = { NONE: '无需依据', OPTIONAL: '依据选填', REQUIRED: '依据必填' };
export const statusLabels = { DRAFT: '草稿', ACTIVE: '已发布', MAINTENANCE: '维护中' };
export const modeLabels = { SINGLE: '单条录入', EXCEL: 'Excel 录入' };
export const attributionLabels = { ROUTE: '绑定线路', DAILY: '日常费用' };
export const expenseLabels = {
  PENALTY: '罚款', CARRYING: '搬运', REPAIR: '维修', ELECTRIC: '电费', GAS: '气费',
  TEMP_ELECTRIC: '临时电费', WATER: '水费', TOLL: '过路费', CAR_WASH: '洗车',
  INFORMATION: '信息费', DRIVER_SALARY: '司机工资', OTHER: '其他'
};

export function emptyForm(category = 'EXPENSE') {
  return {
    templateCode: category === 'TIRE' ? 'TIRE_REQUEST' : '',
    category,
    flowName: '',
    evidenceRequirement: 'OPTIONAL',
    initiatorRoleIds: [],
    nodes: [{ nodeOrder: 1, nodeName: '', positionId: '', approverUserId: '', allowReturn: true }],
    expenseType: '',
    inputModes: ['SINGLE'],
    attributionTypes: ['ROUTE']
  };
}

export function formFromConfiguration(configuration, category) {
  return {
    templateCode: '',
    category,
    flowName: configuration.flow.flowName,
    evidenceRequirement: configuration.evidenceRequirement,
    initiatorRoleIds: (configuration.initiatorRoleIds || []).map(Number),
    nodes: (configuration.nodes || []).map((node) => ({
      nodeOrder: node.nodeOrder,
      nodeName: node.nodeName,
      positionId: node.positionId ?? '',
      approverUserId: node.approverUserId,
      allowReturn: node.allowReturn
    })),
    expenseType: configuration.expenseConfig?.expenseType || '',
    inputModes: [...(configuration.expenseConfig?.inputModes || [])],
    attributionTypes: [...(configuration.expenseConfig?.attributionTypes || [])]
  };
}

function checkedOptions(values, allowed, label) {
  if (!Array.isArray(values) || !values.length || new Set(values).size !== values.length
      || values.some((value) => !allowed.includes(value))) {
    throw new Error(`请选择有效的${label}`);
  }
  return values;
}

export function formPayload(form, { creating = false } = {}) {
  if (!templateCategories.includes(form.category)) throw new Error('请选择有效的模板类别');
  if (creating && !/^[A-Z][A-Z0-9_]{0,63}$/.test(form.templateCode)) {
    throw new Error('模板代码须以大写字母开头，仅含大写字母、数字和下划线');
  }
  if (creating && form.category === 'TIRE' && form.templateCode !== 'TIRE_REQUEST') {
    throw new Error('换胎模板代码必须为 TIRE_REQUEST');
  }
  if (!form.flowName?.trim()) throw new Error('请填写流程名称');
  if (!evidenceRequirements.includes(form.evidenceRequirement)) throw new Error('请选择有效的申请依据要求');
  const initiatorRoleIds = (form.initiatorRoleIds || []).map(Number);
  if (new Set(initiatorRoleIds).size !== initiatorRoleIds.length
      || initiatorRoleIds.some((id) => !Number.isSafeInteger(id) || id <= 0)
      || (form.category !== 'TIRE' && !initiatorRoleIds.length)) {
    throw new Error('请选择有效的允许发起角色');
  }
  if (!Array.isArray(form.nodes) || !form.nodes.length) throw new Error('至少配置一个审批节点');
  const nodes = form.nodes.map((node, index) => {
    const approverUserId = Number(node.approverUserId);
    if (!node.nodeName?.trim() || !Number.isSafeInteger(approverUserId) || approverUserId <= 0) {
      throw new Error(`请完整配置第 ${index + 1} 个审批节点`);
    }
    const positionId = node.positionId === '' || node.positionId == null ? null : Number(node.positionId);
    if (positionId !== null && (!Number.isSafeInteger(positionId) || positionId <= 0)) {
      throw new Error(`第 ${index + 1} 个节点岗位 ID 不合法`);
    }
    return { nodeOrder: index + 1, nodeName: node.nodeName.trim(), positionId,
      approverUserId, allowReturn: Boolean(node.allowReturn) };
  });
  const body = { flowName: form.flowName.trim(), evidenceRequirement: form.evidenceRequirement,
    initiatorRoleIds, nodes };
  if (creating) Object.assign(body, { templateCode: form.templateCode, category: form.category });
  if (form.category === 'EXPENSE') {
    if (!expenseTypes.includes(form.expenseType)) throw new Error('请选择正式费用类型');
    body.expenseConfig = {
      expenseType: form.expenseType,
      inputModes: checkedOptions(form.inputModes, inputModes, '录入方式'),
      attributionTypes: checkedOptions(form.attributionTypes, attributionTypes, '费用归属')
    };
  }
  return body;
}

export function templateSummary(template, versions) {
  const active = versions.find((version) => version.id === template.activeFlowId);
  const latest = versions.reduce((current, version) => !current || version.versionNo > current.versionNo
    ? version : current, null);
  return {
    ...template,
    displayName: active?.flowName || latest?.flowName || template.templateCode,
    activeVersion: active ? `V${active.versionNo}` : '—',
    currentStatus: active?.status || latest?.status || 'DRAFT',
    latestFlowId: latest?.id ?? null
  };
}

export function canMaintain(version, versions) {
  return version?.status === 'ACTIVE' && versions.every((item) => item.versionNo <= version.versionNo);
}

export function canConfigureNext(version, versions) {
  return version?.status === 'MAINTENANCE' && versions.every((item) => item.versionNo <= version.versionNo);
}

export function canPublish(version) {
  return version?.status === 'DRAFT' || version?.status === 'MAINTENANCE';
}
