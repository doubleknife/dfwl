function formatDateTime(value) {
  return String(value || '').replace('T', ' ').slice(0, 19);
}

module.exports = { formatDateTime };
