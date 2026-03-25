# SaleADS E2E - Mi Negocio workflow

This folder contains the Playwright test for:

- `saleads_mi_negocio_full_test`

## Prerequisites

From repo root:

1. Install dependencies:

   ```bash
   npm install
   ```

2. Install Playwright browser:

   ```bash
   npx playwright install chromium
   ```

## Environment variables

Use one of these options:

- `SALEADS_LOGIN_URL` (preferred if you already know the login route), or
- `SALEADS_BASE_URL` (the test will try common login paths dynamically)

Optional:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)

Examples:

```bash
export SALEADS_LOGIN_URL="https://<your-env>/login"
export SALEADS_GOOGLE_ACCOUNT="juanlucasbarbiergarzon@gmail.com"
```

or

```bash
export SALEADS_BASE_URL="https://<your-env>"
```

## Run

Headless:

```bash
npm run saleads:test
```

Headed:

```bash
npm run saleads:test:headed
```

## Evidence

The test stores screenshots at important checkpoints in:

- `checkpoints/`

The final PASS/FAIL report is attached as `final-report` in Playwright test artifacts.
