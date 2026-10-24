import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import {ref,watch,reactive,nextTick} from 'vue';
import {parse,compileScript,compileTemplate} from '@vue/compiler-sfc';
const files=['components/PasswordInput.vue','components/DriverManagementSample.vue','layouts/AppLayout.vue','views/LoginView.vue','views/SystemUi1View.vue'];
for(const file of files){
 const {descriptor,errors}=parse(fs.readFileSync('src/'+file,'utf8'),{filename:file});assert.deepEqual(errors,[]);
 const script=compileScript(descriptor,{id:file});
 const template=compileTemplate({source:descriptor.template.content,filename:file,id:file,compilerOptions:{bindingMetadata:script.bindings}});
 assert.deepEqual(template.errors,[]);
}
const source=fs.readFileSync('src/components/PasswordInput.vue','utf8');
const {descriptor}=parse(source);
const props=reactive({modelValue:'example'});
const context={ref,watch,defineOptions:()=>{},defineProps:()=>props,defineEmits:()=>()=>{}};
vm.createContext(context);
vm.runInContext(descriptor.scriptSetup.content.replace(/^import .*;$/gm,'')+'\nglobalThis.state=visible;',context);
assert.equal(context.state.value,false);
context.state.value=true;assert.equal(props.modelValue,'example');
props.modelValue='';await nextTick();assert.equal(context.state.value,false);
assert.match(source,/type="button"/);assert.match(source,/v-bind="\$attrs"/);
console.log('PASS all password views compile; default masking, independent visibility, value preservation and clearing reset');
