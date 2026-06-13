# SaleADS Mi Negocio Full Workflow E2E

This Playwright suite automates the workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google (and optional account picker selection).
2. Open and validate **Mi Negocio** menu.
3. Open and validate **Agregar Negocio** modal.
4. Open and validate **Administrar Negocios** view.
5. Validate **Informacion General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Terminos y Condiciones** (same tab or new tab).
9. Validate **Politica de Privacidad** (same tab or new tab).
10. Emit a final PASS/FAIL report per section.

## Environment compatibility

This suite is URL-agnostic:

- It **does not hardcode any SaleADS domain**.
- Provide the login URL at runtime with `SALEADS_URL`.
- If the browser is pre-positioned by your runner and `SALEADS_URL` is omitted, the test uses the already-open page.

## Install

```bash
cd qa/saleads-e2e
npm install
npx playwright install chromium
```

## Run

```bash
cd qa/saleads-e2e
SALEADS_URL="https://<current-env-login-url>" npm test
```

Optional headed mode:

```bash
SALEADS_URL="https://<current-env-login-url>" npm run test:headed
```

## Output evidence

The test captures screenshots at key checkpoints and writes a final artifact:

- Checkpoint screenshots:
  - `01-dashboard-loaded.png`
  - `02-mi-negocio-expanded.png`
  - `03-crear-negocio-modal.png`
  - `04-administrar-negocios-page.png`
  - `05-terminos-y-condiciones.png`
  - `06-politica-de-privacidad.png`
- Final structured result:
  - `final-report.json` (PASS/FAIL per requested field + legal URLs)

Artifacts are available in Playwright `test-results` output for each run.
