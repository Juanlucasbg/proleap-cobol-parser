# SaleADS E2E - Mi Negocio workflow

This folder contains a Playwright test that validates the complete Mi Negocio workflow:

- Login with Google (or continue if session already authenticated)
- Open and validate Mi Negocio menu
- Validate Agregar Negocio modal
- Validate Administrar Negocios sections
- Validate legal links (Términos y Condiciones, Política de Privacidad), including popup/new-tab handling
- Generate screenshots at key checkpoints
- Produce a final PASS/FAIL JSON report

## Requirements

- Node.js 20+
- Chromium dependencies available in the environment

## Install

```bash
cd /workspace/e2e
npm install
npx playwright install --with-deps chromium
```

## Run

Use one of these options:

1. If the runner already opens the login page, configure `BASE_URL`/`SALEADS_BASE_URL` to that environment login URL.
2. Keep it environment-agnostic by setting the URL externally:

```bash
cd /workspace/e2e
SALEADS_BASE_URL="https://<current-environment-login-url>" npm test
```

Useful variants:

```bash
npm run test:headed
npm run test:report
```

## Output artifacts

Artifacts are written under Playwright's `test-results/` directory:

- `01_dashboard_loaded.png`
- `02_mi_negocio_menu_expanded.png`
- `03_agregar_negocio_modal.png`
- `04_administrar_negocios_full_page.png`
- `05_terminos_y_condiciones.png`
- `06_politica_de_privacidad.png`
- `saleads_mi_negocio_report.json`
