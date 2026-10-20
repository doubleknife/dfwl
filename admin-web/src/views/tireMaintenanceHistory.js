// Older tire history stores one row per field without an edit ID. Adjacent fields
// with the same operator, reason and displayed second belong to one edit display.
export function groupTireMaintenanceHistory(history = []) {
  const groups = [];
  for (const change of history) {
    const operatedAt = String(change.operatedAt || '').replace('T', ' ').slice(0, 19);
    const key = JSON.stringify([operatedAt, change.operatorPhone || '', change.reason || '']);
    const previous = groups.at(-1);
    if (previous?.key === key) previous.changes.push(change);
    else groups.push({ key, operatedAt: change.operatedAt, operatorPhone: change.operatorPhone,
      reason: change.reason, changes: [change] });
  }
  return groups;
}
