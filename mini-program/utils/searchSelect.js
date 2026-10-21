function optionLabel(option, labelKey) {
  const displayed = option && typeof option === 'object' ? option[labelKey] : option;
  return displayed == null ? '' : String(displayed);
}

function optionValue(option, valueKey) {
  return option && typeof option === 'object' ? option[valueKey] : option;
}

function filterOptions(options, query, labelKey = 'label', valueKey = 'value', currentValue) {
  const needle = String(query || '').trim().toLocaleLowerCase();
  return (options || []).map((option, index) => ({
    index,
    label: optionLabel(option, labelKey),
    value: optionValue(option, valueKey),
    disabled: !!(option && typeof option === 'object' && option.disabled),
    selected: currentValue !== null && currentValue !== undefined && currentValue !== ''
      && String(optionValue(option, valueKey)) === String(currentValue)
  })).filter((option) => option.label.toLocaleLowerCase().includes(needle));
}

module.exports = { optionLabel, optionValue, filterOptions };
