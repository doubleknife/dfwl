import assert from 'node:assert/strict';
import fs from 'node:fs';
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc';
for (const file of ['src/views/SystemUi1View.vue','src/layouts/AppLayout.vue']) {
  const source = fs.readFileSync(new URL('../'+file,import.meta.url),'utf8');
  const {descriptor, errors} = parse(source); assert.equal(errors.length,0);
  const script = compileScript(descriptor,{id:file});
  const compiled = compileTemplate({id:file,source:descriptor.template.content,filename:file,
    compilerOptions:{bindingMetadata:script.bindings}});
  assert.equal(compiled.errors.length,0);
  if(file.includes('SystemUi1')) {
    assert.match(source,/v-model.trim="userForm.name" required maxlength="128"/);
    assert.match(source,/name: userForm.name.trim\(\)/);
    assert.match(source,/userDetail.name/);
    assert.match(source,/key:'name',label:'姓名'/);
    assert.match(source,/请在司机管理维护/);
  } else assert.match(source,/姓名：\{\{ authStore.user\?\.name \}\}/);
}
console.log('PASS Web name forms, read-only identity and Vue compilation (no GUI/build)');
