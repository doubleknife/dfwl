import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const lines = readFileSync(resolve(root, 'docs/openapi.yaml'), 'utf8').split(/\r?\n/);

function block(name, indentation, within = lines) {
  const prefix = ' '.repeat(indentation);
  const start = within.findIndex((line) => line === `${prefix}${name}:`);
  if (start < 0) throw new Error(`OpenAPI contract missing: ${name}`);
  const end = within.findIndex((line, index) => index > start && line.startsWith(prefix)
    && line[indentation] !== ' ' && /^[A-Za-z][A-Za-z0-9]*:/.test(line.slice(indentation)));
  return within.slice(start + 1, end < 0 ? undefined : end);
}

function values(section, label) {
  const match = section.join('\n').match(/enum: \[([^\]]+)\]/);
  if (!match) throw new Error(`OpenAPI enum missing: ${label}`);
  return match[1].split(',').map((value) => value.trim());
}

function schema(name) {
  return block(name, 4);
}

const options = {
  templateCategories: values(schema('ApprovalTemplateCategory'), 'ApprovalTemplateCategory'),
  evidenceRequirements: values(schema('ApprovalEvidenceRequirement'), 'ApprovalEvidenceRequirement'),
  expenseTypes: values(schema('ExpenseType'), 'ExpenseType'),
  inputModes: values(block('inputModes', 8, schema('ApprovalExpenseConfigRequest')), 'inputModes'),
  attributionTypes: values(block('attributionTypes', 8, schema('ApprovalExpenseConfigRequest')), 'attributionTypes')
};

const output = resolve(root, 'admin-web/src/generated/approvalOptions.js');
mkdirSync(dirname(output), { recursive: true });
writeFileSync(output, `// Generated from docs/openapi.yaml. Run npm test/build/dev to refresh.\n${Object.entries(options)
  .map(([name, entries]) => `export const ${name} = Object.freeze(${JSON.stringify(entries)});`)
  .join('\n')}\n`);
