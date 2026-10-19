export function calendarDays(month) {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month || '')) return [];
  const [year, number] = month.split('-').map(Number);
  return Array.from({ length: new Date(year, number, 0).getDate() }, (_, index) => index + 1);
}

export function dayFact(row, day) {
  const indexed = row.days?.[day - 1];
  return indexed?.day === day ? indexed : row.days?.find((item) => item.day === day);
}

export function isWeekend(month, day) {
  const [year, number] = month.split('-').map(Number);
  const weekday = new Date(year, number - 1, day).getDay();
  return weekday === 0 || weekday === 6;
}

export function isCurrentDay(month, day, today = new Date()) {
  return month === `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`
    && day === today.getDate();
}

export function rowTotalMatches(row) {
  return (row.days || []).reduce((sum, item) => sum + Number(item.count || 0), 0)
    === Number(row.monthlyTotal || 0);
}
