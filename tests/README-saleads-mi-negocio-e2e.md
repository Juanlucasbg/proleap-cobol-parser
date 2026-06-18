# SaleADS Mi Negocio Full Workflow E2E

This repository now includes a Playwright end-to-end test that validates the full "Mi Negocio" flow:

- Google login
- Sidebar and Mi Negocio menu behavior
- "Agregar Negocio" modal validation
- "Administrar Negocios" account sections validation
- Legal links validation (`Términos y Condiciones`, `Política de Privacidad`) including final URLs
- Screenshot evidence at key checkpoints
- Final PASS/FAIL report as JSON artifact

## Files

- `playwright.config.js`
- `tests/saleads-mi-negocio-full.spec.js`

## Environment variables

No domain is hardcoded. Provide one of:

- `SALEADS_LOGIN_URL` (full login URL), or
- `SALEADS_BASE_URL` plus optional `SALEADS_LOGIN_PATH` (default: `/`)

## Run

```bash
npm install
npx playwright install chromium
SALEADS_BASE_URL="https://<your-saleads-environment>" npm run test:e2e
```

Headed mode:

```bash
SALEADS_BASE_URL="https://<your-saleads-environment>" npm run test:e2e:headed
```

## Output evidence

Playwright stores artifacts under `test-results/`:

- Screenshots for required checkpoints
- Trace/video on failures
- `saleads-mi-negocio-report.json` with per-step PASS/FAIL and legal page URLs
