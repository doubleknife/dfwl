// API LocalDateTime values are local wall-clock times; display them without ISO separators or milliseconds.
export function formatDateTime(value, empty = '—') {
  if (value == null || value === '') return empty;
  const text = String(value);
  const match = text.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})(?::(\d{2}))?(?:\.\d+)?$/);
  return match ? `${match[1]} ${match[2]}:${match[3] || '00'}` : text;
}
