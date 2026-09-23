export const visibleTemplates = (templates) => (templates || []).filter(
  (template) => template.category === 'EXPENSE' || template.category === 'OTHER'
);

export const allowedAttributions = (template) => (template?.allowedAttributionTypes || [])
  .filter((type) => type === 'ROUTE' || type === 'DAILY');

export const isEnergy = (type) => ['ELECTRIC', 'GAS', 'TEMP_ELECTRIC'].includes(type);

export async function loadAllPages(fetchPage) {
  const results = [];
  for (let pageNo = 1; pageNo <= 100; pageNo += 1) {
    const page = await fetchPage(pageNo);
    if (Array.isArray(page)) return [...results, ...page];
    if (!Array.isArray(page?.records)) throw new Error('主数据分页响应无效');
    results.push(...page.records);
    const total = Number(page.total);
    if (Number.isFinite(total) && total >= 0 && results.length >= total) return results;
    if (!page.records.length) throw new Error('主数据分页结果不完整');
    if (!Number.isFinite(total) && page.records.length < Number(page.pageSize || 100)) return results;
  }
  throw new Error('主数据超过 100 页，请缩小查询范围');
}

export function newItem(itemNo, values = {}) {
  return {
    itemNo, vehicleId: '', driverId: '', amount: '', businessDate: '', remark: '',
    energyType: '', stationId: '', orderNo: '', startTime: '', quantity: '',
    ...values
  };
}

export function normalizeAmount(value) {
  if (typeof value === 'number') {
    if (!Number.isFinite(value) || String(value).replace(/\D/g, '').length > 15) {
      throw new Error('金额数字超出安全精度，请将 Excel 单元格设为文本后重新导入');
    }
    value = String(value);
  }
  const text = String(value ?? '').trim();
  if (!/^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/.test(text) || !/[1-9]/.test(text)) {
    throw new Error('金额必须是正数，最多两位小数');
  }
  const [whole, fraction = ''] = text.split('.');
  if (whole.length > 16 || `${whole}${fraction}`.replace(/^0+/, '').length > 18) {
    throw new Error('金额超出数据库 DECIMAL(18,2) 范围');
  }
  return text;
}

export function normalizeDate(value) {
  const text = value instanceof Date
    ? `${value.getUTCFullYear()}-${String(value.getUTCMonth() + 1).padStart(2, '0')}-${String(value.getUTCDate()).padStart(2, '0')}`
    : String(value ?? '').trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) throw new Error('日期必须为 YYYY-MM-DD');
  const date = new Date(`${text}T00:00:00Z`);
  if (Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== text) {
    throw new Error('日期不是有效日历日期');
  }
  return text;
}

export function normalizeQuantity(value) {
  const text = String(value ?? '').trim();
  if (!/^(?:0|[1-9]\d*)(?:\.\d{1,3})?$/.test(text) || !/[1-9]/.test(text)) {
    throw new Error('能源数量必须为正数，最多三位小数');
  }
  if (text.replace(/\D/g, '').replace(/^0+/, '').length > 18) throw new Error('能源数量最多 18 位有效数字');
  return text;
}

function uniqueId(text, records, field, label) {
  const value = String(text ?? '').trim();
  if (!value) throw new Error(`${label}不能为空`);
  const matches = records.filter((record) => String(record[field] ?? '').trim() === value);
  if (matches.length !== 1) throw new Error(`${label}“${value}”${matches.length ? '有多个匹配' : '未匹配到主数据'}`);
  return matches[0].id;
}

export function mapExcelRows(rows, vehicles, drivers, nextItemNo) {
  if (!Array.isArray(rows) || rows.length < 2) throw new Error('Excel 必须包含表头和至少一条明细');
  const header = rows[0].map((value) => String(value ?? '').trim());
  const required = ['车辆', '司机', '金额', '日期'];
  for (const name of required) {
    if (header.filter((value) => value === name).length !== 1) throw new Error(`Excel 表头必须且只能包含一个“${name}”列`);
  }
  const results = [];
  const errors = [];
  rows.slice(1).forEach((row, index) => {
    if (!row.some((value) => value !== null && value !== undefined && String(value).trim())) return;
    try {
      const value = (name) => row[header.indexOf(name)];
      results.push(newItem(nextItemNo(), {
        vehicleId: uniqueId(value('车辆'), vehicles, 'plateNo', '车辆'),
        driverId: value('司机') == null || String(value('司机')).trim() === ''
          ? '' : uniqueId(value('司机'), drivers, 'name', '司机'),
        amount: normalizeAmount(value('金额')),
        businessDate: normalizeDate(value('日期'))
      }));
    } catch (error) {
      errors.push(`第 ${index + 2} 行：${error.message}`);
    }
  });
  if (errors.length) throw new Error(errors.join('；'));
  if (!results.length) throw new Error('Excel 没有可导入的明细');
  return results;
}

export function expensePayload(template, attributionType, routeId, items, attachmentIds) {
  if (!template || template.category !== 'EXPENSE') throw new Error('请先选择费用审批模板');
  if (!allowedAttributions(template).includes(attributionType)) throw new Error('请选择模板允许的费用归属');
  if (attributionType === 'ROUTE' && !routeId) throw new Error('线路费用必须选择线路');
  if (template.evidenceRequirement === 'REQUIRED' && !attachmentIds.length) throw new Error('该模板必须上传图片依据');
  if (!items.length) throw new Error('请至少录入一条费用明细');
  const seen = new Set();
  const normalized = items.map((item) => {
    if (!/^[A-Za-z0-9_-]{1,64}$/.test(item.itemNo) || seen.has(item.itemNo)) throw new Error('费用明细编号无效或重复');
    seen.add(item.itemNo);
    if (!item.vehicleId) throw new Error(`${item.itemNo}：请选择车辆`);
    const entry = {
      itemNo: item.itemNo, vehicleId: Number(item.vehicleId),
      amount: normalizeAmount(item.amount), businessDate: normalizeDate(item.businessDate)
    };
    if (item.driverId) entry.driverId = Number(item.driverId);
    if (item.remark?.trim()) entry.remark = item.remark.trim();
    if (isEnergy(template.expenseType)) {
      if (!['ELECTRIC', 'GAS', 'TEMP_ELECTRIC'].includes(item.energyType)
          || !item.orderNo?.trim() || !item.startTime || !item.quantity) {
        throw new Error(`${item.itemNo}：请填写完整能源明细`);
      }
      if (item.orderNo.trim().length > 128) throw new Error(`${item.itemNo}：能源订单号过长`);
      if (!/^\d{4}-\d{2}-\d{2}T(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?$/.test(item.startTime)) {
        throw new Error(`${item.itemNo}：能源开始时间不合法`);
      }
      normalizeDate(item.startTime.slice(0, 10));
      entry.energyDetail = {
        energyType: item.energyType.trim(), orderNo: item.orderNo.trim(),
        startTime: item.startTime, quantity: normalizeQuantity(item.quantity)
      };
      if (item.stationId) {
        if (!/^[1-9]\d*$/.test(String(item.stationId))) throw new Error(`${item.itemNo}：站点 ID 不合法`);
        entry.energyDetail.stationId = Number(item.stationId);
      }
    }
    return entry;
  });
  return {
    templateId: template.templateId,
    attributionType,
    routeId: attributionType === 'ROUTE' ? Number(routeId) : null,
    attachmentIds: [...attachmentIds],
    items: normalized
  };
}
