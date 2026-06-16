# SaleADS Mi Negocio full workflow test

This repository now includes a standalone Playwright E2E workflow test:

- `tests/saleads_mi_negocio_full_test.spec.ts`

## Run

1. Install dependencies:

```bash
npm install
```

2. Install Playwright browsers (first run only):

```bash
npx playwright install
```

3. Run the workflow test:

```bash
npm run test:ui -- tests/saleads_mi_negocio_full_test.spec.ts
```

## Environment handling

The test does **not** hardcode a domain. It supports either:

- Starting directly on a SaleADS login page, or
- Providing a URL via `SALEADS_BASE_URL` (or `BASE_URL`) when the browser starts on `about:blank`.

Example:

```bash
SALEADS_BASE_URL="https://your-saleads-environment.example" npm run test:ui -- tests/saleads_mi_negocio_full_test.spec.ts
```

## Evidence and report

The test captures screenshots at critical checkpoints and writes a final JSON report artifact with PASS/FAIL values for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
