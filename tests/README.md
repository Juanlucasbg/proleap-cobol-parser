# SaleADS E2E workflow test

This folder contains an end-to-end Playwright test for the Mi Negocio workflow:

- `saleads_mi_negocio_full_test.spec.js`

## Run

1. Install dependencies:

```bash
npm install
```

2. Run the workflow test in headless mode:

```bash
SALEADS_URL="https://your-current-saleads-environment/login" npm run test:e2e -- --grep saleads_mi_negocio_full_test
```

3. Run headed (useful for Google sign-in flows):

```bash
SALEADS_URL="https://your-current-saleads-environment/login" npm run test:e2e:headed -- --grep saleads_mi_negocio_full_test
```

## Environment notes

- The test does not hardcode any SaleADS domain.
- Provide `SALEADS_URL` (or `SALEADS_BASE_URL` / `BASE_URL`) at runtime.
- If your runner preloads the browser on the login page, URL vars are optional.

## Evidence generated

The test captures screenshots at key checkpoints and attaches:

- dashboard loaded
- Mi Negocio menu expanded
- Agregar Negocio modal
- Administrar Negocios view
- Términos y Condiciones page
- Política de Privacidad page

It also writes a JSON PASS/FAIL summary per requested validation area.
