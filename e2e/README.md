# SaleADS E2E - Mi Negocio Full Workflow

This folder contains a Playwright test that validates the full `Mi Negocio` workflow for SaleADS, including:

- Login with Google (and optional account selection)
- Sidebar navigation and `Mi Negocio` menu expansion
- `Agregar Negocio` modal validation
- `Administrar Negocios` account page validation
- `Informacion General`, `Detalles de la Cuenta`, and `Tus Negocios` checks
- `Terminos y Condiciones` and `Politica de Privacidad` legal-link validation
- Required screenshots at critical checkpoints
- Final PASS/FAIL report by section with captured legal-page URLs

## Test file

- `tests/saleads-mi-negocio-full-test.spec.ts`

## Environment behavior

The test is URL-agnostic by default:

- If `SALEADS_BASE_URL` is set, the test opens that URL.
- If `SALEADS_BASE_URL` is not set, the test expects the browser to already be on the SaleADS login page.

This allows use across dev/staging/production without hardcoding a domain.

## Run locally

From this `e2e` directory:

```bash
npm install
npx playwright install --with-deps chromium
```

Run headless:

```bash
SALEADS_BASE_URL="https://your-saleads-environment.example" npm run test:mi-negocio
```

Run headed:

```bash
SALEADS_BASE_URL="https://your-saleads-environment.example" HEADLESS=false npm run test:mi-negocio
```

## Artifacts

- Explicit checkpoint screenshots are saved under `artifacts/screenshots/`.
- On failures, Playwright also saves trace/video/screenshot based on config.

## Notes

- The test uses visible-text-centric selectors with accent-insensitive matching for Spanish labels.
- It waits for UI load after each click to match workflow requirements.
- For legal links, it supports same-tab navigation and new-tab popups, then returns to the app context.
