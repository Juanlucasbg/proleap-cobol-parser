# SaleADS Mi Negocio E2E

This folder contains the Playwright automation test:

- `saleads_mi_negocio_full_test.spec.ts`

## What it validates

The workflow covers:

1. Login with Google (including optional account selection for `juanlucasbarbiergarzon@gmail.com`)
2. Mi Negocio sidebar expansion
3. Agregar Negocio modal validation
4. Administrar Negocios page validation
5. Informacion General validation
6. Detalles de la Cuenta validation
7. Tus Negocios validation
8. Terminos y Condiciones validation (new tab or same tab)
9. Politica de Privacidad validation (new tab or same tab)
10. Final PASS/FAIL report in test output

The test captures screenshots at important checkpoints and prints legal URLs in the output.

## Environment variables

- `SALEADS_LOGIN_URL` (required unless your runner preloads the login page): login page URL for the current environment (dev/staging/prod).  
  No domain is hardcoded in the test.
- `SALEADS_EVIDENCE_DIR` (optional): custom screenshot output directory.
- `HEADLESS` (optional): set to `false` to run headed.

## Run

```bash
cd e2e
npm install
npm run test:saleads-mi-negocio
```
