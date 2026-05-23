# SaleADS Mi Negocio Full Workflow Test

Playwright test for the workflow:

1. Login with Google
2. Navigate to `Negocio` -> `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open and validate `Administrar Negocios`
5. Validate account and legal sections
6. Validate `Términos y Condiciones` and `Política de Privacidad` (same tab or new tab)
7. Emit a final PASS/FAIL report for each requested validation group

## Why this works across environments

- It does **not** hardcode any SaleADS domain.
- The target environment is injected through one of these variables:
  - `SALEADS_BASE_URL` (preferred)
  - `SALEADS_URL`
  - `BASE_URL`
- Element targeting prioritizes visible text and role names.

## Run

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
SALEADS_BASE_URL="https://<your-saleads-environment>" npm test
```

To run in headed mode:

```bash
HEADED=true SALEADS_BASE_URL="https://<your-saleads-environment>" npm run test:headed
```

## Evidence generated

- Checkpoint screenshots attached to test artifacts:
  - dashboard loaded
  - Mi Negocio expanded
  - Agregar Negocio modal
  - Administrar Negocios page
  - Términos y Condiciones
  - Política de Privacidad
- `final-report.json` with PASS/FAIL by section and legal page final URLs.
