## SaleADS Mi Negocio Full Workflow Test

This workflow is implemented in:

- `automation/saleads_mi_negocio_full_test.mjs`

The script is environment-agnostic: it does not hardcode a domain and requires the target login URL at runtime.

## Prerequisites

- Node.js 18+
- Network access to the chosen SaleADS environment
- A Google session capable of selecting/signing in with:
  - `juanlucasbarbiergarzon@gmail.com` (default account in the script)

## Run

```bash
npx -y -p playwright node automation/saleads_mi_negocio_full_test.mjs \
  --url "https://<your-saleads-environment>/login"
```

Optional flags:

- `--headed` (default is headless)
- `--account-email "juanlucasbarbiergarzon@gmail.com"`
- `--out-dir "artifacts/saleads_mi_negocio_full_test"`
- `--slow-mo-ms 250`

Equivalent environment variables:

- `SALEADS_LOGIN_URL`
- `GOOGLE_ACCOUNT_EMAIL`
- `SALEADS_ARTIFACTS_DIR`
- `HEADLESS=true|false`
- `SLOW_MO_MS=250`

## Output

After execution, the script stores:

- JSON report:
  - `artifacts/saleads_mi_negocio_full_test/final_report.json`
- Checkpoint screenshots:
  - `artifacts/saleads_mi_negocio_full_test/screenshots/*.png`

The report includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Informacion General
- Detalles de la Cuenta
- Tus Negocios
- Terminos y Condiciones
- Politica de Privacidad
