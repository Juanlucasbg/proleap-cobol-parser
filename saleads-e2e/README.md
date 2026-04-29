# SaleADS E2E - Mi Negocio Workflow

This folder contains the automated test:

- `saleads_mi_negocio_full_test`

It validates the complete Mi Negocio workflow after Google login and captures screenshots + per-step PASS/FAIL output.

## Environment-agnostic behavior

- The test does **not** hardcode a domain.
- It uses a runtime URL from environment variable `SALEADS_URL`.

## Environment variables

- `SALEADS_URL` (required): Login page URL for the current environment.
- `GOOGLE_ACCOUNT_EMAIL` (optional): Defaults to `juanlucasbarbiergarzon@gmail.com`.

## Commands

```bash
cd /workspace/saleads-e2e
npx playwright install chromium
SALEADS_URL="https://your-saleads-env.example/login" npm run test:mi-negocio
```

Headed run:

```bash
SALEADS_URL="https://your-saleads-env.example/login" npm run test:mi-negocio -- --headed
```

## Output artifacts

- Checkpoint screenshots and evidence files: `artifacts/`
- Final step report JSON: `artifacts/saleads-mi-negocio-final-report.json`
