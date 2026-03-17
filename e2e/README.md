# SaleADS E2E Tests

This folder contains Playwright-based UI tests for SaleADS workflows.

## Implemented test

- `tests/saleads_mi_negocio_full_test.spec.ts`
  - Logs in with Google.
  - Validates the full **Mi Negocio** flow.
  - Handles legal links that open in same tab or new tab.
  - Captures screenshots at key checkpoints.
  - Generates a final PASS/FAIL report per requested step.

## Setup

1. Install dependencies:

```bash
npm install
```

2. Install Chromium:

```bash
npm run install:browsers
```

3. Configure environment variables:

```bash
cp .env.example .env
```

Set at least:

- `SALEADS_LOGIN_URL` (environment-specific login URL; test is domain-agnostic and does not hardcode any domain).
- `SALEADS_GOOGLE_ACCOUNT` (defaults to `juanlucasbarbiergarzon@gmail.com` if omitted).

## Run

Run only this scenario:

```bash
npm run test:saleads-mi-negocio
```

Run headed (debug):

```bash
npm run test:headed -- tests/saleads_mi_negocio_full_test.spec.ts
```

## Artifacts

- Screenshots are saved under Playwright test output.
- HTML report is generated in `playwright-report/`.
- Final JSON report attachment: `saleads_mi_negocio_final_report.json`.
