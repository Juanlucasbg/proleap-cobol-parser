# SaleADS - Mi Negocio E2E

This repository includes an end-to-end Playwright test for the workflow:

- Login with Google
- Open **Mi Negocio**
- Validate **Agregar Negocio** modal
- Open **Administrar Negocios**
- Validate account sections
- Validate **Términos y Condiciones**
- Validate **Política de Privacidad**

## Test file

- `tests/saleads_mi_negocio_full_test.spec.ts`

## Environment variables

- `SALEADS_LOGIN_URL` (recommended): login URL for the target environment (dev/staging/prod).
  - The test does not hardcode a domain.
  - If not set, the test expects the browser to already be on the login page.
- `HEADED=true` (optional): run headed mode.

## Run

```bash
npm run test:e2e -- tests/saleads_mi_negocio_full_test.spec.ts
```

or headed:

```bash
npm run test:e2e:headed -- tests/saleads_mi_negocio_full_test.spec.ts
```

## Evidence output

The test captures screenshots at important checkpoints and stores them under Playwright test output (`test-results/`).
