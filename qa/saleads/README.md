# SaleADS Mi Negocio full workflow (Playwright)

This suite validates the complete workflow requested by `saleads_mi_negocio_full_test`:

1. Login with Google.
2. Open **Negocio > Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** (supports same-tab or new-tab).
9. Validate **Política de Privacidad** (supports same-tab or new-tab).
10. Produce final PASS/FAIL report for each required field.

## Environment-agnostic behavior

- The test does **not** hardcode any SaleADS domain.
- It uses the page already opened by the environment.
- If Playwright starts from `about:blank`, set one of:
  - `SALEADS_BASE_URL`
  - `BASE_URL`
  - `APP_URL`

## Install

```bash
cd qa/saleads
npm install
```

## Run

```bash
cd qa/saleads
npm test
```

Headed mode:

```bash
npm run test:headed
```

## Evidence generated

- Screenshots at critical checkpoints:
  - dashboard
  - expanded Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios full page
  - Términos y Condiciones
  - Política de Privacidad
- JSON report with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
