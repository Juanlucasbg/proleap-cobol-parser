## SaleADS Browser Automation Scripts

### `saleads_mi_negocio_full_test`

End-to-end browser automation for validating the **Mi Negocio** workflow after Google login across SaleADS environments (dev/staging/prod), without hardcoding any domain.

### Prerequisites

1. Install dependencies:

```bash
npm install
```

2. Install Playwright browser binaries (once per machine):

```bash
npx playwright install chromium
```

### Run

```bash
npm run saleads:mi-negocio:test
```

Required environment variable:

- `SALEADS_START_URL`: Login page URL for the current environment.

Optional environment variables:

- `GOOGLE_ACCOUNT_EMAIL`: Google account to select (default: `juanlucasbarbiergarzon@gmail.com`).
- `HEADLESS`: Set to `false` to run headed.
- `PW_DEFAULT_TIMEOUT_MS`: Override default Playwright timeout (default: `20000`).
- `ARTIFACTS_DIR`: Override output directory root (default: `artifacts`).

### Output Evidence

Each run writes to:

`artifacts/saleads_mi_negocio_full_test-<timestamp>/`

- `screenshots/`: checkpoint screenshots.
- `report.json`: detailed per-check report and step statuses.
- `result.json`: compact final summary.

The terminal output also prints the final summary with:

- PASS/FAIL by step:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
- Evidence paths
- Captured final legal URLs
