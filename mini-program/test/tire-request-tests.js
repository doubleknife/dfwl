const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function pageWithOcr(candidates, replies = {}) {
  let definition;
  const calls = [];
  let exited = false;
  const source = fs.readFileSync(path.join(__dirname, '../pages/tire-request/index.js'), 'utf8');
  const wx = {
    chooseMedia({ success }) { success({ tempFiles: [{ tempFilePath: 'photo.jpg' }] }); },
    previewImage() {},
    navigateBack({ success }) { exited = true; if (success) success(); },
    reLaunch() { exited = true; }
  };
  vm.runInNewContext(source, {
    Page(value) { definition = value; },
    wx,
    require(name) {
      if (name === '../../utils/request') return {
        uploadFile: async () => ({ id: 91 }),
        downloadAttachment: async (id) => `attachment-${id}.jpg`,
        request: async ({ url, data }) => {
          calls.push({ url, data });
          return replies[url] ||
            ({ id: 92, candidates, candidateText: candidates[0]?.candidate || '', ocrStatus: 'SUCCESS' });
        }
      };
      if (name === '../../utils/permissions') return { hasPermission: () => true };
      throw new Error(`Unexpected module: ${name}`);
    }
  });
  const page = { ...definition, data: { ...definition.data }, setData(patch) { Object.assign(this.data, patch); } };
  page._calls = calls;
  page._exited = () => exited;
  return page;
}

module.exports = [
  ['tire OCR candidates require an explicit selection and permit manual correction', async () => {
    const page = pageWithOcr([
      { candidate: '18DUANAK3', inventoryMatched: false },
      { candidate: 'H621769799', inventoryMatched: true }
    ]);
    page.data.templateIndex = 0;
    page.data.templates = [{ id: 1 }];
    await page.takePhoto({ currentTarget: { dataset: {} } });
    assert.equal(page.data.candidatePickerOpen, true);
    assert.equal(page.data.items.length, 0);
    assert.equal(page.data.candidateOptions.length, 2);
    page.chooseCandidate({ currentTarget: { dataset: { index: 1 } } });
    page.confirmCandidate();
    assert.equal(page.data.items[0].confirmedTireNo, 'H621769799');
    assert.equal(page.data.candidatePickerOpen, false);
    page.onItemNumber({ currentTarget: { dataset: { index: 0 } }, detail: { value: 'h621769798' } });
    assert.equal(page.data.items[0].confirmedTireNo, 'h621769798');
    page.removeItem({ currentTarget: { dataset: { index: 0 } } });
    assert.equal(page.data.items.length, 0);
  }],
  ['failed inventory validation keeps every uploaded photo and row error', async () => {
    const page = pageWithOcr([{ candidate: 'MISSING' }], {
      '/tire-requests/validate': { allValid: false, rows: [{ valid: false, message: '胎号不存在' }] }
    });
    page.data.templateIndex = 0;
    page.data.templates = [{ id: 1 }];
    await page.takePhoto({ currentTarget: { dataset: {} } });
    page.chooseCandidate({ currentTarget: { dataset: { index: 0 } } });
    page.confirmCandidate();
    await page.submit();
    assert.equal(page.data.items.length, 1);
    assert.equal(page.data.items[0].message, '胎号不存在');
    assert.equal(page.data.items[0].photoPath, 'photo.jpg');
    assert.equal(page._calls.some((call) => call.url === '/tire-requests'), false);
    page.onItemNumber({ currentTarget: { dataset: { index: 0 } }, detail: { value: 'H621769799' } });
    assert.equal(page.data.items[0].message, '');
  }],
  ['submit validates first and creates one approval only after every row passes', async () => {
    const page = pageWithOcr([{ candidate: 'H621769799' }], {
      '/tire-requests/validate': { allValid: true, rows: [{ valid: true, message: '库存校验通过' }] },
      '/tire-requests': { id: 123 }
    });
    page.data.templateIndex = 0;
    page.data.templates = [{ id: 1 }];
    await page.takePhoto({ currentTarget: { dataset: {} } });
    page.chooseCandidate({ currentTarget: { dataset: { index: 0 } } });
    page.confirmCandidate();
    await page.submit();
    assert.deepEqual(page._calls.slice(-2).map((call) => call.url), ['/tire-requests/validate', '/tire-requests']);
    assert.equal(page.data.stage, 'DONE');
  }],
  ['cancel exits the tire application without submitting', () => {
    const page = pageWithOcr([]);
    page.cancel();
    assert.equal(page._exited(), true);
    assert.equal(page._calls.some((call) => call.url === '/tire-requests'), false);
  }],
  ['returned tire approval requires a new photo before resubmitting the same approval version', async () => {
    const page = pageWithOcr([], {
      '/me': { id: 102, driverType: 'INTERNAL', currentVehicleId: 20 },
      '/approval-templates/available': [{ id: 7, business_type: 'TIRE_REPLACEMENT' }],
      '/approvals/44': { approval: { id: 44, status: 'RETURNED_TO_APPLICANT',
        business_type: 'TIRE_REPLACEMENT', applicant_user_id: 102, template_id: 7, version: 3 },
      tireRequests: [{ photoAttachmentId: 90, ocrRecordId: 91,
        tireNo: 'H621769799', vehiclePlate: '云A100' }] },
      '/tire-requests/validate': { allValid: true, rows: [{ valid: true, message: '库存校验通过' }] },
      '/tire-requests/approvals/44/resubmit': { approval: { id: 44, status: 'PENDING' } }
    });
    page.onLoad({ resubmitId: '44' });
    await page.load();
    assert.equal(page.data.stage, 'ITEMS');
    assert.equal(page.data.items[0].photoPath, 'attachment-90.jpg');
    page.onItemNumber({ currentTarget: { dataset: { index: 0 } }, detail: { value: 'H621769798' } });
    await page.submit();
    assert.equal(page.data.items[0].message, '请重新上传照片');
    assert.equal(page._calls.some((call) => call.url === '/tire-requests/approvals/44/resubmit'), false);
    await page.takePhoto({ currentTarget: { dataset: { index: 0 } } });
    page.onCandidateDraft({ detail: { value: 'H621769798' } });
    page.confirmCandidate();
    assert.equal(page.data.items[0].needsUpload, false);
    await page.submit();
    assert.deepEqual(page._calls.slice(-2).map((call) => call.url),
      ['/tire-requests/validate', '/tire-requests/approvals/44/resubmit']);
    assert.equal(page._calls.at(-1).data.expectedVersion, 3);
    assert.equal(page._calls.at(-1).data.items[0].confirmedTireNo, 'H621769798');
    assert.equal(page.data.stage, 'DONE');
  }]
];
