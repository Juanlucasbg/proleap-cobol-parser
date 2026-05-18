# SaleADS Mi Negocio full workflow test

Playwright E2E test that validates the complete workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google
2. Open `Negocio` -> `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Información General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Términos y Condiciones`
9. Validate `Política de Privacidad`
10. Produce PASS/FAIL report by required fields

## Why this works in any environment

- No hardcoded SaleADS domain is used.
- Start URL is injected at runtime:
  - `SALEADS_START_URL` (preferred)
  - `SALEADS_BASE_URL`
  - `BASE_URL`
- All UI interactions use visible text / roles with fallbacks.

## Run

```bash
cd /workspace/e2e/saleads-mi-negocio
npx playwright install --with-deps
SALEADS_START_URL="https://<your-saleads-login-page>" npm run test:mi-negocio
```

For headed execution:

```bash
SALEADS_START_URL="https://<your-saleads-login-page>" npm run test:mi-negocio:headed
```

## Evidence and report

- Checkpoint screenshots are saved in Playwright test output under:
  - `.../screenshots/01-dashboard-loaded.png`
  - `.../screenshots/02-mi-negocio-menu-expanded.png`
  - `.../screenshots/03-agregar-negocio-modal.png`
  - `.../screenshots/04-administrar-negocios-view.png`
  - `.../screenshots/05-terminos-y-condiciones.png`
  - `.../screenshots/06-politica-de-privacidad.png`
- Final JSON report is attached to the test output as:
  - `saleads-mi-negocio-final-report.json`
