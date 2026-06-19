# SaleADS Mi Negocio full workflow test

This Playwright suite validates the full `saleads_mi_negocio_full_test` flow:

1. Login with Google.
2. Open and validate `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Validate `Administrar Negocios` sections.
5. Validate legal links, including popup/new-tab handling.
6. Produce a PASS/FAIL final report with evidence.

## Environment-agnostic design

- No domain is hardcoded.
- The test prefers visible text selectors.
- You can run against any SaleADS environment by setting:

```bash
export SALEADS_LOGIN_URL="https://<your-saleads-environment>/login"
```

If your runner opens the browser already on the login page, `SALEADS_LOGIN_URL` can be omitted.

## Install and run

```bash
cd qa/saleads
npm install
npm run install:browsers
SALEADS_LOGIN_URL="https://<env>/login" npm test
```

Optional headed mode:

```bash
SALEADS_LOGIN_URL="https://<env>/login" npm run test:headed
```

## Output artifacts

For each run, artifacts are saved under:

`qa/saleads/artifacts/<timestamp>/`

Including:

- Checkpoint screenshots
- `final-report.json` with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
