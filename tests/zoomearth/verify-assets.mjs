// Runs real methods extracted from the supplied APK outputs, with native/network
// boundaries replaced by counters. No proprietary app bundle is checked in.
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import assert from 'node:assert/strict';

const root = process.argv[2];
let checks = 0;
function equal(actual, expected, label) { assert.deepEqual(actual, expected, label); checks++; }
function between(source, start, end) {
  const a = source.indexOf(start), b = source.indexOf(end, a + start.length);
  assert(a >= 0 && b > a, `Missing method boundary: ${start}`);
  return source.slice(a, b);
}
function method(source, pattern) {
  const match = source.match(pattern);
  assert(match, `Missing method: ${pattern}`);
  return match[0];
}

for (let mask = 0; mask < 16; mask++) {
  const source = fs.readFileSync(path.join(root, mask ? `mask-${mask}/app.js` : 'original.js'), 'utf8');
  new vm.Script(source); checks++; // Parse the entire shipped JavaScript, not just edited fragments.
  const pro = !!(mask & 1), ads = !!(mask & 2), favorites = !!(mask & 4), quiet = !!(mask & 8);
  const localGetter = method(source, /get xt\(\)\{[^{}]*\}/);
  const serverGetter = pro ? method(source, /get mlkemServerPro\(\)\{[^{}]*\}/) : '';
  const routing = source.match(/pd=\(\)=>([^,]+),md=/)[1];

  // Local controls must survive account refresh/logout without turning an
  // unentitled account's map requests into paid /pro/ requests.
  for (const loggedIn of [false, true]) for (const tier of ['free', 'pro']) for (const storePro of [false, true]) {
    const account = vm.runInNewContext(`({status:{isLoggedIn:${loggedIn},entitlement:{tier:${JSON.stringify(tier)}}},
      get isLoggedIn(){return this.status.isLoggedIn},${localGetter}${serverGetter ? ',' + serverGetter : ''}})`,
      {Nt: {PRO: 'pro'}, ca: {j: storePro}});
    const genuine = loggedIn && tier === 'pro' || storePro;
    equal(account.xt, pro || genuine, 'Local entitlement');
    equal(vm.runInNewContext(routing, {ac: account}), genuine, 'Server data follows genuine entitlement');
    equal(account.status.entitlement.tier, tier, 'Reading local Pro does not alter account tier');
    account.status.isLoggedIn = false;
    account.status.entitlement.tier = 'free';
    equal(account.xt, pro || storePro, 'Logout/refresh does not undo local Pro');
  }

  const visible = method(source, /ys\(\)\{return\(this\.gs\.locations\|\|\[\]\)\.slice\([^{}]+\)\}/);
  const count = method(source, /get bs\(\)\{[^{}]*\}/);
  const full = method(source, /get As\(\)\{[^{}]*\}/);
  for (const isPro of [false, true]) {
    const cap = favorites ? 100 : isPro ? 30 : 3;
    for (const size of [0, cap - 1, cap, cap + 5]) {
      const data = Array.from({length: size}, (_, id) => ({id, title: `Place ${id}`}));
      const object = vm.runInNewContext(`({gs:{locations:data},${visible},${count},${full}})`, {data, ac: {xt: isPro}});
      equal(object.ys().length, Math.min(size, cap), 'Visible places cap');
      equal(object.As, size >= cap, 'Add guard matches visible cap');
      equal(object.gs.locations.length, size, 'Existing saved places are never trimmed');
      if (size) equal(object.ys().at(-1).id, size - 1, 'Original saved-place order is retained');
    }
  }

  if (ads) {
    const calls = {initialize: 0, show: 0, resume: 0, timers: 0};
    class EventSource { constructor() { this.events = []; } dispatchEvent(e) { this.events.push(e.type); } }
    const AdController = vm.runInNewContext(`(${between(source, 'class DA extends Co', 'const OA=')})`, {
      Co: EventSource, EA: {addListener() {}, removeAllListeners() {}, hideBanner() {},
        initialize() { calls.initialize++; return Promise.resolve({canRequestAds: true}); },
        showBanner() { calls.show++; }, resumeBanner() { calls.resume++; }},
      SA: {FAILED_TO_LOAD: 'failed', LOADED: 'loaded', SIZE_CHANGED: 'size'},
      Bo: {footerHeight: 50, I: false}, ac: {xt: pro}, Ti: 'init', zi: 'click', wi: 'click',
      cr() {}, Er() { return {}; }, document: {}, Yr() {}, ae: (v, lo, hi) => Math.max(lo, Math.min(hi, v)),
      navigator: {onLine: false}, setTimeout() { calls.timers++; }, clearTimeout() {}, IA: 15000
    });
    const controller = new AdController();
    controller.init(); controller.init(); controller.ps();
    equal(controller.isInitialized, true, 'Offline initialization completes without retries');
    equal(controller.canRequestAds, false, 'Ad footer receives disabled state');
    equal(Array.from(controller.events), ['init'], 'One completion event, no startup loop');
    controller.canRequestAds = true; controller.isBannerLoaded = true; controller.ps();
    equal(calls, {initialize: 0, show: 0, resume: 0, timers: 0}, 'No banner initialization/show/resume/timers');
  }

  let reviews = 0;
  const reviewState = {reviewRequestCount: 0, reviewRequestTime: 0};
  vm.runInNewContext(`${between(source, 'Wl=()=>{', ',Bl=()=>')};Wl()`, {
    Ho: reviewState, Gl: [0], navigator: {onLine: true}, document: {hidden: false},
    Fe: {isConnectionSlow: false, bootTime: 0, resumeTime: 0}, _l: {request() { reviews++; }}
  });
  equal(reviews, quiet ? 0 : 1, 'Automatic review request');
  equal(reviewState.reviewRequestCount, quiet ? 0 : 1, 'Quiet mode preserves stored review counters');

  const show = between(source, 'show(t){if(', 'showErrorAlert(){');
  for (const placement of ['auto', 'manual']) {
    let offers = 0, errors = 0;
    const paywall = vm.runInNewContext(`({${show},showErrorAlert(){error()}})`, {
      Bo: {I: false}, ac: {xt: pro}, vc: {AUTO: 'auto'},
      ca: {_() { offers++; return undefined; }}, error() { errors++; }
    });
    paywall.show(placement);
    const blocked = pro || quiet && placement === 'auto';
    equal(offers, blocked ? 0 : 1, 'Automatic paywall suppression preserves manual flow');
    equal(errors, blocked || placement === 'auto' ? 0 : 1, 'Manual error handling remains available');
  }
  console.log(`PASS JavaScript behavior mask ${mask}`);
}
fs.writeFileSync(path.join(root, 'javascript-verification.json'), JSON.stringify({result: 'passed', masks: 16, checks,
  scope: 'Syntax of full bundle and extracted real methods with mocked native/network boundaries', phone_tested: false}, null, 2) + '\n');
console.log(`PASS ${checks} JavaScript behavior checks`);
