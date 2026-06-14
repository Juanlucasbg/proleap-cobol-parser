# SaleADS Mi Negocio workflow E2E

This folder contains an environment-agnostic Playwright test for the full **Mi Negocio** workflow:

- Test name: `saleads_mi_negocio_full_test`
- File: `tests/saleads_mi_negocio_full_test.spec.ts`

## What it validates

1. Google login flow and dashboard/sidebar visibility.
2. Expansion of **Mi Negocio** menu.
3. **Agregar Negocio** modal fields and buttons.
4. **Administrar Negocios** view and core sections.
5. **Información General** content.
6. **Detalles de la Cuenta** content.
7. **Tus Negocios** content.
8. **Términos y Condiciones** legal page (same tab or popup).
9. **Política de Privacidad** legal page (same tab or popup).
10. Final PASS/FAIL report by validation area.

## Runtime configuration

Set these variables before running:

- `SALEADS_START_URL` (required): Login URL for the current environment (dev/staging/prod).
- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (optional): defaults to `juanlucasbarbiergarzon@gmail.com`.

The test does not hardcode any specific domain.

## Install and run

```bash
cd e2e
npm install
npx playwright install
SALEADS_START_URL="https://<current-env-login-url>" npm test
```

## Artifacts

- Checkpoint screenshots: `e2e/screenshots/*.png`
- Playwright artifacts/report: `e2e/test-results/`
- Final workflow report JSON: `e2e/test-results/saleads_mi_negocio_full_test-report.json`
