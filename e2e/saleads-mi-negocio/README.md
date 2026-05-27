# SaleADS Mi Negocio full workflow test

This Playwright suite automates the full `saleads_mi_negocio_full_test` workflow:

1. Login with Google.
2. Open **Mi Negocio** menu.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** link (same tab or new tab).
9. Validate **Política de Privacidad** link (same tab or new tab).
10. Produce PASS/FAIL report artifact.

## Why this works across environments

- No hardcoded SaleADS domain.
- Uses `SALEADS_LOGIN_URL` (or `SALEADS_BASE_URL`) at runtime.
- Selectors prioritize visible text in Spanish.

## Run

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps
SALEADS_LOGIN_URL="https://<your-env>/login" npm test
```

## Output artifacts

- Checkpoint screenshots are saved in Playwright test output.
- `final-report.json` attachment includes:
  - PASS/FAIL by required report fields.
  - Final URL for Terms and Privacy pages.
