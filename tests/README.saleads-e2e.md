# SaleADS Mi Negocio E2E

This repository now includes a Playwright E2E test named:

- `tests/saleads_mi_negocio_full_test.spec.ts`

## Goal covered

Validates the complete Mi Negocio workflow:

1. Login with Google.
2. Expand **Negocio > Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** (same tab or new tab).
9. Validate **Política de Privacidad** (same tab or new tab).
10. Produce a PASS/FAIL final report.

## Environment compatibility

No domain is hardcoded.

Set one of:

- `SALEADS_LOGIN_URL`
- `BASE_URL`

Example:

```bash
SALEADS_LOGIN_URL="https://<your-environment>/login" npm run test:e2e:saleads-mi-negocio
```

## Evidence generated

- Screenshots at key checkpoints (dashboard, expanded menu, modal, account page, legal pages).
- JSON final report attached to Playwright test results.
