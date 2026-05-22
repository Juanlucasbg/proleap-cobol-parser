# SaleADS Mi Negocio E2E

Playwright suite for the full `saleads_mi_negocio_full_test` workflow:

1. Login with Google
2. Open `Mi Negocio` menu
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate:
   - Información General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Términos y Condiciones
   - Política de Privacidad
7. Produce screenshots and final PASS/FAIL report.

## Environment-agnostic execution

This suite does **not** hardcode SaleADS domains.  
Pass the login page URL for the target environment through an environment variable:

- `SALEADS_LOGIN_URL` (preferred)
- `LOGIN_URL` or `BASE_URL` (fallback)

Example:

```bash
cd saleads-e2e
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm test
```

## Install and run

```bash
cd saleads-e2e
npm install
npx playwright install chromium
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm run test:mi-negocio
```

## Evidence outputs

Each run creates:

- Checkpoint screenshots in `artifacts/run-<timestamp>/`
- `final-report.json` in that same folder with:
  - PASS/FAIL for each requested field
  - legal page final URLs

## Notes

- Selectors prioritize visible text (`getByRole`/`getByText`).
- After each click, the test waits for UI load states.
- Legal links support same-tab navigation and new-tab behavior.
