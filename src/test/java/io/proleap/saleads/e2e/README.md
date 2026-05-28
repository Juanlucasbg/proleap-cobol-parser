# SaleADS Mi Negocio E2E

This folder contains an end-to-end workflow test for SaleADS.ai:

- Class: `io.proleap.saleads.e2e.SaleadsMiNegocioFullWorkflowE2E`
- Goal: validate Google login + full **Mi Negocio** workflow.
- Evidence: screenshots + `final-report.json` with PASS/FAIL per required section.

## Runtime configuration

Required:

- `SALEADS_LOGIN_URL` -> login URL for the current environment (dev/staging/prod).

Optional:

- `SALEADS_HEADLESS` (default `true`)
- `SALEADS_TIMEOUT_MS` (default `30000`)
- `SALEADS_GOOGLE_EMAIL` (default `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_ARTIFACTS_DIR` (default `target/saleads-mi-negocio-artifacts`)

## Run

```bash
SALEADS_LOGIN_URL="https://<current-env>/login" \
SALEADS_HEADLESS=false \
mvn -Dtest=io.proleap.saleads.e2e.SaleadsMiNegocioFullWorkflowE2E test
```

Artifacts are generated under:

`target/saleads-mi-negocio-artifacts/<timestamp>/`
