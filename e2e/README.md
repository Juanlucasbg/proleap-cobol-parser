# SaleADS Mi Negocio full workflow test

This folder contains the automated Playwright test:

- `tests/saleads_mi_negocio_full_test.spec.js`

## What it validates

The test automates the full flow requested in `saleads_mi_negocio_full_test`:

1. Login with Google
2. Open `Mi Negocio` in sidebar
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Información General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Términos y Condiciones` (including new tab handling)
9. Validate `Política de Privacidad` (including new tab handling)
10. Emit final PASS/FAIL report for each checkpoint

It uses visible text-first locators, explicit waits after each click, and captures screenshots at key checkpoints.

## Environment-agnostic usage

No domain is hardcoded. Set one of these environment variables:

- `SALEADS_URL`
- `SALEADS_LOGIN_URL`
- `BASE_URL`

Example:

```bash
cd e2e
npm run install:browsers
SALEADS_URL="https://your-current-saleads-env/login" npm test
```

## Output evidence

Artifacts are stored by Playwright under `test-results/` (screenshots, trace/video on failure, JSON attachment with final report).
