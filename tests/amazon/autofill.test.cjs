const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const output = process.argv[2];
const script = fs.readFileSync(path.join(output, 'autofill-fixture.js'), 'utf8');
const manualScript = fs.readFileSync(path.join(output, 'autofill-manual-fixture.js'), 'utf8');
const expected = fs.readFileSync(path.join(output, 'expected-name.txt'), 'utf8');
let checks = 0;
function fixture({origin = 'https://aeroplan.rewardops.com', pathname = '/en-CA/affiliate-details/amazon',
    framed = false, visible = true, disabled = false, existing = '', missing = false} = {}) {
  class Input {
    constructor() { this._value = existing; this.tagName = 'INPUT'; this.disabled = disabled; this.events = []; }
    get value() { return this._value; }
    set value(value) { this._value = value; }
    getClientRects() { return visible ? [{}] : []; }
    dispatchEvent(event) { this.events.push(event.type); }
  }
  const number = new Input(), lastName = new Input();
  const window = {};
  window.top = framed ? {} : window;
  const context = {window, location: {origin, pathname}, HTMLInputElement: Input,
    Event: class {constructor(type) {this.type = type;}},
    document: {getElementById: id => missing ? null : id === 'membershipNumber' ? number : lastName}};
  vm.createContext(context);
  return {context, number, lastName, run: () => vm.runInContext(script, context), manual: () => vm.runInContext(manualScript, context)};
}
function check(run) {run(); checks++;}
check(() => {
  const f = fixture();
  assert.equal(f.run(), 'filled');
  assert.equal(f.number.value, '000000000');
  assert.equal(f.lastName.value, expected);
  assert.equal(f.context.pwned, undefined, 'Input must not be evaluated as code');
  assert.deepEqual(f.number.events, ['input', 'change']);
  assert.deepEqual(f.lastName.events, ['input', 'change']);
  assert.equal(f.run(), 'user-entered');
});
check(() => {
  const f = fixture(); f.run();
  f.number.value = ''; f.lastName.value = '';
  assert.equal(f.run(), 'already-filled', 'Do not automatically undo a deliberate clear');
  assert.equal(f.manual(), 'filled', 'Manual fill can restore cleared details');
});
check(() => {
  const f = fixture(); f.run();
  const reopened = fixture();
  f.context.document = reopened.context.document;
  assert.equal(f.run(), 'filled', 'A replacement login modal can be filled');
  assert.equal(reopened.number.value, '000000000');
});
for (const options of [{origin:'https://evil.test'}, {origin:'http://aeroplan.rewardops.com'},
    {origin:'https://aeroplan.rewardops.com.evil.test'}, {pathname:'/en-CA/other'}, {framed:true}]) {
  check(() => {const f=fixture(options); assert.equal(f.run(),'wrong-origin'); assert.equal(f.number.value,'');});
}
for (const options of [{visible:false}, {disabled:true}, {missing:true}]) {
  check(() => {const f=fixture(options); assert.equal(f.run(),'waiting'); assert.equal(f.number.value,'');});
}
check(() => {const f=fixture({existing:'user-edit'}); assert.equal(f.run(),'user-entered'); assert.equal(f.number.value,'user-edit');});
console.log(`PASS: ${checks} autofill scenarios (including origin races, frames, hidden forms, preserved edits and script injection)`);
