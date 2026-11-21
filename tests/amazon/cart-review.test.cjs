const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const script = fs.readFileSync('patches/src/main/resources/aeroplan/mlkem_aeroplan_review.js','utf8');
class FixedDate extends Date { static now() { return Date.UTC(2026,10,21); } }
function node(values = {}) {
    return {textContent:values.text || '', parentElement:null, closest:()=>null,
        getAttribute:k => (values.attrs || {})[k] || null,
        querySelector:s => s.includes('product-title') ? {textContent:values.title || ''} : null,
        querySelectorAll:()=>[]};
}
function check(store, path, items=[], count='', crumbs=[], id='', clock=FixedDate, live=null) {
    const window={}; window.top=window;
    const loc=new URL('https://www.'+store+'.ca'+path);
    const doc={
        querySelector:s => s.includes('cart-item-count') ? {textContent:count} : null,
        querySelectorAll:s => {
            if (s.startsWith('#wayfinding-')) return crumbs.map(text=>node({text}));
            if (s.startsWith('[data-order-id]')) return id ? [node({attrs:{'data-order-id':id}})] : [];
            if (s.includes('checkout-item')) return items;
            return [];
        }
    };
    return vm.runInNewContext(script+'('+JSON.stringify(store)+','+JSON.stringify(live)+')',{window,location:loc,document:doc,URL,Date:clock});
}
const row=(title,category,quantity)=>node({title,attrs:{'data-category':category,'data-quantity':quantity}});
const walmart=check('walmart','/en/cart',[row('Oats','Grocery','1'),row('Sparkling water','Grocery','1'),row('Earbuds','Electronics','1'),row('Notebook','Stationery','1'),row('Mystery item','','1')],'5 items');
assert.equal(walmart.whole,true);assert.equal(walmart.rows.filter(r=>r.state==='flag').length,3);
assert.equal(walmart.rows[4].state,'unknown');assert.equal(walmart.rows[3].state,'review');
assert.equal(check('amazon','/dp/B012345678',[],'',['Toys & Games']).state,'flag');
assert.equal(check('amazon','/dp/B012345678',[],'',['Jeux vidéo']).state,'flag');
assert.equal(check('walmart','/en/ip/product/123',[],'',['Épicerie']).state,'flag');
assert.equal(check('walmart','/checkout',[row('Camera','Electronics','1')],'3 items').whole,false);
assert.equal(check('amazon','/gp/cart/view.html',[row('Toys & Games gift set','','1')],'1 item').rows[0].state,'unknown');
assert.equal(check('amazon','/gp/buy/thankyou',[],'',[],'702-1234567-1234567').id,'702-1234567-1234567');
assert.equal(check('walmart','/checkout',[],'',[],'60000012345678').kind,'checkout');
assert.equal(check('amazon','/dp/B012345678',[],'',['Toys & Games'],'',class extends Date {static now(){return Date.UTC(2026,11,5)}}).state,'unknown');
assert.equal(check('amazon','/dp/B012345678',[],'',['Beauty'],'',FixedDate,{included:['Beauty']}).state,'listed');
assert.equal(check('walmart','/en/ip/shoes/123',[],'',['Footwear'],'',FixedDate,{excluded:['Footwear']}).state,'flag');
assert.equal(check('amazon','/dp/B012345678',[],'',['Toys & Games'],'',FixedDate,{included:['Toys & Games']}).state,'listed');
console.log('Cart, product, checkout, expiry and confirmation fixtures passed.');
