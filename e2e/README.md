# SaleADS Mi Negocio E2E

This folder contains a portable Playwright test that validates the complete SaleADS.ai "Mi Negocio" workflow:

- Login with Google
- Sidebar + Mi Negocio menu navigation
- "Agregar Negocio" modal validations
- "Administrar Negocios" page sections
- "Información General", "Detalles de la Cuenta", and "Tus Negocios" checks
- "Términos y Condiciones" and "Política de Privacidad" link handling (same tab or new tab)
- Checkpoint screenshots
- Final PASS/FAIL JSON report

## Files

- `saleads-mi-negocio.full.spec.js`: end-to-end workflow test
- `playwright.config.js`: local Playwright configuration
- `artifacts/` (generated at runtime): screenshots and final report

## Run

1. Install dependencies:

```bash
cd /workspace/e2e
npm install
npx playwright install
```

2. Run the test against any environment by passing the login URL through env vars:

```bash
SALEADS_LOGIN_URL="https://your-current-saleads-environment/login" npm test
```

You can also use `SALEADS_URL`.

## Output evidence

Generated under `e2e/artifacts/`:

- `01-dashboard-loaded.png`
- `02-mi-negocio-menu-expanded.png`
- `03-agregar-negocio-modal.png`
- `04-administrar-negocios-view.png`
- `05-terminos-y-condiciones.png`
- `06-politica-de-privacidad.png`
- `saleads-mi-negocio-final-report.json`

## Notes

- No domain is hardcoded.
- Selectors prioritize visible text to work across environments.
- Legal links are validated whether they open in the same page or a new tab.
