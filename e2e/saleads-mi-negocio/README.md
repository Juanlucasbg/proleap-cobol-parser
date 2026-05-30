# SaleADS Mi Negocio full workflow (Playwright)

This test automates the full workflow requested for SaleADS:

1. Login with Google.
2. Open `Negocio > Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate:
   - `Información General`
   - `Detalles de la Cuenta`
   - `Tus Negocios`
6. Validate legal links:
   - `Términos y Condiciones`
   - `Política de Privacidad`
7. Generate final PASS/FAIL report per requested section.

## Environment-agnostic usage

No domain is hardcoded. You can run in either mode:

- If browser/session already starts on the SaleADS login page, no URL variable is required.
- Or set the environment URL at runtime:

- `SALEADS_LOGIN_URL` (preferred), or
- `SALEADS_BASE_URL`

Google account defaults to `juanlucasbarbiergarzon@gmail.com`, and can be overridden with:

- `SALEADS_GOOGLE_EMAIL`

## Install and run

```bash
cd /workspace/e2e/saleads-mi-negocio
npm install
npx playwright install chromium
npm run test:saleads
```

Headed run (useful for interactive Google flows):

```bash
PW_HEADLESS=false npm run test:saleads:headed
```

## Artifacts

The test captures screenshots at important checkpoints and writes:

- Checkpoint screenshots in Playwright output directories.
- `final-report.json` with:
  - PASS/FAIL for each required report field
  - failure messages (if any)
  - final URLs for legal pages
