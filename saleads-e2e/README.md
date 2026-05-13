# SaleADS Mi Negocio Full Workflow Test

This Playwright script automates the full workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google.
2. Open and validate `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate account sections and legal links.
6. Produce a PASS/FAIL final report.

## Why this is environment-agnostic

- No SaleADS domain is hardcoded.
- The script works with:
  - an already-open browser tab via `PLAYWRIGHT_WS_ENDPOINT`, or
  - a runtime start URL via `SALEADS_START_URL`.
- Selectors prefer visible text (`Mi Negocio`, `Agregar Negocio`, etc.).

## Usage

From this directory:

```bash
npm install
```

Then run one of:

```bash
# Option 1: open directly at runtime (any env URL)
SALEADS_START_URL="https://<current-env>/login" npm run test:mi-negocio

# Option 2: connect to an existing browser session/tab
PLAYWRIGHT_WS_ENDPOINT="<ws-or-cdp-endpoint>" npm run test:mi-negocio
```

Optional:

```bash
# Default is true. Set false to watch the browser.
HEADLESS=false npm run test:mi-negocio
```

## Output

Artifacts are written under:

```text
artifacts/<timestamp>/
```

Including:

- checkpoint screenshots,
- legal page screenshots,
- `final-report.json` with PASS/FAIL per requested step.
