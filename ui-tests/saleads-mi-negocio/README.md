# SaleADS Mi Negocio full workflow test

This Playwright test automates the full `saleads_mi_negocio_full_test` flow:

1. Login with Google.
2. Open `Negocio` -> `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate:
   - `Información General`
   - `Detalles de la Cuenta`
   - `Tus Negocios`
6. Validate legal links:
   - `Términos y Condiciones`
   - `Política de Privacidad`
7. Generate a PASS/FAIL final report.

The test uses visible text selectors and does not hardcode any SaleADS domain.

## Requirements

- Node.js 18+ (validated on Node 22)
- npm
- Playwright Chromium browser

## Install

```bash
npm install
npm run install:browsers
```

## Run

From this folder:

```bash
npm run test:mi-negocio
```

Optional environment variables:

- `SALEADS_LOGIN_URL` (recommended): login URL for the target environment (dev/staging/prod).
- `SALEADS_GOOGLE_ACCOUNT` (optional, default: `juanlucasbarbiergarzon@gmail.com`).
- `SALEADS_HEADLESS` (`false` for headed mode).
- `SALEADS_BROWSER_CHANNEL` (optional, for example `chrome`).

### Example

```bash
SALEADS_LOGIN_URL="https://your-env/login" npm run test:mi-negocio
```

## Evidence and report outputs

- Checkpoint screenshots are attached to Playwright results.
- HTML report is generated in `playwright-report/`.
- Final JSON report is written to:

```text
reports/saleads-mi-negocio-final-report.json
```
