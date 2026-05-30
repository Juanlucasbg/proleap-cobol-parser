## SaleADS Mi Negocio end-to-end test

This suite adds the scenario `saleads_mi_negocio_full_test` for:

1. Google login
2. Mi Negocio navigation
3. Agregar Negocio modal validation
4. Administrar Negocios page validation
5. Legal links validation, including new-tab handling

### Run

```bash
npm run test:e2e:install
SALEADS_LOGIN_URL="https://<current-saleads-env>/login" npm run test:e2e -- e2e/saleads-mi-negocio-full.spec.js
```

Notes:

- The test does not hardcode any domain; it reads `SALEADS_LOGIN_URL` (or `SALEADS_BASE_URL` / `BASE_URL`).
- Checkpoint screenshots are saved under `e2e-artifacts/checkpoints/<run-id>/`.
- A final PASS/FAIL matrix is written as JSON under `e2e-artifacts/reports/`.
