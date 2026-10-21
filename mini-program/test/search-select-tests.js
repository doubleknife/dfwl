const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { filterOptions } = require('../utils/searchSelect');

const root = path.resolve(__dirname, '..');

module.exports = [
  ['all reachable single-column dropdowns use SearchSelect while dates remain native', () => {
    const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'));
    let selectors = 0;
    for (const page of app.pages) {
      const markup = fs.readFileSync(path.join(root, `${page}.wxml`), 'utf8');
      assert.doesNotMatch(markup, /<picker\b[^>]*mode=["']selector["']/);
      const used = (markup.match(/<search-select\b/g) || []).length;
      selectors += used;
      if (used) {
        const config = JSON.parse(fs.readFileSync(path.join(root, `${page}.json`), 'utf8'));
        assert.equal(config.usingComponents['search-select'], '../../components/search-select/index');
      }
    }
    assert.equal(selectors, 6);
    assert.match(fs.readFileSync(path.join(root, 'pages/expense-apply/index.wxml'), 'utf8'), /<picker mode="date"/);
    assert.match(fs.readFileSync(path.join(root, 'pages/salary/index.wxml'), 'utf8'), /<picker mode="date"/);
    const component = JSON.parse(fs.readFileSync(path.join(root, 'components/search-select/index.json'), 'utf8'));
    assert.equal(component.component, true);
  }],
  ['search matches only visible labels with Chinese substrings and case-insensitive letters', () => {
    const drivers = [
      { id: 10, name: '严国明', phone: '15800000001' },
      { id: 11, name: '王文', phone: '严国明' },
      { id: 12, name: 'Alan' }
    ];
    for (const query of ['严', '国明', '严国', ' 严国 ']) {
      assert.deepEqual(filterOptions(drivers, query, 'name', 'id').map((row) => row.value), [10]);
    }
    assert.deepEqual(filterOptions(drivers, 'AL', 'name', 'id').map((row) => row.value), [12]);
    assert.deepEqual(filterOptions(drivers, '158', 'name', 'id'), []);
    assert.deepEqual(filterOptions(drivers, '', 'name', 'id').map((row) => row.value), [10, 11, 12]);
    assert.deepEqual(filterOptions([{ id: 2, plateNo: '贵E0200D' }], 'e020', 'plateNo', 'id')
      .map((row) => row.value), [2]);
  }],
  ['SearchSelect returns original ID and index, preserves choice on cancel, and never requests during search', () => {
    const oldComponent = global.Component;
    const oldWx = global.wx;
    let definition;
    let requests = 0;
    global.Component = (value) => { definition = value; };
    global.wx = { getSystemInfoSync: () => ({ windowHeight: 800 }),
      request: () => { requests += 1; throw new Error('search must stay local'); } };
    const file = require.resolve('../components/search-select/index.js');
    try {
      delete require.cache[file];
      require(file);
      const events = [];
      const instance = { ...definition.methods,
        data: { ...definition.data, title: '选择司机', labelKey: 'name', valueKey: 'id',
          currentValue: 10, options: [{ id: 10, name: '严国明' }, { id: 11, name: 'Alan', disabled: true },
            { id: 12, name: '王文' }], disabled: false, allowClear: false },
        setData(update) { Object.assign(this.data, update); },
        triggerEvent(name, detail) { events.push({ name, detail }); } };
      instance.open();
      assert.equal(instance.data.opened, true);
      assert.equal(instance.data.filteredOptions[0].selected, true);
      instance.onSearch({ detail: { value: ' 没有 ' } });
      assert.deepEqual(instance.data.filteredOptions, []);
      instance.onSearch({ detail: { value: '' } });
      assert.equal(instance.data.filteredOptions.length, 3);
      instance.choose({ currentTarget: { dataset: { index: 1 } } });
      assert.equal(events.length, 0);
      instance.close();
      assert.equal(instance.data.currentValue, 10);
      assert.equal(events.length, 0);
      instance.data.disabled = true;
      instance.open();
      assert.equal(instance.data.opened, false);
      instance.data.disabled = false;
      instance.open();
      instance.onSearch({ detail: { value: '王' } });
      instance.choose({ currentTarget: { dataset: { index: 2 } } });
      assert.deepEqual(events, [{ name: 'change', detail: { value: 12, index: 2, label: '王文' } }]);
      assert.equal(instance.data.opened, false);
      instance.onKeyboardHeightChange({ detail: { height: 300 } });
      assert.match(instance.data.sheetStyle, /bottom:300px/);
      instance.data.allowClear = true;
      instance.open();
      instance.clear();
      assert.deepEqual(events[1], { name: 'change', detail: { value: null, index: -1, label: '' } });
      assert.equal(requests, 0);
    } finally {
      global.Component = oldComponent;
      global.wx = oldWx;
      delete require.cache[file];
    }
  }],
  ['selection handlers keep form indices and existing business validation', () => {
    const expense = fs.readFileSync(path.join(root, 'pages/expense-apply/index.js'), 'utf8');
    const tire = fs.readFileSync(path.join(root, 'pages/tire-request/index.js'), 'utf8');
    const approval = fs.readFileSync(path.join(root, 'pages/approvals/index.js'), 'utf8');
    assert.match(expense, /const templateIndex = Number\(e\.detail\.index\)/);
    assert.match(expense, /const vehicle = this\.data\.visibleVehicles\[Number\(e\.detail\.index\)\]/);
    assert.match(expense, /const task = this\.data\.visibleTasks\[Number\(e\.detail\.index\)\]/);
    assert.match(expense, /if \(this\.data\.templateIndex < 0 \|\| this\.data\.vehicleIndex < 0/);
    assert.match(tire, /templateIndex: Number\(event\.detail\.index\)/);
    assert.match(approval, /this\.data\.returnable\[Number\(e\.detail\.index\)\]/);
    const markup = fs.readFileSync(path.join(root, 'pages/expense-apply/index.wxml'), 'utf8');
    assert.match(markup, /disabled="\{\{!!resubmitId\}\}"/);
    assert.match(markup, /options="\{\{visibleTasks\}\}"/);
    assert.match(markup, /options="\{\{visibleVehicles\}\}"/);
  }]
];
