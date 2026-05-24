# SaleADS Mi Negocio Full Workflow Test

This test automates the full workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google
2. Open and validate **Mi Negocio** menu
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate **Información General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Términos y Condiciones** (same tab or new tab)
9. Validate **Política de Privacidad** (same tab or new tab)
10. Emit final PASS/FAIL report by step

## Environment assumptions

- The script is environment-agnostic and does **not** hardcode a SaleADS URL.
- It assumes the browser is already on the SaleADS login page when execution starts.
- If Google account selector appears, it will pick:
  - `juanlucasbarbiergarzon@gmail.com`

## Setup

```bash
cd /workspace/qa/saleads-mi-negocio
npm install
npm run install:browsers
```

## Run

```bash
npm test
```

Or headed mode:

```bash
npm run test:headed
```

## Evidence generated

Playwright stores evidence under `test-results/` and `playwright-report/`.

The workflow captures explicit checkpoints:

- `01_dashboard_loaded.png`
- `02_mi_negocio_menu_expanded.png`
- `03_agregar_negocio_modal.png`
- `04_administrar_negocios_view.png`
- `08_terminos_y_condiciones.png`
- `09_politica_de_privacidad.png`

It also attaches a JSON artifact named:

- `saleads-mi-negocio-final-report`

Containing:

- PASS/FAIL for each requested report field
- Captured legal page final URLs
- Failure details (if any)
