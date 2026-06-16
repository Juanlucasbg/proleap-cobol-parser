# SaleADS Mi Negocio full workflow E2E

This suite automates the workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google.
2. Open `Negocio > Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (same tab or new tab).
9. Validate `Política de Privacidad` (same tab or new tab).
10. Generate final PASS/FAIL report.

## Environment-agnostic behavior

- No environment-specific domain is hardcoded.
- Optionally pass the current environment login URL using `SALEADS_LOGIN_URL`.
- All key interactions use visible text selectors and role-based selectors.

## Install

```bash
npm install
npx playwright install
```

## Run

```bash
SALEADS_LOGIN_URL="https://<your-current-environment>/login" npm run test:e2e
```

If the browser session is already opened and controlled externally, you can omit `SALEADS_LOGIN_URL`.

## Evidence and report outputs

- Checkpoint screenshots: `test-results/saleads-mi-negocio-checkpoints/`
- JSON final report: `test-results/saleads-mi-negocio-report.json`
