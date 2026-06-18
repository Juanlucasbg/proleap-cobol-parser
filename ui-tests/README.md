# SaleADS.ai Mi Negocio end-to-end workflow

This folder contains an environment-agnostic Playwright test that validates the full **Mi Negocio** flow:

- Google login
- Sidebar and **Mi Negocio** expansion
- **Agregar Negocio** modal checks
- **Administrar Negocios** account view checks
- Legal links validation (**Términos y Condiciones** and **Política de Privacidad**)
- Screenshot evidence and final PASS/FAIL report

## Setup

```bash
cd ui-tests
npm install
npm run install:browsers
```

## Run

If your runtime already opens the login page, no URL is required.

If Playwright starts from a blank page, provide the environment login URL dynamically:

```bash
SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:saleads-mi-negocio
```

Or use:

```bash
SALEADS_BASE_URL="https://<current-env>" npm run test:saleads-mi-negocio
```

## Output

- Checkpoint screenshots are stored by Playwright in the test output.
- A JSON final report attachment includes PASS/FAIL for each required validation field.
- URLs for legal pages are captured as text attachments.
