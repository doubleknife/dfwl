import fs from 'node:fs';
import path from 'node:path';
import { readChargingWorkbook, previewChargingOrders } from '../src/views/chargingWorkbook.js';

const directory = process.env.CHARGING_SAMPLE_DIR;
const output = process.env.CHARGING_SAMPLE_OUTPUT;
if (!directory || !output) throw new Error('请设置 CHARGING_SAMPLE_DIR 和 CHARGING_SAMPLE_OUTPUT');
// Only a sample verification fixture: production mappings are chosen and saved by an administrator.
const columns = {
  中陶: [1, 11, 17, 30], 云南景诚: [1, 8, 15, 6], 云南源欣: [0, 11, 15, 35],
  云睿骐骥: [1, 8, 15, 6], 保山金粒: [0, 1, 4, 10], 嵩明: [1, 8, 15, 6],
  开远: [0, 69, 26, 34], 文山鑫荣: [1, 12, 3, 10], 晋宁人滴: [0, 71, 11, 44],
  晋宁鑫博鑫: [1, 4, 6, 2], 虹溪: [2, 7, 62, 13], 鑫翔达克云: [0, 6, 19, 28]
};
const samples = [];
for (const name of fs.readdirSync(directory).filter((entry) => /\.xlsx?$/i.test(entry))) {
  const company = Object.keys(columns).find((candidate) => name.startsWith(candidate));
  if (!company) throw new Error(`未配置样本核验列：${name}`);
  const workbook = await readChargingWorkbook({ arrayBuffer: async () => fs.readFileSync(path.join(directory, name)) });
  const [orderNo, plateNo, chargedAt, amount] = columns[company];
  const mapping = { orderNo, plateNo, chargedAt, amount };
  const preview = previewChargingOrders(workbook, mapping);
  samples.push({ name, company, payload: {
    companyId: 0, sheetName: workbook.sheetName, headerRowNo: workbook.headerRowNo,
    headers: workbook.headers, mapping,
    orders: preview.orders.map(({ status, ...order }) => order), count: preview.count,
    zeroCount: preview.zeroCount, totalAmount: preview.totalAmount
  }, errors: preview.errors, ignored: preview.ignored });
  console.log(`${company}: 有效 ${preview.count}，零元 ${preview.zeroCount}，异常 ${preview.errors.length}，汇总/空身份 ${preview.ignored.length}，金额 ${preview.totalAmount}`);
}
if (samples.length !== 12) throw new Error(`预期 12 家文件，实际 ${samples.length}`);
fs.writeFileSync(output, JSON.stringify(samples));
