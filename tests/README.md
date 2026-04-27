# SaleADS E2E tests

This folder contains end-to-end tests for SaleADS workflows using Playwright.

## Test included

- `saleads_mi_negocio_full_test.spec.js`

## How this test is environment-agnostic

The test does **not** hardcode any environment URL. It assumes the browser context
is already on the SaleADS login page, matching the requested environment (dev,
staging, or production). This aligns with the scenario where login starts from
the current environment's login screen.

## Running locally

1. Install dependencies:

   ```bash
   npm install
   ```

2. Run headed (recommended for Google login interaction):

   ```bash
   npm run test:e2e:saleads:headed
   ```

3. Or run default mode:

   ```bash
   npm run test:e2e:saleads
   ```

## Optional environment variables

- `SALEADS_GOOGLE_ACCOUNT_EMAIL`  
  Defaults to: `juanlucasbarbiergarzon@gmail.com`

## Evidence and reporting

- Screenshots are captured at required checkpoints and attached to test output.
- A final PASS/FAIL summary is printed in the test output for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Informacion General
  - Detalles de la Cuenta
  - Tus Negocios
  - Terminos y Condiciones
  - Politica de Privacidad
