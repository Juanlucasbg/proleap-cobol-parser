# SaleADS Mi Negocio Full Workflow Test

This folder contains the Playwright E2E test `saleads_mi_negocio_full_test` for validating the full **Mi Negocio** workflow in any SaleADS.ai environment (dev/staging/prod).

## What this test validates

The test covers all required checkpoints:

1. Login with Google
2. Expand **Negocio → Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate **Información General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Términos y Condiciones** (including new-tab handling)
9. Validate **Política de Privacidad** (including new-tab handling)
10. Produce a final PASS/FAIL report per step

It also captures screenshots at important checkpoints and records final URLs for legal links.

## Usage

### 1) Install browser binaries

```bash
npm run playwright:install
```

### 2) Run test

Set an environment-specific login URL through an environment variable (the test does not hardcode any domain):

```bash
SALEADS_LOGIN_URL="https://<your-environment-login-url>" npm run test:saleads-mi-negocio
```

You may also use:

```bash
SALEADS_URL="https://<your-environment-login-url>" npm run test:saleads-mi-negocio
```

### 3) Optional headed execution

```bash
SALEADS_LOGIN_URL="https://<your-environment-login-url>" npm run test:saleads-mi-negocio:headed
```

## Output artifacts

Artifacts are saved under `test-results/` and include:

- Step screenshots
- Trace/video on failure
- JSON attachment with the final PASS/FAIL report and legal URLs
