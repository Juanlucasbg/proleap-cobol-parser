# SaleADS Mi Negocio Full Test Report

- Test name: `saleads_mi_negocio_full_test`
- Executed at (UTC): 2026-07-20 01:06
- Trigger: cron (`0 * * * *`)
- Runner: Playwright (`e2e/tests/saleads-mi-negocio-full.spec.js`)
- Environment rule: environment-agnostic test logic (no hardcoded environment URL)
- Runtime target used for this execution: `https://saleads.ai` (via `SALEADS_LOGIN_URL`)

## Execution Result

The workflow was **blocked before login UI** due to a Cloudflare access page:

- `Sorry, you have been blocked`
- `You are unable to access saleads.ai`
- Cloudflare Ray ID: `a1de0fa04ed13416`

Because the application UI never loaded, authenticated workflow validations (Mi Negocio module) could not be executed in this environment.

## Final PASS/FAIL Report

| Field | Result | Notes |
|---|---|---|
| Login | FAIL | Google login button not reachable because Cloudflare block page replaced app login screen. |
| Mi Negocio menu | FAIL | Requires successful app login; not reachable due upstream block. |
| Agregar Negocio modal | FAIL | Requires authenticated app view; not reachable due upstream block. |
| Administrar Negocios view | FAIL | Requires authenticated app view; not reachable due upstream block. |
| Información General | FAIL | Requires authenticated app view; not reachable due upstream block. |
| Detalles de la Cuenta | FAIL | Requires authenticated app view; not reachable due upstream block. |
| Tus Negocios | FAIL | Requires authenticated app view; not reachable due upstream block. |
| Términos y Condiciones | FAIL | Legal link is inside blocked app context; navigation step could not be executed. |
| Política de Privacidad | FAIL | Legal link is inside blocked app context; navigation step could not be executed. |

## Evidence

- Failure screenshot:
  - `/workspace/e2e/test-results/saleads-mi-negocio-full-saleads-mi-negocio-full-test/test-failed-1.png`
- Run video:
  - `/workspace/e2e/test-results/saleads-mi-negocio-full-saleads-mi-negocio-full-test/video.webm`
- Error context and page accessibility snapshot:
  - `/workspace/e2e/test-results/saleads-mi-negocio-full-saleads-mi-negocio-full-test/error-context.md`

## Captured URLs

- Initial/final reachable URL in this run: `https://saleads.ai/`
- Términos y Condiciones URL: N/A (step blocked before app navigation)
- Política de Privacidad URL: N/A (step blocked before app navigation)

## Notes

- The test implementation itself remains environment-agnostic and text-selector based as requested.
- This specific run failed due to network/security gating before login controls were rendered.
