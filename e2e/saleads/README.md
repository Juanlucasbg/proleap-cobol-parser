# SaleADS Mi Negocio E2E

Playwright end-to-end test that validates:

- Login with Google
- Mi Negocio menu expansion
- Agregar Negocio modal
- Administrar Negocios account page sections
- Legal pages (Términos y Condiciones / Política de Privacidad)
- Final PASS/FAIL report by validation area

## Requirements

- Node.js 18+ (22+ recommended)
- Playwright browser binaries installed

## Setup

```bash
cd e2e/saleads
npm install
npx playwright install
```

## Run

The test does not hardcode any domain. Provide the login URL for the active environment:

```bash
SALEADS_LOGIN_URL="https://<current-saleads-environment>/login" npm test
```

Optional:

- `HEADLESS=false` to watch execution in headed mode.
- `BASE_URL` can be used instead of `SALEADS_LOGIN_URL`.

## Evidence and report outputs

Generated under `e2e/saleads/artifacts/`:

- `checkpoints/*.png` (checkpoint screenshots)
- `saleads_mi_negocio_final_report.json` (final PASS/FAIL report)
- `playwright-results.json` (Playwright JSON result)
