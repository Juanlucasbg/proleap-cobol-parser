# SaleADS - Mi Negocio Full Workflow (Playwright)

This folder contains an end-to-end test for the workflow:

- Login with Google
- Open and validate the **Mi Negocio** menu
- Validate **Agregar Negocio** modal
- Open **Administrar Negocios** and validate each section
- Validate legal links (**Términos y Condiciones** and **Política de Privacidad**)
- Produce a final PASS/FAIL report for each requested checkpoint

## Prerequisites

1. Install dependencies:

```bash
npm install
```

2. Install browsers:

```bash
npx playwright install
```

3. Provide the login page URL for the current environment:

```bash
export SALEADS_LOGIN_URL="https://<current-environment-login-url>"
```

Optional:

```bash
export SALEADS_GOOGLE_ACCOUNT="juanlucasbarbiergarzon@gmail.com"
export HEADLESS=false
```

## Run

```bash
npm run e2e:saleads-mi-negocio
```

## Evidence

Artifacts are stored in `test-results/`:

- Checkpoint screenshots
- Final legal URLs (attachments)
- Final PASS/FAIL report attachment
