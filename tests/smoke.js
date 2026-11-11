// Quick checks of the DAP with made-up data. Run: DAP_URL=http://localhost:8765/index.html node tests/smoke.js
// (needs playwright-core and a Chromium; set CHROME to its path)
const { chromium } = require('playwright-core');
const fs = require('fs'), os = require('os'), path = require('path');
const APP = process.env.DAP_URL, CHROME = process.env.CHROME || '/opt/pw-browsers/chromium-1243/chrome-linux64/chrome';
let fails = 0; const ok = (c, m) => { console.log((c ? 'PASS ' : 'FAIL ') + m); if (!c) fails++; };
(async () => {
  const errors = [];
  for (const native of [false, true]) {
    const ctx = await chromium.launchPersistentContext(fs.mkdtempSync(path.join(os.tmpdir(), 'dap-smoke-')), { executablePath: CHROME, headless: true, args: ['--no-sandbox'], viewport: native ? { width: 390, height: 844 } : { width: 1366, height: 900 }, isMobile: native, hasTouch: native });
    if (native) await ctx.addInitScript(() => { const pl = () => new Proxy({}, { get: () => async () => ({ notifications: [], display: 'granted', contacts: [] }) }); window.Capacitor = { getPlatform: () => 'android' }; window.capacitorExports = { registerPlugin: () => pl() }; });
    const page = ctx.pages()[0]; page.on('pageerror', e => errors.push(e.message));
    await page.clock.install({ time: new Date('2026-11-02T09:00:00') });
    await page.goto(APP); await page.waitForSelector('.sheet');
    const tag = native ? 'app' : 'PC';
    const row = t => page.locator('.row', { has: page.locator('.title', { hasText: t }) });
    const add = async t => { const i = page.locator('.addtask').first(); await i.fill(t); await i.press('Enter'); };
    await add('Pump alignment'); await add('Gasket order');
    const st = row('Pump alignment').locator('.addstep'); await st.fill('Approval'); await st.press('Enter');
    ok(await row('Pump alignment').locator('.mini').count() === 4, `${tag}: a sub-task set adds its steps`);
    await page.locator('h1').click();
    await row('Gasket order').locator('.tbox').click();
    ok(await row('Gasket order').evaluate(e => e.classList.contains('done')), `${tag}: ticking works`);
    await page.click('[data-act="undo"]');
    ok(await row('Gasket order').evaluate(e => !e.classList.contains('done')), `${tag}: undo works`);
    ok((await page.locator('[data-act="org-open"]').count() === 1) === native, `${tag}: the Organogram button is ${native ? 'there' : 'not there'}`);
    await page.clock.setFixedTime(new Date('2026-11-03T09:00:00')); await page.reload(); await page.waitForSelector('.sheet');
    ok(await row('Gasket order').count() === 1 && await page.locator('.daybtn').count() === 7, `${tag}: next day carries open tasks, seven days in the strip`);
    const r = await page.evaluate(() => {           // merging two devices' copies, including the organogram
      const base = JSON.parse(JSON.stringify(db)); base.people = [{ id: 'emp-1', emp: '1', name: 'Test Person', unit: 'u-mech', role: '', phone: '', updatedAt: '2026-11-01T00:00:00Z' }];
      const L = JSON.parse(JSON.stringify(base)), R = JSON.parse(JSON.stringify(base));
      L.people[0].phone = '0300 0000000'; L.people[0].updatedAt = '2026-11-02T00:00:00Z';
      R.people[0].role = 'head'; R.people[0].updatedAt = '2026-11-02T01:00:00Z';
      const m = mergeDb(L, R, base, base).db.people[0];
      return m.phone === '0300 0000000' && m.role === 'head';
    });
    ok(r, `${tag}: a number set on one device and a role set on another both survive syncing`);
    await ctx.close();
  }
  ok(errors.length === 0, 'no script errors ' + errors.join(' | '));
  console.log(fails ? `\n${fails} FAILED` : '\nALL PASSED'); process.exit(fails ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
