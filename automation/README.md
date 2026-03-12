# SaleADS UI Automation

## `saleads_mi_negocio_full_test`

End-to-end workflow validator for the SaleADS **Mi Negocio** module.

### Run

```bash
npm install
npx playwright install chromium
SALEADS_LOGIN_URL="https://<your-env-login-page>" node automation/saleads_mi_negocio_full_test.js
```

### Alternative: attach to an already-open browser

```bash
BROWSER_CDP_URL="http://127.0.0.1:9222" node automation/saleads_mi_negocio_full_test.js
```

### Environment variables

- `SALEADS_LOGIN_URL`: Login URL for the target environment (dev/staging/prod).
- `BROWSER_CDP_URL`: Optional Chrome DevTools endpoint if reusing an existing browser session.
- `GOOGLE_ACCOUNT_EMAIL`: Defaults to `juanlucasbarbiergarzon@gmail.com`.
- `HEADLESS`: `true` (default) or `false`.
- `ARTIFACTS_DIR`: Optional output directory override.

### Outputs

The script writes:

- `report.json` (structured PASS/FAIL results)
- `report.md` (human-readable summary)
- checkpoint screenshots (PNG)

to `artifacts/saleads_mi_negocio_full_test_<timestamp>/`.
