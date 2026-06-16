# SaleADS Mi Negocio E2E

This folder contains an end-to-end workflow test for the SaleADS.ai **Mi Negocio** module.

## Test included

- `saleads-mi-negocio.spec.js`

## Environment configuration

The test is environment-agnostic and does not hardcode a specific domain.
Set one of these environment variables before running:

- `SALEADS_URL` (preferred)
- `SALEADS_BASE_URL`
- `BASE_URL`
- `APP_URL`

Example:

```bash
SALEADS_URL="https://<your-saleads-environment>" npm run e2e:saleads-mi-negocio
```

## Run commands

- Headless:

```bash
npm run e2e:saleads-mi-negocio
```

- Headed:

```bash
npm run e2e:saleads-mi-negocio:headed
```

## Evidence artifacts

The test stores screenshots and a final validation report at:

- `e2e-artifacts/saleads-mi-negocio/`

It includes:

- Dashboard screenshot after login
- Mi Negocio expanded menu screenshot
- Agregar Negocio modal screenshot
- Administrar Negocios page full screenshot
- Términos y Condiciones screenshot + final URL in JSON report
- Política de Privacidad screenshot + final URL in JSON report
- `final-report.json` with PASS/FAIL by requested validation field
