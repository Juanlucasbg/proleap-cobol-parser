# SaleADS - Mi Negocio full workflow (Playwright)

This suite validates the complete SaleADS "Mi Negocio" workflow end-to-end, including:

1. Google login (with optional account selection for `juanlucasbarbiergarzon@gmail.com`)
2. Left sidebar > `Negocio` > `Mi Negocio`
3. `Agregar Negocio` modal validation
4. `Administrar Negocios` sections validation
5. Account sections validation (`Información General`, `Detalles de la Cuenta`, `Tus Negocios`)
6. Legal links validation:
   - `Términos y Condiciones`
   - `Política de Privacidad`
7. Final PASS/FAIL report for all required checkpoints

## Environment-agnostic behavior

- No SaleADS domain is hardcoded.
- Set the login page URL at runtime:

```bash
export SALEADS_LOGIN_URL="https://<your-saleads-environment>/login"
```

If your execution harness opens the login page automatically, this variable can be omitted.

## Run

```bash
npm install
npm run install:browsers
npm test
```

For headed mode:

```bash
npm run test:headed
```

## Evidence and report output

- Checkpoint screenshots are saved in Playwright test output directories.
- Legal-document screenshots are captured when terms/privacy pages open.
- A `final-report` JSON attachment is emitted with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
