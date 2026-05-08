# SaleADS Mi Negocio Full Workflow Test

This Playwright suite validates the complete **Mi Negocio** workflow with a Google login entry flow and legal page checks.

## What it validates

The spec `saleads_mi_negocio_full_test.spec.ts` covers:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal content
4. Administrar Negocios view sections
5. Informacion General data
6. Detalles de la Cuenta data
7. Tus Negocios data
8. Terminos y Condiciones legal page + final URL
9. Politica de Privacidad legal page + final URL
10. Final PASS/FAIL report output

It captures screenshots at the key checkpoints requested in the workflow.

## Environment-agnostic configuration

Set one of these environment variables to your current SaleADS environment login page:

- `SALEADS_URL`
- `SALEADS_LOGIN_URL`
- `BASE_URL`

No domain is hardcoded in the test.

## Install

```bash
cd /workspace/e2e/saleads-mi-negocio
npm install
npm run install:browsers
```

## Run

Headless:

```bash
SALEADS_URL="https://your-saleads-env.example/login" npm run test:mi-negocio
```

Headed:

```bash
SALEADS_URL="https://your-saleads-env.example/login" npm run test:mi-negocio:headed
```

## Evidence and report outputs

Generated under:

- `artifacts/saleads_mi_negocio_<timestamp>/`

Including:

- checkpoint screenshots (`01_*.png` ... `06_*.png`)
- `final_report.json` with PASS/FAIL per required report field

