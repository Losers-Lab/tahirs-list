/**
 * SC-205 browser pass — the owner/admin photo upload.
 *
 * Runs headless Chromium against a locally running stack (backend on :8090,
 * SvelteKit dev server on :5175). It drives the real UI, captures one screenshot
 * per state and asserts the acceptance criteria as it goes; any mismatch exits
 * non-zero so the run is a gate, not a demo.
 *
 * The owner account and listing are created through the API (setup only); the
 * admin account is created and promoted by the caller, because promotion is an
 * operator action (D-10) with no endpoint.
 *
 * Env: BASE_URL, OWNER_EMAIL, ADMIN_EMAIL, PASSWORD, SHOT_DIR, FIXTURE
 */
const { chromium, request } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');

const BASE = process.env.BASE_URL || 'http://localhost:5175';
// The browser maps `localhost` to the dev container; Node does not, so the
// setup calls (signup/create) use the reachable URL directly.
const API = process.env.API_URL || BASE;
const OWNER_EMAIL = process.env.OWNER_EMAIL;
const ADMIN_EMAIL = process.env.ADMIN_EMAIL;
const PASSWORD = process.env.PASSWORD || 'password123';
const SHOTS = process.env.SHOT_DIR || '/work/docs/features/SC-205/screenshots';
const FIXTURE = process.env.FIXTURE || '/work/e2e/fixtures/storefront.jpg';

if (!OWNER_EMAIL || !ADMIN_EMAIL) {
  console.error('OWNER_EMAIL and ADMIN_EMAIL are required');
  process.exit(2);
}

fs.mkdirSync(SHOTS, { recursive: true });

function check(condition, message) {
  if (!condition) throw new Error(`ASSERT: ${message}`);
  console.log(`ok: ${message}`);
}

async function signupAndLogin(ctx, email) {
  const signup = await ctx.post('/v1/auth/signup', { data: { email, password: PASSWORD } });
  check(signup.ok() || signup.status() === 409, `signup-or-exists ${email} (${signup.status()})`);
  const login = await ctx.post('/v1/auth/login', { data: { email, password: PASSWORD } });
  check(login.ok(), `login ${email} (${login.status()})`);
  return login.json();
}

async function createListing(ctx, token) {
  const resp = await ctx.post('/v1/listings', {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      name: 'E2E Halal Grill',
      address: '123 Test Street',
      city: 'Toronto',
      province: 'ON',
      postal: 'M5V 1A1',
      country: 'CA',
      lat: 43.6426,
      lng: -79.3871,
      cuisine: 'mediterranean'
    }
  });
  check(resp.ok(), `create listing (${resp.status()})`);
  return (await resp.json()).id;
}

async function loginInBrowser(page, email) {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
  // SvelteKit navigates client-side; do not make the click wait on a load event.
  await page.getByRole('button', { name: /log in/i }).click({ noWaitAfter: true });
  try {
    await page.waitForFunction(() => location.pathname === '/', null, { timeout: 20000 });
  } catch (error) {
    console.error('login did not navigate; url=', page.url());
    console.error('body:', (await page.locator('body').innerText()).slice(0, 600));
    await page.screenshot({ path: path.join(SHOTS, 'debug-login.png') });
    throw error;
  }
}

async function main() {
  const api = await request.newContext({ baseURL: API });
  const owner = await signupAndLogin(api, OWNER_EMAIL);
  const listingId = await createListing(api, owner.accessToken);
  console.log(`listing: ${listingId}`);

  // Serve the app on `localhost` and map it to the dev-server container:
  // localhost is a trustworthy origin, so the app's HSTS + CSP
  // `upgrade-insecure-requests` do not force https and the SvelteKit client
  // actually hydrates. A bare container IP is not trustworthy and breaks it.
  const args = ['--no-sandbox'];
  if (process.env.HOST_MAP) args.push('--host-resolver-rules=MAP localhost ' + process.env.HOST_MAP);
  const browser = await chromium.launch({ args });
  const errors = [];
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  context.on('page', (p) => p.on('pageerror', (e) => errors.push(String(e))));

  // ---- owner: empty state + control --------------------------------------
  const page = await context.newPage();
  await loginInBrowser(page, OWNER_EMAIL);
  await page.goto(`${BASE}/restaurants/${listingId}`, { waitUntil: 'networkidle' });

  const addButton = page.getByRole('button', { name: /add a photo/i });
  try {
    await addButton.waitFor({ timeout: 15000 });
  } catch (error) {
    console.error('no control; url=', page.url());
    console.error('body:', (await page.locator('body').innerText()).slice(0, 500));
    await page.screenshot({ path: path.join(SHOTS, 'debug-detail.png') });
    const diag = await page.evaluate(async (id) => {
      const ref = await fetch('/v1/auth/refresh', { method: 'POST', credentials: 'include' });
      const refBody = await ref.text();
      return { refreshStatus: ref.status, refBody: refBody.slice(0, 200), hint: localStorage.getItem('hff.session.v1') };
    }, listingId);
    console.error('diag', JSON.stringify(diag));
    console.error('cookies', JSON.stringify(await context.cookies()));
    throw error;
  }
  check(await page.getByText('No photo yet').isVisible(), 'empty state is labelled');
  await page.screenshot({ path: path.join(SHOTS, '01-owner-empty-state.png') });

  // ---- the dialog offers both sources ------------------------------------
  await addButton.click();
  const dialog = page.locator('dialog[open]');
  await dialog.waitFor();
  check(await dialog.getByRole('button', { name: 'Choose a photo' }).isVisible(), 'choose-file action');
  check(await dialog.getByRole('button', { name: 'Take a photo' }).isVisible(), 'camera action');
  await page.screenshot({ path: path.join(SHOTS, '02-upload-dialog.png') });

  // ---- upload -------------------------------------------------------------
  await dialog.locator('input[type="file"]').first().setInputFiles(FIXTURE);
  await page.getByRole('button', { name: /replace photo/i }).waitFor({ timeout: 20000 });
  const afterUpload = await page.locator('img[alt="E2E Halal Grill"]').first().getAttribute('src');
  check(!!afterUpload && afterUpload.includes('&v='), 'uploaded image URL carries a version');
  await page.screenshot({ path: path.join(SHOTS, '03-owner-after-upload.png') });

  // ---- AC-4: the photo survives a reload ---------------------------------
  await page.reload({ waitUntil: 'networkidle' });
  await page.locator('img[alt="E2E Halal Grill"]').first().waitFor({ timeout: 15000 });
  const afterReload = await page.locator('img[alt="E2E Halal Grill"]').first().getAttribute('src');
  check(afterReload === afterUpload, 'the same versioned URL is served after a reload');
  await page.screenshot({ path: path.join(SHOTS, '04-after-reload.png') });

  // ---- admin: sees the control on a listing they do not own --------------
  const adminContext = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const adminPage = await adminContext.newPage();
  await loginInBrowser(adminPage, ADMIN_EMAIL);
  await adminPage.goto(`${BASE}/restaurants/${listingId}`, { waitUntil: 'networkidle' });

  const replaceButton = adminPage.getByRole('button', { name: /replace photo/i });
  await replaceButton.waitFor({ timeout: 15000 });
  check(true, 'an admin sees Replace on a listing they do not own');
  await adminPage.screenshot({ path: path.join(SHOTS, '05-admin-foreign-listing.png') });

  // ---- admin replace shows the disclosure before writing -----------------
  await replaceButton.click();
  const adminDialog = adminPage.locator('dialog[open]');
  await adminDialog.waitFor();
  check(
    await adminDialog.getByText(/previous one cannot be restored/i).isVisible(),
    'replace discloses that the old photo is destroyed'
  );
  await adminPage.screenshot({ path: path.join(SHOTS, '06-admin-replace-disclosure.png') });

  // ---- non-owner: no control ---------------------------------------------
  const strangerEmail = `e2e-stranger-${Date.now()}@example.test`;
  const stranger = await signupAndLogin(api, strangerEmail);
  check(!!stranger.accessToken, 'stranger account created (setup only)');

  const strangerContext = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const strangerPage = await strangerContext.newPage();
  await loginInBrowser(strangerPage, strangerEmail);
  await strangerPage.goto(`${BASE}/restaurants/${listingId}`, { waitUntil: 'networkidle' });
  await strangerPage.waitForTimeout(1500);
  check(
    (await strangerPage.getByRole('button', { name: /add a photo|replace photo/i }).count()) === 0,
    'a signed-in non-owner is offered no photo control'
  );
  await strangerPage.screenshot({ path: path.join(SHOTS, '07-non-owner-no-control.png') });

  check(errors.length === 0, `no uncaught browser errors (${errors.join(' | ') || 'none'})`);
  await browser.close();
  await api.dispose();
  console.log('PASS');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
