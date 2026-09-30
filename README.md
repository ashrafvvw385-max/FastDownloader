# VPS deployment

1. Copy `.env.example` from the repository root to `server/.env`.
2. Change both secrets to long random values.
3. On the VPS, run:

    cd server
    docker compose --env-file .env up -d

4. Put the VPS behind HTTPS before using it over the public internet.
5. In the Android app, enter the VPS base address and the API token.

For a production deployment, add firewall rules, HTTPS, backups, and stronger
download-file access controls.

Only download content you are authorized to download.
