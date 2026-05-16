# SaleADS Mi Negocio Full Workflow (Playwright)

This suite validates the complete `Mi Negocio` flow (not only login), including:

- Google login entry point.
- Sidebar and `Mi Negocio` submenu checks.
- `Agregar Negocio` modal checks.
- `Administrar Negocios` account sections.
- Legal links (`Terminos y Condiciones` and `Politica de Privacidad`) including new-tab handling.
- Screenshot checkpoints and final PASS/FAIL report.

## Why it is environment-agnostic

No domain is hardcoded. The environment is injected at runtime:

- `SALEADS_LOGIN_URL` (recommended), or
- `SALEADS_URL`, or
- `BASE_URL`

Use any dev/staging/prod URL.

## Install

```bash
cd e2e
npm install
npm run install:browsers
```

## Run

```bash
cd e2e
SALEADS_LOGIN_URL="https://<your-saleads-environment>/login" npm test
```

Optional:

- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS=false` for headed mode
- `SALEADS_SCREENSHOT_DIR` to customize screenshot path

## Output artifacts

- Screenshots: `e2e/artifacts/screenshots/`
- HTML report: `e2e/playwright-report/`
- Attached JSON final report: `final-report.json` in Playwright test output
