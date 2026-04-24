# SaleADS Mi Negocio Full Workflow Test

This Playwright test implements the workflow named `saleads_mi_negocio_full_test`.

## Goals covered

- URL-agnostic execution (works in dev/staging/prod by configuration).
- Google login first, then complete all remaining Mi Negocio steps.
- Wait after each click/action before validating.
- Text-first selectors for better resilience.
- Handle legal links that may open same tab or a new tab.
- Capture screenshots at important checkpoints.
- Emit a final PASS/FAIL report for all requested fields.

## Setup

1. Install dependencies:

```bash
npm install
npm run playwright:install
```

2. Set required environment variable:

```bash
export SALEADS_BASE_URL="https://<current-environment-login-page>"
```

Optional:

```bash
export SALEADS_GOOGLE_ACCOUNT="juanlucasbarbiergarzon@gmail.com"
```

## Run

Headless:

```bash
npm run test:saleads
```

Headed:

```bash
npm run test:saleads:headed
```

## Artifacts

- Screenshots:
  - `test-results/saleads-mi-negocio/screenshots/<checkpoint>.png`
- Final report JSON:
  - `test-results/saleads-mi-negocio/final-report.json`
- Playwright HTML report:
  - `playwright-report/`
