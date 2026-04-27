# SaleADS Mi Negocio Full Workflow Test

This package contains a Playwright end-to-end test for:

- Login with Google
- Mi Negocio navigation and modal checks
- Administrar Negocios section validations
- Legal links (same tab or new tab)
- Checkpoint screenshots and final PASS/FAIL report

## Test file

- `tests/saleads-mi-negocio-full-workflow.spec.ts`

## Environment compatibility

The test is environment-agnostic:

- It does **not** hardcode any SaleADS URL.
- If a browser context already starts on the SaleADS login page, the test continues there.
- If the page starts at `about:blank`, set `SALEADS_URL` to the current environment login URL.

## Run

Install dependencies:

```bash
npm install
```

Run in headless mode:

```bash
SALEADS_URL="https://<your-saleads-login-url>" npm test
```

Run in headed mode:

```bash
SALEADS_URL="https://<your-saleads-login-url>" npm run test:headed
```

List tests only:

```bash
npm run test:list
```

## Evidence output

Playwright test artifacts are stored under `test-results/`:

- Checkpoint screenshots (dashboard, menu, modal, account, legal pages)
- `final-report.json` with PASS/FAIL per required field
- Legal final URLs for Terms and Privacy
