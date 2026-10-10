import * as XLSX from 'xlsx/xlsx.mjs';

const FIELD_HINTS = [/订单|交易号|流水号/, /车牌/, /时间|日期/, /金额|支付|费用/];
const REQUIRED = ['orderNo', 'plateNo', 'chargedAt', 'amount'];

export function detectHeader(rows) {
  let best = { rowNo: 0, score: -1 };
  for (let i = 0; i < Math.min(rows.length, 20); i++) {
    const labels = (rows[i] || []).map((value) => String(value ?? '').trim()).filter(Boolean);
    const score = FIELD_HINTS.filter((hint) => labels.some((label) => hint.test(label))).length;
    if (score > best.score) best = { rowNo: i + 1, score };
  }
  return best.rowNo || 1;
}

export async function readChargingWorkbook(file) {
  const bytes = await file.arrayBuffer();
  // Keep Excel serial values: SheetJS Date conversion may shift seconds for some vendor workbooks.
  const workbook = XLSX.read(bytes, { type: 'array', cellDates: false, dense: true });
  const sheetName = workbook.SheetNames[0];
  if (!sheetName) throw new Error('Excel 没有可读取的工作表');
  const rows = XLSX.utils.sheet_to_json(workbook.Sheets[sheetName], {
    header: 1, raw: true, defval: '', blankrows: true
  });
  if (!rows.length) throw new Error('第一个工作表没有内容');
  const headerRowNo = detectHeader(rows);
  return { sheetName, rows, headerRowNo, headers: headersAt(rows, headerRowNo) };
}

export function headersAt(rows, headerRowNo) {
  const row = rows[headerRowNo - 1] || [];
  return row.map((value, index) => ({ index, label: String(value ?? '').trim() }))
    .filter((entry) => entry.label);
}

export function mappingComplete(mapping, headers) {
  return REQUIRED.every((key) => Number.isInteger(mapping?.[key])
    && headers.some((entry) => entry.index === mapping[key]));
}

function dateText(value) {
  if (value instanceof Date && !Number.isNaN(value.getTime())) {
    const pad = (n) => String(n).padStart(2, '0');
    return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`
      + `T${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`;
  }
  if (typeof value === 'number') {
    const parsed = XLSX.SSF.parse_date_code(value);
    if (!parsed) return '';
    const pad = (n) => String(n).padStart(2, '0');
    return `${parsed.y}-${pad(parsed.m)}-${pad(parsed.d)}T${pad(parsed.H)}:${pad(parsed.M)}:${pad(parsed.S)}`;
  }
  const text = String(value ?? '').trim().replaceAll('/', '-').replace(' ', 'T');
  return /^\d{4}-\d{1,2}-\d{1,2}T\d{1,2}:\d{2}(:\d{2})?$/.test(text)
    ? `${text.length === 16 ? `${text}:00` : text}` : '';
}

function amountText(value) {
  const raw = String(value ?? '').replace(/[￥¥,\s]/g, '');
  if (!/^\d+(\.\d+)?$/.test(raw)) return '';
  const [whole, fraction = ''] = raw.split('.');
  if (fraction.length > 2 && /[1-9]/.test(fraction.slice(2))) return '';
  const cents = BigInt(whole) * 100n + BigInt(fraction.slice(0, 2).padEnd(2, '0'));
  if (cents > 999999999999999999n) return '';
  return `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}`;
}

function summaryRow(row) {
  return row.some((value) => /^(合计|总计|汇总|总订单|订单总数|总金额)/.test(String(value ?? '').trim()));
}

export function previewChargingOrders(workbook, mapping) {
  if (!mappingComplete(mapping, workbook.headers)) throw new Error('请从表头选择全部四项字段');
  const orders = [], errors = [], ignored = [];
  let totalCents = 0n, zeroCount = 0;
  let lastIdentityIndex = workbook.headerRowNo - 1;
  for (let i = workbook.headerRowNo; i < workbook.rows.length; i++) {
    if (['orderNo', 'plateNo', 'chargedAt'].some((key) =>
      String(workbook.rows[i]?.[mapping[key]] ?? '').trim())) lastIdentityIndex = i;
  }
  for (let i = workbook.headerRowNo; i < workbook.rows.length; i++) {
    const row = workbook.rows[i] || [];
    if (row.every((value) => value == null || String(value).trim() === '')) continue;
    const identityEmpty = ['orderNo', 'plateNo', 'chargedAt']
      .every((key) => !String(row[mapping[key]] ?? '').trim());
    const amountPresent = String(row[mapping.amount] ?? '').trim() !== '';
    const trailingTotal = identityEmpty && amountPresent && i > lastIdentityIndex
      && amountText(row[mapping.amount]) === `${totalCents / 100n}.${String(totalCents % 100n).padStart(2, '0')}`;
    if (summaryRow(row) || (identityEmpty && !amountPresent) || trailingTotal) {
      ignored.push(i + 1); continue;
    }
    const rawOrder = row[mapping.orderNo];
    const orderNo = typeof rawOrder === 'number' && !Number.isSafeInteger(rawOrder)
      ? '' : String(rawOrder ?? '').trim();
    const plateNo = String(row[mapping.plateNo] ?? '').trim();
    const chargedAt = dateText(row[mapping.chargedAt]);
    const amount = amountText(row[mapping.amount]);
    const problems = [];
    if (!orderNo) problems.push('订单号缺失或数字超过 Excel 精度，请在源文件中改为文本');
    if (!plateNo) problems.push('车牌号缺失');
    if (!chargedAt) problems.push('充电时间缺失或格式错误');
    if (!amount) problems.push('金额不能为空、负数或无法解析，且最多两位小数');
    if (problems.length) { errors.push({ rowNo: i + 1, message: problems.join('；') }); continue; }
    totalCents += BigInt(amount.replace('.', ''));
    if (amount === '0.00') zeroCount++;
    orders.push({ rowNo: i + 1, sheetName: workbook.sheetName, orderNo, plateNo, chargedAt, amount,
      status: amount === '0.00' ? '零元不入账' : '待确认入账' });
  }
  const totalAmount = `${totalCents / 100n}.${String(totalCents % 100n).padStart(2, '0')}`;
  return { orders, errors, ignored, count: orders.length, zeroCount, totalAmount };
}
