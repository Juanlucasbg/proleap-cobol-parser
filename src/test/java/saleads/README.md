SaleADS Mi Negocio E2E test
===========================

This folder contains `SaleadsMiNegocioFullTest`, a Playwright + JUnit workflow test that:

1. Logs in with Google.
2. Validates the full **Mi Negocio** sidebar and account workflow.
3. Validates legal links (including new-tab behavior).
4. Captures screenshots and writes a final PASS/FAIL report.

Environment variables
---------------------

The test is opt-in and environment agnostic (no hardcoded SaleADS domain):

- `SALEADS_E2E_ENABLED` (required): set to `true` to run.
- `SALEADS_LOGIN_URL` (required): login URL for the current environment (dev/staging/prod).
- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (optional): defaults to `juanlucasbarbiergarzon@gmail.com`.
- `SALEADS_HEADLESS` (optional): defaults to `true`.
- `SALEADS_TIMEOUT_MS` (optional): defaults to `30000`.
- `SALEADS_EVIDENCE_DIR` (optional): defaults to `target/saleads-evidence`.

Run example
-----------

```bash
SALEADS_E2E_ENABLED=true \
SALEADS_LOGIN_URL="https://<current-saleads-env>/login" \
mvn -Dtest=saleads.SaleadsMiNegocioFullTest test
```

Evidence
--------

On execution, screenshots and `final-report.txt` are stored in `SALEADS_EVIDENCE_DIR`.
