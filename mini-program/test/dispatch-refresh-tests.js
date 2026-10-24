const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname,'../pages/dispatch/index.js'),'utf8');
let definition, records = [], fail = false, driverCalls = 0, writes = 0;
const user = {permissions:['PAGE_MINI_DISPATCH','BUTTON_MINI_DISPATCH_PUBLISH'],role:{code:'ADMIN'}};
vm.runInNewContext(source, {Date, Math, Page:d=>{definition=d;},getApp:()=>({globalData:{}}),wx:{},
  require:p=>p.endsWith('/request')?{request:async o=>{
    if(o.method==='POST') writes++;
    if(o.url==='/me')return user;
    if(o.url.endsWith('/templates'))return [];
    if(o.url.includes('/driver-options')){
      driverCalls++; if(fail)throw new Error('网络失败');
      const page=Number(o.url.match(/pageNo=(\d+)/)[1]);
      return {records:records.slice((page-1)*100,page*100),total:records.length};
    }
    return {records:[],total:0};
  }}:require(path.resolve(__dirname,'../pages/dispatch',p))
});
const p={...definition,data:JSON.parse(JSON.stringify(definition.data))};
p.setData=patch=>Object.entries(patch).forEach(([key,v])=>{
 const parts=key.split('.');let target=p.data;parts.slice(0,-1).forEach(k=>target=target[k]);target[parts.at(-1)]=v;
});
const driver=(overrides={})=>({id:1,name:'甲',phone:'19900000001',status:1,driverType:'INTERNAL',currentVehicleId:11,currentVehiclePlateNo:'测试11',...overrides});
(async()=>{
 records=[driver()];await p.onShow();assert.equal(driverCalls,1);assert(p.data.driversFresh);console.log('PASS initial load');
 p.chooseDriver({detail:{value:1}});
 p.setData({'form.businessDate':'2026-10-01','form.templateStableCode':'L001','form.actualUnitPrice':'1.20','form.actualInformationFee':'3','form.initialSalary':'4'});
 const other=()=>JSON.stringify({...p.data.form,driverId:null,outsourcedPlateNo:null});const before=other();
 records=[driver({name:'乙',phone:'19900000002',currentVehicleId:22,currentVehiclePlateNo:'测试22'})];
 await p.onShow();assert.equal(driverCalls,2);assert.equal(p.data.driver.currentVehicleId,22);assert.equal(p.data.driver.name,'乙');assert.equal(p.data.driver.phone,'19900000002');assert.equal(p.data.form.driverId,1);console.log('PASS reentry updates binding and identity');
 records=[driver({currentVehicleId:33,currentVehiclePlateNo:'测试33'})];await p.refreshDrivers();assert.equal(p.data.driver.currentVehicleId,33);console.log('PASS manual refresh');
 records=[driver({currentVehicleId:null,currentVehiclePlateNo:null})];await p.refreshDrivers();assert.equal(p.data.driver,null);assert.equal(p.data.form.driverId,'');assert(p.data.driverError.includes('重新选择'));console.log('PASS lost binding clears selection');
 p.chooseDriver({detail:{value:1}});records=[driver({currentVehicleId:44,currentVehiclePlateNo:'测试44'})];await p.refreshDrivers();assert.equal(p.data.driver.currentVehicleId,44);assert.equal(p.data.form.driverId,1);console.log('PASS binding added after previously unbound');
 records=[];await p.refreshDrivers();assert.equal(p.data.driver,null);assert.equal(p.data.form.driverId,'');console.log('PASS disabled/absent driver clears selection');
 records=[driver()];await p.refreshDrivers();p.chooseDriver({detail:{value:1}});
 fail=true;await p.refreshDrivers();assert(!p.data.driversFresh);assert(p.data.driverError.includes('刷新失败'));await p.publish();assert.equal(writes,0);console.log('PASS failure prevents stale submission');
 fail=false;records=[...Array.from({length:100},(_,i)=>driver({id:i+2})),driver({name:'第二页司机'})];await p.refreshDrivers();assert.equal(p.data.driver.name,'第二页司机');assert.equal(p.data.form.driverId,1);assert.equal(other(),before);console.log('PASS selected driver beyond first page and other fields preserved');
 records=[driver({status:0})];await p.refreshDrivers();assert.equal(p.data.driver,null);console.log('PASS disabled response cannot retain selection');
 assert(!source.includes('setInterval'));assert(!source.includes('onSearch'));console.log('PASS no polling or remote keyword search');
})().catch(e=>{console.error(e);process.exitCode=1;});
