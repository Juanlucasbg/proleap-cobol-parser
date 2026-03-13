# SaleADS Mi Negocio full workflow test

This folder contains `saleads_mi_negocio_full_test`, an end-to-end Playwright script that validates the complete Mi Negocio workflow after Google login.

## What it validates

1. Login with Google and dashboard/sidebar visibility.
2. Mi Negocio menu expansion and submenu options.
3. Agregar Negocio modal (title, input, limit text, buttons).
4. Administrar Negocios page and required sections.
5. Informacion General checks.
6. Detalles de la Cuenta checks.
7. Tus Negocios checks.
8. Terminos y Condiciones page (same tab or new tab).
9. Politica de Privacidad page (same tab or new tab).

The script captures screenshots at required checkpoints and writes a final JSON report with PASS/FAIL by step.

## Usage

```bash
cd qa/saleads-mi-negocio
npm install
SALEADS_BASE_URL="https://<current-environment-login-url>" npm test
```

Optional:

- `HEADLESS=false` to run with visible browser.
- `SALEADS_TIMEOUT_MS=30000` to increase timeout.

## Output

Artifacts are written to:

```text
qa/saleads-mi-negocio/artifacts/<timestamp>/
```

Including:

- checkpoint screenshots
- `final_report.json`
