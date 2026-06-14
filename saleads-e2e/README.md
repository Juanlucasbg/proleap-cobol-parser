# SaleADS - Mi Negocio Full Workflow Test

This folder contains a standalone Playwright automation named:

- `saleads_mi_negocio_full_test`

It validates the complete flow requested for:

1. Login with Google (including optional account chooser)
2. `Negocio` -> `Mi Negocio` menu expansion
3. `Agregar Negocio` modal validation
4. `Administrar Negocios` account page validation
5. `Información General` validation
6. `Detalles de la Cuenta` validation
7. `Tus Negocios` validation
8. `Términos y Condiciones` validation (same tab or new tab)
9. `Política de Privacidad` validation (same tab or new tab)
10. Final PASS/FAIL report output

## Why it works in any environment

- No hardcoded domain is used.
- The target environment URL is provided at runtime with `SALEADS_BASE_URL`.
- Selectors prioritize visible text and semantic roles.

## Run

From repository root:

```bash
cd saleads-e2e
npx playwright install
SALEADS_BASE_URL="https://<your-saleads-environment>" npm run test:mi-negocio
```

Optional headed mode:

```bash
SALEADS_BASE_URL="https://<your-saleads-environment>" npm run test:headed -- tests/saleads-mi-negocio-full.spec.ts
```

## Evidence artifacts

The test captures screenshots at checkpoints and writes a final JSON report:

- Dashboard loaded
- Mi Negocio menu expanded
- Crear Nuevo Negocio modal
- Administrar Negocios page (full screenshot)
- Términos y Condiciones page
- Política de Privacidad page
- `mi-negocio-final-report.json` with PASS/FAIL for all report fields and final legal URLs
