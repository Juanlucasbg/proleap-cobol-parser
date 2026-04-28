## SaleADS Mi Negocio workflow test

This directory contains an environment-agnostic end-to-end test for the SaleADS.ai **Mi Negocio** module workflow.

### Test script

- `saleads_mi_negocio_full_test.mjs`

### What it validates

1. Login with Google (and optional account picker handling for `juanlucasbarbiergarzon@gmail.com`)
2. Sidebar `Mi Negocio` submenu expansion
3. `Agregar Negocio` modal fields and actions
4. `Administrar Negocios` account view sections
5. `Informacion General` section
6. `Detalles de la Cuenta` section
7. `Tus Negocios` section
8. `Terminos y Condiciones` legal page (same tab or new tab)
9. `Politica de Privacidad` legal page (same tab or new tab)
10. Final PASS/FAIL report generation

### Environment requirements

The test does **not** hardcode any SaleADS domain. Provide the environment URL through one of:

- `SALEADS_LOGIN_URL` (preferred)
- `SALEADS_URL`
- `BASE_URL`

The URL should point to the login page in the target environment.

### Run

```bash
cd e2e
npm install
npm run test:mi-negocio
```

For headed mode:

```bash
HEADLESS=false npm run test:mi-negocio
```

### Artifacts

Artifacts are written under:

`artifacts/saleads_mi_negocio_full_test/<timestamp>/`

Including:

- checkpoint screenshots
- `report.json`
- `report.md`
