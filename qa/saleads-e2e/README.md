# SaleADS Mi Negocio E2E

Playwright test that validates the full **Mi Negocio** workflow, including:

- Login with Google
- Sidebar menu expansion
- "Agregar Negocio" modal checks
- "Administrar Negocios" page sections
- Legal links ("Términos y Condiciones", "Política de Privacidad")
- Screenshot checkpoints and final PASS/FAIL JSON report

## Run

```bash
cd qa/saleads-e2e
npm install
SALEADS_START_URL="https://<your-saleads-environment>" npm test
```

Notes:

- The test does **not** hardcode any domain.
- Use any environment URL through `SALEADS_START_URL`, `SALEADS_URL`, or `BASE_URL`.
- Artifacts are generated under `qa/saleads-e2e/test-results/`.
