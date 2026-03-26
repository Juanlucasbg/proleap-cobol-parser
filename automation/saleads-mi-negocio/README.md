# SaleADS Mi Negocio Full Workflow Test

This folder contains an isolated Playwright automation for the workflow:

- `saleads_mi_negocio_full_test`

The test validates:

1. Login with Google (and optional Google account chooser selection).
2. Mi Negocio menu expansion.
3. Agregar Negocio modal content.
4. Administrar Negocios sections.
5. Informacion General section data.
6. Detalles de la Cuenta section data.
7. Tus Negocios section data.
8. Terminos y Condiciones legal page.
9. Politica de Privacidad legal page.
10. Final PASS/FAIL report by requested fields.

## Requirements

- Node.js 18+ (verified with Node 22 in this environment)
- npm

## Install

```bash
npm install
```

## Run

Use the environment login URL so the same script works across dev/staging/prod:

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-page>" npm test
```

Optional variables:

- `HEADLESS=false` to run with visible browser.
- `SALEADS_EXPECTED_USER_NAME="<expected user display name>"` to strengthen username validation.

## Artifacts

Each run writes evidence to:

```text
automation/saleads-mi-negocio/artifacts/<timestamp>/
```

Including:

- checkpoint screenshots
- `final-report.json`
- `final-report.md`

The process exits with non-zero code if any requested report field is `FAIL`.
