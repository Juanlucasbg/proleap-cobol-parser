# SaleADS Mi Negocio Full Workflow Test

This folder contains an environment-agnostic Playwright test named:

- `saleads_mi_negocio_full_test`

## What it validates

1. Login with Google and dashboard/sidebar visibility.
2. Mi Negocio menu expansion and submenu options.
3. "Agregar Negocio" modal structure and controls.
4. "Administrar Negocios" page sections.
5. "Información General" content.
6. "Detalles de la Cuenta" content.
7. "Tus Negocios" content.
8. "Términos y Condiciones" navigation/content and final URL.
9. "Política de Privacidad" navigation/content and final URL.
10. Final PASS/FAIL report for each required field.

The test captures screenshots on the required checkpoints and supports legal links opening in either the same tab or a new tab.

## Environment rules covered

- No hardcoded domain.
- Optional `SALEADS_URL` / `BASE_URL` for launching when the page is `about:blank`.
- Uses visible text-first selectors (`getByRole`, `getByText`, labeled inputs).

## Run

```bash
cd automation/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
npm test
```

Optional:

```bash
SALEADS_URL="https://<current-environment-login-url>" npm test
HEADLESS=false npm run test:headed
```

## Evidence output

Playwright stores artifacts under:

- `test-results/**` (screenshots/traces/videos)
- `playwright-report/**` (HTML report)

The test also writes and attaches:

- `saleads-mi-negocio-final-report.json`
- `saleads-mi-negocio-final-report.txt`
