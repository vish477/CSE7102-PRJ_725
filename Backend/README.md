# GeoSafe server

This implements the cloud/application-server/database/guardian-dashboard portion of the architecture.

## Run
1. Install Node.js 18+.
2. Open this folder in Terminal.
3. Run `npm install`.
4. Run `npm start`.
5. Open http://localhost:3000

Endpoints:
- GET /api/health
- POST /api/telemetry
- POST /api/alerts
- GET /api/alerts
- GET /api/telemetry

The included `database.json` is a simple prototype data store. A production system should use an authenticated database and secure transport.
