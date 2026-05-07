# SaleADS Mi Negocio Full Workflow E2E

This repository now includes a Playwright test named:

- `saleads_mi_negocio_full_test`

It automates the full SaleADS "Mi Negocio" workflow:

1. Login with Google.
2. Expand "Mi Negocio".
3. Validate "Agregar Negocio" modal.
4. Open "Administrar Negocios".
5. Validate "Información General".
6. Validate "Detalles de la Cuenta".
7. Validate "Tus Negocios".
8. Validate "Términos y Condiciones" including new-tab handling.
9. Validate "Política de Privacidad" including new-tab handling.
10. Emit a final PASS/FAIL JSON report with required fields.

## Environment agnostic behavior

- No hard-coded SaleADS domain is used.
- Provide the current environment login URL at runtime:

```bash
export SALEADS_LOGIN_URL="https://<current-saleads-environment>/login"
```

If `SALEADS_LOGIN_URL` is not set, the test expects the browser context to already be on the SaleADS login page.

## Run

```bash
npm run playwright:install
npm run test:saleads-mi-negocio
```

## Evidence output

Playwright stores screenshots and report artifacts in `test-results/` and `playwright-report/`.

Important checkpoints captured:

- Dashboard after login
- Expanded Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios full page
- Términos y Condiciones page
- Política de Privacidad page

The final JSON report includes PASS/FAIL by requested field, validation failures, and captured legal-page URLs.
