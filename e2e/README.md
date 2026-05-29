# SaleADS E2E: Mi Negocio Full Workflow

This folder contains a Playwright test named `saleads_mi_negocio_full_test` that automates:

1. Google login in SaleADS
2. Mi Negocio menu expansion
3. Agregar Negocio modal validation
4. Administrar Negocios page validation
5. Información General checks
6. Detalles de la Cuenta checks
7. Tus Negocios checks
8. Términos y Condiciones validation (same-tab or popup)
9. Política de Privacidad validation (same-tab or popup)
10. Final PASS/FAIL report generation

## Environment-agnostic behavior

- The test does **not** hardcode any SaleADS domain.
- Use `SALEADS_LOGIN_URL` to point to the current environment login page (dev/staging/prod).
- If no URL is provided, the test expects the browser context to already be on the SaleADS login page.

## Required/optional environment variables

- `SALEADS_LOGIN_URL` (recommended): Login URL for the target environment.
- `SALEADS_GOOGLE_ACCOUNT` (optional): Defaults to `juanlucasbarbiergarzon@gmail.com`.
- `HEADLESS` (optional): Set `false` to run headed.

## Run locally

```bash
cd e2e
npm install
npm run install:browsers
SALEADS_LOGIN_URL="https://<your-environment>/login" npm run test:mi-negocio
```

## Artifacts and evidence

For each run, evidence is written under:

- `e2e/artifacts/<timestamp>/screenshots/*.png`
- `e2e/artifacts/<timestamp>/saleads_mi_negocio_final_report.json`

The JSON report includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
