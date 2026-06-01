# SaleADS - Mi Negocio Full Workflow Test

Automated Playwright test for the full `Mi Negocio` workflow:

- Login with Google
- Open and validate `Mi Negocio` menu
- Validate `Agregar Negocio` modal
- Open and validate `Administrar Negocios`
- Validate:
  - Informacion General
  - Detalles de la Cuenta
  - Tus Negocios
- Validate legal pages:
  - Terminos y Condiciones
  - Politica de Privacidad
- Produce PASS/FAIL report by validation area

## 1) Install dependencies

```bash
cd qa/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
```

## 2) Configure target environment URL

The suite is environment-agnostic and does not hardcode any SaleADS domain.

Set one of:

- `SALEADS_LOGIN_URL`
- `SALEADS_BASE_URL`
- `BASE_URL`

Example:

```bash
export SALEADS_LOGIN_URL="https://your-current-saleads-environment/login"
```

## 3) Run the test

```bash
npm run test:saleads-mi-negocio
```

## 4) Artifacts

The run stores evidence in:

- `qa/saleads-mi-negocio/artifacts/screenshots/*.png`
- `qa/saleads-mi-negocio/artifacts/saleads_mi_negocio_full_test_report.json`

The JSON report includes:

- PASS/FAIL status for each required validation step
- Captured legal final URLs
- Screenshot references
- Step-level errors when failures happen
