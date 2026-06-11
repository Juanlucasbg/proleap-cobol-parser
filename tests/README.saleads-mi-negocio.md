# SaleADS Mi Negocio E2E workflow

This Playwright spec validates the full **Mi Negocio** flow requested in `saleads_mi_negocio_full_test`.

## Run

```bash
npm run test:saleads-mi-negocio
```

Optional (recommended for environment-agnostic execution):

```bash
SALEADS_START_URL="https://<current-environment-login>" npm run test:saleads-mi-negocio
```

## What it validates

1. Login with Google and dashboard/sidebar visibility.
2. Sidebar expansion for `Negocio` -> `Mi Negocio`.
3. `Agregar Negocio` modal fields/buttons.
4. `Administrar Negocios` page sections.
5. `Información General`.
6. `Detalles de la Cuenta`.
7. `Tus Negocios`.
8. `Términos y Condiciones` (same tab or popup tab).
9. `Política de Privacidad` (same tab or popup tab).
10. Final PASS/FAIL JSON report.

## Evidence artifacts

- Checkpoint screenshots:
  - `artifacts/saleads_mi_negocio_full_test/screenshots/*.png`
- Final report:
  - `artifacts/saleads_mi_negocio_full_test/final-report.json`
