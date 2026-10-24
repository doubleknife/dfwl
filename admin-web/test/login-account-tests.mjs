import assert from 'node:assert/strict';
import fs from 'node:fs';
import {parse,compileScript,compileTemplate} from '@vue/compiler-sfc';
for(const filename of ['src/views/LoginView.vue','src/views/SystemUi1View.vue']) {
 const source=fs.readFileSync(filename,'utf8');
 const {descriptor,errors}=parse(source,{filename});assert.equal(errors.length,0);
 const script=compileScript(descriptor,{id:filename});
 const template=compileTemplate({source:descriptor.template.content,filename,id:filename,compilerOptions:{bindingMetadata:script.bindings}});
 assert.deepEqual(template.errors,[]);
 if(filename.includes('LoginView')) {
  assert.match(source, /<PasswordInput v-model="form.password" autocomplete="current-password"/);
 }else{
  assert.match(source,/\[\{key:'phone',label:'登录账号'\},\{key:'name',label:'姓名'\}/);
  assert.match(source,/v-model.trim="userForm.phone"[^>]*:disabled="!!userForm.id"/);
 }
 console.log('PASS Vue compilation and account/password UI contract: '+filename);
}
