const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const { hasPermission, visibleHomeActions } = require('../utils/permissions');
const source = fs.readFileSync(path.join(__dirname, '../pages/dispatch/index.js'), 'utf8');
function harness(responder) {
  let definition; const calls = [], modals = [];
  const wx = { showModal: o => { modals.push(o.content); o.success({confirm:true}); }, pageScrollTo: () => {} };
  vm.runInNewContext(source, { Page: d => { definition = d; }, Date, Math, wx, getApp: () => ({globalData:{}}),
    require: p => p.endsWith('/request') ? {request: async o => { calls.push(o); return responder(o); }}
      : require(path.resolve(__dirname, '../pages/dispatch',p)) });
  const page = { ...definition, data: JSON.parse(JSON.stringify(definition.data)) };
  page.setData = patch => Object.entries(patch).forEach(([key,value]) => {
    const keys = key.replace(/\[(\d+)\]/g,'.$1').split('.'); let target = page.data;
    keys.slice(0,-1).forEach(k => { target = target[k]; }); target[keys.at(-1)] = value;
  });
  page.setData({denied:false,canPublish:true});
  return {page,calls,modals};
}
const template = {id:1,stableCode:'L0000001',name:'线路',label:'线路',defaultUnitPrice:100,defaultInformationFee:10,defaultDriverSalary:20};
function fill(page,external=false) {
  page.setData({driversFresh:true,templates:[template], drivers:[{id:2,status:1,name:'司机',phone:'19900000000',driverType:external?'OUTSOURCED':'INTERNAL',currentVehicleId:external?null:3,currentVehiclePlateNo:'测试001'}]});
  page.chooseTemplate({detail:{value:template.stableCode}}); page.chooseDriver({detail:{value:2}});
  page.input({currentTarget:{dataset:{field:'actualUnitPrice'}},detail:{value:'123.45'}});
  page.input({currentTarget:{dataset:{field:'actualInformationFee'}},detail:{value:'0'}});
  page.input({currentTarget:{dataset:{field:'initialSalary'}},detail:{value:'56.78'}});
  if(external) page.input({currentTarget:{dataset:{field:'outsourcedPlateNo'}},detail:{value:'外协002'}});
}
const list = {records:[{id:77,taskNo:'T77',status:'PUBLISHED'}],total:1};
async function run() {
  const user = {role:{code:'DISPATCH'},permissions:['PAGE_MINI_DISPATCH','BUTTON_MINI_DISPATCH_PUBLISH']};
  assert(visibleHomeActions(user,null).some(a=>a.key==='dispatch'));
  assert(!hasPermission({permissions:['BUTTON_MINI_DISPATCH_PUBLISH']},'BUTTON_MINI_DISPATCH_PUBLISH'));
  assert(!visibleHomeActions({...user,role:{code:'DRIVER'}},null).some(a=>a.key==='dispatch'));
  for(const external of [false,true]) {
    let release; const wait = new Promise(resolve=>{release=resolve;});
    const {page,calls,modals} = harness(async o => {
      if(o.url.endsWith('/preview')) {await wait; return {previewId:'p',valid:true,rows:[{templateName:'线路',vehiclePlate:external?'外协002':'测试001'}]};}
      if(o.url.endsWith('/confirm')) return {rows:[{taskId:77,taskNo:'T77'}]};
      return list;
    });
    fill(page,external);
    const before = JSON.stringify(page.data.form);
    await page.switchTab({currentTarget:{dataset:{tab:'sent'}}});
    await page.switchTab({currentTarget:{dataset:{tab:'new'}}});
    assert.equal(JSON.stringify(page.data.form),before);
    const pending = page.publish(); await page.publish(); release(); await pending;
    assert.equal(calls.filter(c=>c.url.endsWith('/confirm')).length,1);
    const row = calls.find(c=>c.url.endsWith('/confirm')).data.batch.rows[0];
    assert.equal(row.actualUnitPrice,'123.45');assert.equal(row.actualInformationFee,'0');assert.equal(row.initialSalary,'56.78');
    assert.equal(row.outsourcedPlateNo,external?'外协002':null);
    assert.equal(page.data.tab,'sent');assert.equal(page.data.highlight,'77');assert.equal(page.data.tasks[0].highlighted,true);
    assert(modals[0].includes('123.45'));
  }
  let attempt=0;
  const failed=harness(async o=> {
    if(o.url.endsWith('/preview')) return {previewId:'p',valid:true,rows:[{templateName:'线路'}]};
    if(o.url.endsWith('/confirm')) {if(++attempt===1)throw new Error('网络断开');return {rows:[{taskId:77,taskNo:'T77'}]};} return list;
  });
  fill(failed.page); const original=JSON.stringify(failed.page.data.form);
  await failed.page.publish();assert.equal(JSON.stringify(failed.page.data.form),original);assert.equal(failed.page.data.error,'网络断开');
  await failed.page.publish();const confirmations=failed.calls.filter(c=>c.url.endsWith('/confirm'));
  assert.equal(confirmations[0].data.requestKey,confirmations[1].data.requestKey);
  const invalid=harness(async()=>{throw new Error('must not call');}); fill(invalid.page);
  invalid.page.data.driver.currentVehicleId=null;await invalid.page.publish();assert.equal(invalid.calls.length,0);
  fill(invalid.page,true);invalid.page.data.form.outsourcedPlateNo='';await invalid.page.publish();assert.equal(invalid.calls.length,0);
  fill(invalid.page);invalid.page.data.form.initialSalary='1.234';await invalid.page.publish();assert.equal(invalid.calls.length,0);
  const action=harness(async o=>o.url.endsWith('/transfer-context')?{expectedVersion:1,expectedCurrentParticipantId:8,participants:[{id:8,driverId:3,driverName:'原司机',salary:10}]}:list);
  fill(action.page,true);action.page.setData({canTransfer:true,canCancel:true,tasks:[{id:77,taskNo:'T77',status:'IN_TRANSIT'}]});
  await action.page.openAction({currentTarget:{dataset:{type:'transfer',id:77}}});
  action.page.chooseTransferDriver({detail:{value:2}});action.page.setData({newSalary:'20',transferPlate:'外协002',reason:'转交'});
  await action.page.submitAction();assert(action.calls.some(c=>c.url.endsWith('/77/transfer')));assert.equal(action.page.data.action,null);
  action.page.setData({tasks:[{id:77,taskNo:'T77',version:1,status:'PUBLISHED'}]});
  await action.page.openAction({currentTarget:{dataset:{type:'cancel',id:77}}});action.page.setData({reason:'取消'});
  await action.page.submitAction();assert(action.calls.some(c=>c.url.endsWith('/77/cancel')));
  const detail = harness(async o => o.url.includes('/tasks?') ? list : ({task:{id:77,taskNo:'T77',status:'PUBLISHED'}, actualInformationFee:0,grossWeight:null}));
  detail.page.setData({tasks:list.records,tab:'sent',pageNo:2});fill(detail.page);
  const preserved = JSON.stringify(detail.page.data.form);
  await detail.page.changeStatus({detail:{value:'4'}});
  assert(detail.calls.at(-1).url.includes('status=COMPLETED&pageNo=1'));
  await detail.page.openDetail({currentTarget:{dataset:{id:77}}});
  assert.equal(detail.page.data.detail.taskNo,'T77');assert.equal(detail.page.data.detail.actualInformationFee,'0');
  assert.equal(detail.page.data.detail.grossWeight,'—');
  detail.page.closeDetail();assert.equal(detail.page.data.statusIndex,4);assert.equal(JSON.stringify(detail.page.data.form),preserved);
  const missing=harness(async()=>{throw new Error('任务不存在');});missing.page.setData({tasks:list.records});
  await missing.page.openDetail({currentTarget:{dataset:{id:77}}});assert.equal(missing.page.data.detail,null);assert.equal(missing.page.data.detailError,'任务不存在');
  let resolveDetail;const late=harness(()=>new Promise(r=>{resolveDetail=r;}));late.page.setData({tasks:list.records});
  const pendingDetail=late.page.openDetail({currentTarget:{dataset:{id:77}}});late.page.closeDetail();resolveDetail({task:list.records[0]});await pendingDetail;
  assert.equal(late.page.data.detail,null);assert.equal(late.page.data.detailOpen,false);
  // Personal identity uses the unified server field; no client-side name editing.
  assert(fs.readFileSync(path.join(__dirname,'../pages/profile/index.wxml'),'utf8').includes('user.name || user.phone'));
  console.log('PASS mini dispatch: permissions, tabs, internal/external, amounts, duplicate clicks, retry, validation, transfer/cancel, identity');
}
run().catch(e=>{console.error(e);process.exitCode=1;});
