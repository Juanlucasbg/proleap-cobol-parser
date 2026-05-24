# SaleADS E2E automation

This folder contains UI automation scripts for SaleADS workflows.

## Script: `saleads_mi_negocio_full_test`

Runs the full Mi Negocio workflow with:

- Google login step
- Sidebar / Mi Negocio menu validation
- Agregar Negocio modal validation
- Administrar Negocios page validations
- Información General / Detalles de la Cuenta / Tus Negocios checks
- Términos y Condiciones + Política de Privacidad checks (same tab or new tab)
- Screenshot evidence at key checkpoints
- Final PASS/FAIL report by required field

### Required environment variable

Use any SaleADS environment URL. The script does not hardcode a domain.

```bash
export SALEADS_LOGIN_URL="https://<your-environment>/login"
```

Alternative accepted vars:

- `SALEADS_BASE_URL`
- `SALEADS_URL`

### Optional environment variables

```bash
export GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com"
export HEADLESS="false"
export E2E_TIMEOUT_MS="45000"
```

### Run

```bash
npm run saleads:mi-negocio
```

### Output

Artifacts are generated under:

```text
artifacts/saleads_mi_negocio_full_test/<timestamp>/
```

Including:

- `report.json`
- `report.md`
- checkpoint screenshots (`*.png`)
