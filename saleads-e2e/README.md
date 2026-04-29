# SaleADS E2E - Mi Negocio Workflow

This folder contains the automated test:

- `saleads_mi_negocio_full_test`

It validates the complete Mi Negocio workflow after Google login and captures screenshots + per-step PASS/FAIL output.

## Environment-agnostic behavior

- The test does **not** hardcode a domain.
- If `SALEADS_URL` is provided, it navigates there first.
- If `SALEADS_URL` is not provided, it assumes the browser is already on the current environment login page.

## Environment variables

- `SALEADS_URL` (optional): Login page URL for the current environment.
- `GOOGLE_ACCOUNT_EMAIL` (optional): Defaults to `juanlucasbarbiergarzon@gmail.com`.

## Commands

```bash
cd /workspace/saleads-e2e
npx playwright install chromium
npm run test:mi-negocio
# or explicitly:
SALEADS_URL="https://your-saleads-env.example/login" npm run test:mi-negocio
```

Headed run:

```bash
npm run test:mi-negocio -- --headed
```

## Output artifacts

- Checkpoint screenshots and evidence files: `artifacts/`
- Final step report JSON: `artifacts/saleads-mi-negocio-final-report.json`
