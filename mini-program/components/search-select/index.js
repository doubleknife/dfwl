const { filterOptions } = require('../../utils/searchSelect');

Component({
  properties: {
    title: { type: String, value: '选择选项' },
    options: { type: Array, value: [] },
    currentValue: { type: null, value: null },
    displayText: { type: String, value: '' },
    labelKey: { type: String, value: 'label' },
    valueKey: { type: String, value: 'value' },
    placeholder: { type: String, value: '请选择' },
    disabled: { type: Boolean, value: false },
    allowClear: { type: Boolean, value: false },
    multiLine: { type: Boolean, value: false }
  },
  data: { opened: false, query: '', filteredOptions: [], keyboardHeight: 0, sheetStyle: '', listStyle: '' },
  observers: {
    'options,currentValue,labelKey,valueKey': function (options, currentValue, labelKey, valueKey) {
      this.setData({ filteredOptions: filterOptions(options, this.data.query, labelKey, valueKey, currentValue) });
      if (this.data.opened) this.updateKeyboard(this.data.keyboardHeight);
    }
  },
  methods: {
    noop() {},
    open() {
      if (this.data.disabled) return;
      this.setData({ opened: true, query: '',
        filteredOptions: filterOptions(this.data.options, '', this.data.labelKey,
          this.data.valueKey, this.data.currentValue) });
      this.updateKeyboard(0);
    },
    close() {
      this.setData({ opened: false, query: '' });
      this.updateKeyboard(0);
    },
    onSearch(event) {
      const query = event.detail.value || '';
      this.setData({ query, filteredOptions: filterOptions(this.data.options, query,
        this.data.labelKey, this.data.valueKey, this.data.currentValue) });
      this.updateKeyboard(this.data.keyboardHeight);
    },
    onKeyboardHeightChange(event) {
      this.updateKeyboard(Number(event.detail.height) || 0);
    },
    updateKeyboard(height) {
      const windowHeight = wx.getSystemInfoSync().windowHeight;
      const available = Math.max(120, windowHeight - height - 16);
      const contentHeight = this.data.filteredOptions.length * (this.data.multiLine ? 72 : 48)
        + (this.data.allowClear ? 48 : 0);
      const listHeight = Math.max(80, Math.min(available - 160, contentHeight || 120));
      this.setData({ keyboardHeight: height, sheetStyle: `bottom:${height}px;max-height:${available}px`,
        listStyle: `height:${listHeight}px` });
    },
    choose(event) {
      const index = Number(event.currentTarget.dataset.index);
      const option = this.data.options[index];
      if (option === undefined || option === null || option.disabled) return;
      const value = option && typeof option === 'object' ? option[this.data.valueKey] : option;
      const label = option && typeof option === 'object' ? option[this.data.labelKey] : option;
      this.triggerEvent('change', { value, index, label: String(label == null ? '' : label) });
      this.close();
    },
    clear() {
      if (!this.data.allowClear) return;
      this.triggerEvent('change', { value: null, index: -1, label: '' });
      this.close();
    }
  }
});
