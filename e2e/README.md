# SaleADS Mi Negocio workflow automation

This folder contains an isolated Playwright automation script for the workflow:

1. Login with Google
2. Open **Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate account sections
6. Validate legal links (**Términos y Condiciones**, **Política de Privacidad**)
7. Produce a final PASS/FAIL report

## Configuration

Set the login URL for the target SaleADS environment (dev/staging/production):

```bash
export SALEADS_LOGIN_URL="https://<environment-host>/login"
```

Optional:

```bash
export HEADLESS=false
```

## Install and run

```bash
cd e2e
npm install
npm run test:mi-negocio
```

## Output

Each run creates:

- `artifacts/saleads_mi_negocio_full_test_<timestamp>/final-report.json`
- Screenshots for key checkpoints in the same folder
