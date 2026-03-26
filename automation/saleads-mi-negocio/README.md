# SaleADS Mi Negocio Full Workflow Test

This folder contains an isolated Playwright automation that validates:

- Google login flow (or already-authenticated session)
- Mi Negocio menu expansion
- Agregar Negocio modal
- Administrar Negocios sections
- Informacion General / Detalles de la Cuenta / Tus Negocios
- Terminos y Condiciones and Politica de Privacidad legal links

## Run

```bash
cd automation/saleads-mi-negocio
npm install
npx playwright install chromium
npm test
```

## Environment variables

- `SALEADS_LOGIN_URL` (optional): login page URL for the current environment.
- `SALEADS_CDP_URL` (optional): connect to an existing browser session over CDP.
- `SALEADS_STORAGE_STATE_PATH` (optional): Playwright storage state path.
- `HEADLESS` (optional): set to `false` to run headed.

## Output

Artifacts are generated in:

- `automation/saleads-mi-negocio/artifacts/*.png` (screenshots)
- `automation/saleads-mi-negocio/artifacts/*_report.json` (final report with PASS/FAIL per step)
