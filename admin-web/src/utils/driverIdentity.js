const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
const checkDigits = '10X98765432';

export function idCardValidationMessage(raw) {
  const id = String(raw || '').trim().toUpperCase();
  if (!id) return '请填写身份证号';
  if (!/^[1-9]\d{16}[\dX]$/.test(id)) return '身份证号应为18位数字，末位可为X';
  if (!/^(11|12|13|14|15|21|22|23|31|32|33|34|35|36|37|41|42|43|44|45|46|50|51|52|53|54|61|62|63|64|65)$/.test(id.slice(0, 2))) return '身份证号地区码无效，请核对前两位';
  const year = Number(id.slice(6, 10));
  const month = Number(id.slice(10, 12));
  const day = Number(id.slice(12, 14));
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() + 1 !== month || date.getDate() !== day || date > new Date()) return '身份证号中的出生日期无效';
  const sum = weights.reduce((total, weight, index) => total + Number(id[index]) * weight, 0);
  if (id[17] !== checkDigits[sum % 11]) return '身份证号末位校验码不正确，请核对后重新输入';
  return '';
}

export function birthDateFromIdCard(raw) {
  if (idCardValidationMessage(raw)) return null;
  const id = String(raw).trim().toUpperCase();
  return `${id.slice(6, 10)}-${id.slice(10, 12)}-${id.slice(12, 14)}`;
}

export function fullAge(birthDate, now = new Date()) {
  if (!birthDate) return null;
  const [year, month, day] = birthDate.split('-').map(Number);
  let age = now.getFullYear() - year;
  if (now.getMonth() + 1 < month || (now.getMonth() + 1 === month && now.getDate() < day)) age--;
  return age;
}
