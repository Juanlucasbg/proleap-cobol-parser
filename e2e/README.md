# SaleADS Mi Negocio E2E

This folder contains a Playwright end-to-end workflow test for:

- Google login
- Mi Negocio menu flow
- Agregar Negocio modal validation
- Administrar Negocios validations
- Legal pages validation (including new-tab handling)

## Prerequisites

1. Node.js 18+ installed.
2. Install dependencies:

```bash
cd e2e
npm install
```

3. Install Playwright browser(s):

```bash
npx playwright install chromium
```

## Run

Set the environment URL dynamically (no hardcoded domain):

```bash
cd e2e
SALEADS_URL="https://<current-saleads-environment>/login" npm run test:saleads-mi-negocio
```

## Notes

- The test intentionally does not hardcode any SaleADS domain.
- It uses visible text selectors whenever possible.
- It waits for UI load after each click.
- If legal links open a new tab, it validates content, captures evidence, closes the tab, and returns to the app tab.

## Evidence and report

After execution, Playwright stores results under:

- `e2e/test-results/` (screenshots, traces)
- `e2e/playwright-report/` (HTML report)

The test also generates a JSON final report with PASS/FAIL by requested fields:

- `saleads-mi-negocio-final-report.json` (inside the specific test output directory in `test-results`)
