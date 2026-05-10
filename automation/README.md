## SaleADS Mi Negocio full workflow test

This directory contains an environment-agnostic UI automation script:

- `saleads_mi_negocio_full_test.py`

### What it validates

The script executes the complete workflow requested for:

1. Login with Google.
2. Open `Mi Negocio` menu and validate submenu items.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios` and validate sections.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (supports same-tab or new-tab behavior).
9. Validate `Política de Privacidad` (supports same-tab or new-tab behavior).
10. Generate final PASS/FAIL report per step.

### Dependencies

Install latest Playwright:

```bash
python3 -m pip install playwright
python3 -m playwright install chromium
```

### Configuration

Do not hardcode URLs. Use one of:

- `SALEADS_LOGIN_URL` to open the login page directly.
- `SALEADS_CDP_URL` to attach to an already opened Chromium browser/tab.

Optional variables:

- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_TIMEOUT_MS` (default: `15000`)
- `SALEADS_HEADLESS` (`true|false`, default: `false`)

### Run

```bash
python3 automation/saleads_mi_negocio_full_test.py
```

### Artifacts

Each execution writes screenshots and the final JSON report under:

`artifacts/saleads_mi_negocio_full_test/<UTC_RUN_ID>/`
