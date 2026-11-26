# PlebMc Panel

Accounts for a cracked (offline-mode) PlebMc network, plus a website to manage them.

**The problem it fixes:** on an offline-mode server anyone can join with anyone's name, and
nLogin's same-IP sessions don't help on Eaglercraft (every player can show up with the
proxy's IP). So someone could join as you and spend your PlebMc Coins.

**With the panel:** every name can have a PlebMc account with a password. PlebioAPI on the
proxy won't let a player buy, equip, link the website or open the admin menu until they
sign in to the account that owns their name. Names with an account must sign in within
90 seconds of joining or get kicked. Nothing depends on IP addresses.

| Who | What they get |
| --- | --- |
| Players | Make an account in game (`/account register <password> <password>`) or on the website with a code from the game. Sign in with `/account login <password>` on any client, or Right Shift > **Sign in** on the PlebMc client ("Remember me" signs in by itself after that). On the website: coins, cosmetics, change password, sign out everywhere. |
| Admins | Search every player, give/take/set coins, give/remove/put on cosmetics, kick, lock accounts, reset passwords, sign someone out everywhere, create accounts, admin log. |
| Owners | All of that, plus making admins and owners. |

Coins and cosmetics still live in PlebioAPI on the proxy (one place, so nothing can be
spent twice). The proxy sends them to the panel every 20 seconds when they change, and
picks up admin changes from the panel every 3 seconds.

## Put it on a VPS (Docker, about 10 minutes)

You need a small Linux VPS (1 GB RAM is plenty) and a domain or subdomain, for example
`panel.plebmc.top`.

1. **Point the domain at the VPS.** At your DNS provider add an `A` record:
   `panel` → the VPS's IP address. (Cloudflare: set it to "DNS only", grey cloud, the
   first time so the certificate can be issued.)

2. **Install Docker** on the VPS (Ubuntu/Debian):

   ```sh
   curl -fsSL https://get.docker.com | sh
   ```

3. **Copy this `panel` folder to the VPS** (from the repo, or unzip `PlebMc-Panel.zip`):

   ```sh
   scp -r panel root@YOUR_VPS_IP:/opt/plebmc-panel
   ssh root@YOUR_VPS_IP
   cd /opt/plebmc-panel
   ```

4. **Make the settings file:**

   ```sh
   cp .env.example .env
   openssl rand -hex 32        # copy this: it's your server key
   nano .env
   ```

   Set `PANEL_DOMAIN=panel.plebmc.top`, `PANEL_PUBLIC_URL=https://panel.plebmc.top` and
   `PANEL_SERVER_KEY=` the key you just made. Save (Ctrl+O, Enter, Ctrl+X).

5. **Open the firewall** for the website (if you use ufw): `ufw allow 80,443/tcp`

6. **Start it:**

   ```sh
   docker compose up -d --build
   docker compose logs -f        # Ctrl+C to stop watching
   ```

   Caddy gets the HTTPS certificate by itself. Open `https://panel.plebmc.top`: you should
   see the sign-in page.

7. **Connect the proxy.** In the BungeeCord proxy's `plugins/PlebioAPI/config.yml`:

   ```yaml
   accounts:
     enabled: true
     url: 'https://panel.plebmc.top'
     public-url: ''
     server-key: 'the same key as PANEL_SERVER_KEY'
     sign-in-seconds: 90
   ```

   Put the new `PlebioAPI.jar` in `plugins/`, restart the proxy (or `/plebshop reload` if
   the jar was already new). The console says `PlebMc accounts ON`. Within a minute the
   panel's Admin page says "Proxy connected" and lists your players.

8. **Make yourself owner.** Join the server with your name, type
   `/account register <password> <password>`, then on the VPS:

   ```sh
   docker compose exec panel python app.py set-role YourName owner
   ```

   Sign in on the website: you now have the **Admin** tab.

### If the proxy runs on the same VPS

You can skip HTTPS between them: in `docker-compose.yml` add `ports: ["127.0.0.1:8080:8080"]`
under `panel`, and set `url: 'http://127.0.0.1:8080'` and
`public-url: 'https://panel.plebmc.top'` in PlebioAPI's config.

### Pterodactyl / no Docker

It's one Python file with no dependencies, so any host with Python 3.9+ works:

```sh
PANEL_SERVER_KEY=... PANEL_PUBLIC_URL=https://panel.plebmc.top PANEL_PORT=8080 python3 app.py
```

Put it behind HTTPS (Caddy, nginx or Cloudflare Tunnel) and set `PANEL_TRUST_PROXY=1`
so wrong-password limits see real visitor IPs.

## Everyday commands (on the VPS, in /opt/plebmc-panel)

| Command | What |
| --- | --- |
| `docker compose exec panel python app.py set-role NAME owner\|admin\|player` | Change someone's role |
| `docker compose exec panel python app.py reset-password NAME` | Set a new password (asks for it) |
| `docker compose exec panel python app.py create-account NAME [role]` | Make an account (asks for the password) |
| `docker compose exec panel python app.py unlock NAME` | Unlock a locked account |
| `docker compose logs -f panel` | Watch the log |
| `git pull && docker compose up -d --build` | Update after pulling new code |

**Backups:** everything is one SQLite file in the `panel_data` volume:

```sh
docker compose exec panel python -c "import sqlite3; sqlite3.connect('/data/panel.db').backup(sqlite3.connect('/data/backup.db'))"
docker compose cp panel:/data/backup.db ./panel-backup-$(date +%F).db
```

## In game

| Command | What |
| --- | --- |
| `/account` | Are you signed in? What to do next |
| `/account register <password> <password>` | Make an account for the name you're on (signs you in) |
| `/account login <password>` | Sign in (any client) |
| `/account logout` | Sign out for this session |
| `/account code` | A code (15 minutes) to make the account on the website instead |

`/account` is handled by the proxy before BungeeCord sees it as a command, so the password
never reaches a backend server or the proxy's command log.

## How it keeps people out

- A name with an account can't spend, buy, equip, link the website or open the admin menu
  until it signs in, and is kicked if it doesn't sign in within `sign-in-seconds`.
- A look-alike spelling ("owner" for "Owner") is kicked: it would be a different UUID.
- 5 wrong passwords for a name lock that name's sign-in for 10 minutes (per name, because
  Eaglercraft players can share an IP). A saved client sign-in still works meanwhile.
- "Remember me" saves a random token in the browser, never the password. Tokens expire
  after 60 days unused, at most 10 per account, and are wiped by a password change,
  "Sign out everywhere", a lock or a delete.
- Website registration needs a code from the game (`/account code`), so nobody can claim
  your name on the website without being on it. With nLogin installed, `/account register`
  and `/account code` also need nLogin's `/login` first.
- Passwords are stored with scrypt. The server key only lives in the panel's `.env` and
  the proxy's config; never put it in the client.
- Admins can't change other admins or owners; only owners can change roles.
  Every admin change is in the admin log.

**Claim your names first.** Until a name has an account, whoever registers it first owns it.
Register your staff names right after you turn this on, and tell players to make accounts.

### nLogin

Keep nLogin if you like (it still guards the backends before `/login`), but with
`forward_ip: false` in EaglerXServer, turn off nLogin's same-IP auto-login
(`bypass-online-check-with-same-ip: false` and session login off), or Eaglercraft players
sharing the proxy's IP are logged in as each other. PlebMc accounts don't depend on it.

## Development

```sh
python3 -m unittest discover -s panel/tests      # panel tests
sh plugins/PlebMcShop/test/run.sh                # PlebioAPI tests, incl. panel end to end
```
