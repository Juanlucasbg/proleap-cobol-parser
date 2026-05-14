# SaleADS Mi Negocio E2E Test

This folder contains the Playwright automation named:

- `saleads_mi_negocio_full_test`

## What it validates

1. Login with Google (including selecting `juanlucasbarbiergarzon@gmail.com` if account chooser appears)
2. Open and validate `Mi Negocio` sidebar menu
3. Open and validate `Agregar Negocio` modal
4. Open `Administrar Negocios` and validate account sections
5. Validate `Información General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Términos y Condiciones` (same tab or new tab)
9. Validate `Política de Privacidad` (same tab or new tab)
10. Produce a final PASS/FAIL report attachment and console summary

Screenshots are captured at key checkpoints and on failure.

## Environment support

No fixed domain is hardcoded.  
To run in any SaleADS environment, provide the login URL dynamically:

```bash
SALEADS_LOGIN_URL="https://<your-env>/login" npm test
```

If the browser is already at the SaleADS login page (for example in an interactive run), the test uses the current page.

## Run

Install dependencies:

```bash
npm install
```

Run headless:

```bash
npm test
```

Run headed:

```bash
npm run test:headed
```

Open HTML report:

```bash
npm run test:report
```
