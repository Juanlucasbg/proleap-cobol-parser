## SaleADS Mi Negocio full workflow test

This folder contains an environment-agnostic E2E script for the `saleads_mi_negocio_full_test` flow.

### What it validates

The script runs the full workflow requested:

1. Login with Google (including account picker handling)
2. Open **Mi Negocio** menu and validate submenu entries
3. Validate **Agregar Negocio** modal and controls
4. Open **Administrar Negocios**
5. Validate **Información General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Términos y Condiciones** (same tab or new tab)
9. Validate **Política de Privacidad** (same tab or new tab)
10. Emit final PASS/FAIL report per validation area

Screenshots are captured at key checkpoints, and legal page URLs are included in the final report.

### Environment-agnostic behavior

- No SaleADS domain is hardcoded.
- You provide the current environment login URL at runtime.
- UI selection prefers visible text labels in Spanish/English variants where relevant.

### Setup

```bash
pip3 install -r e2e_saleads/requirements.txt
python3 -m playwright install chromium
python3 -m playwright install-deps chromium
```

### Run

```bash
python3 e2e_saleads/saleads_mi_negocio_full_test.py \
  --base-url "https://<current-saleads-environment>/login" \
  --account-email "juanlucasbarbiergarzon@gmail.com" \
  --headless
```

Optional environment variables:

- `SALEADS_START_URL`: login URL for current environment
- `SALEADS_GOOGLE_ACCOUNT`: Google account to select

Equivalent run:

```bash
SALEADS_START_URL="https://<current-saleads-environment>/login" \
SALEADS_GOOGLE_ACCOUNT="juanlucasbarbiergarzon@gmail.com" \
python3 e2e_saleads/saleads_mi_negocio_full_test.py --headless
```

### Artifacts

- Screenshots: `e2e_saleads/screenshots/*.png`
- Final report: `e2e_saleads/final_report.json`

