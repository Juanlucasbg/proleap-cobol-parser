# SaleADS Mi Negocio Full Workflow Test

This Playwright suite automates the full **Mi Negocio** flow for SaleADS:

1. Login with Google.
2. Open `Negocio > Mi Negocio`.
3. Validate the `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Terminos y Condiciones
   - Politica de Privacidad
7. Produce a final PASS/FAIL report by validation area.

## Environment-agnostic setup

No domain is hardcoded. Provide the environment login URL at runtime:

- `SALEADS_LOGIN_URL` (preferred), or
- `BASE_URL`

## Install

```bash
cd automation/saleads-mi-negocio
npm install
npm run install:browsers
```

## Run

Headless:

```bash
SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:mi-negocio
```

Headed:

```bash
SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:mi-negocio:headed
```

## Output artifacts

- Checkpoint screenshots in `artifacts/saleads-mi-negocio/`
- Playwright report in `playwright-report/`
- JSON final report attached by the test as `final-report.json`

The final report includes:

- PASS/FAIL per required field
- Captured legal-page URLs
- Evidence screenshot paths
- Any step-level errors
