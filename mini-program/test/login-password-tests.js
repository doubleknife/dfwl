const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
let page, submitted, redirects = 0;
vm.runInNewContext(fs.readFileSync('mini-program/pages/login/index.js','utf8'), {
 Page: p => page=p,
 require: () => ({request: async r => {submitted=r.data;return {accessToken:'test',user:{}};}}),
 getApp: () => ({setSession:()=>{}}),wx:{redirectTo:()=>redirects++}
});
page.setData = function(p){Object.assign(this.data,p);};
(async()=>{
 assert.equal(page.data.showPassword,false);
 page.onPhone({detail:{value:'13900000000'}});page.onPassword({detail:{value:'test-password'}});
 page.togglePasswordVisibility();assert.equal(page.data.showPassword,true);
 page.togglePasswordVisibility();assert.equal(page.data.showPassword,false);
 assert.equal(page.data.password,'test-password');assert.equal(submitted,undefined);
 await page.login();assert.equal(submitted.password,'test-password');assert.equal(redirects,1);
 console.log('PASS login password toggle: hidden by default, toggle preserves input, no unintended submit, login preserved');
})().catch(e=>{console.error(e);process.exitCode=1;});
