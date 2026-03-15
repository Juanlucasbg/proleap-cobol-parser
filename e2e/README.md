# SaleADS Mi Negocio Full Workflow Test

This script automates the full workflow requested in `saleads_mi_negocio_full_test`:

- Google login (including account selection when available)
- Sidebar navigation to **Negocio > Mi Negocio**
- **Agregar Negocio** modal validations
- **Administrar Negocios** validations
- Legal links (**Términos y Condiciones**, **Política de Privacidad**) including new-tab handling
- Evidence capture (screenshots + final JSON report with PASS/FAIL per step)

## Run

```bash
npm run saleads:mi-negocio
```

### Headed mode

```bash
npm run saleads:mi-negocio:headed
```

## Configuration

The script is environment-agnostic and does not hardcode any domain.

Set one of these:

1. `PW_CDP_URL` (recommended): connect to an already open browser/session that is on the SaleADS login page.
2. `SALEADS_LOGIN_URL`: launch a new browser and navigate to the login URL of the target environment.

Optional:

- `HEADLESS=false` to run non-headless.
- `SALEADS_EVIDENCE_DIR=/path/to/output` to override evidence output directory.

## Evidence output

By default, files are written to:

`artifacts/saleads-mi-negocio/`

- `report.json`: final report including PASS/FAIL by field and legal final URLs.
- `screenshots/*.png`: checkpoint screenshots.
