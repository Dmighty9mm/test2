# PlebMc Panel

Accounts for a cracked (offline-mode) PlebMc network, plus a website to manage them.

**The problem it fixes:** on an offline-mode server anyone can join with anyone's name, and
nLogin's same-IP sessions don't help on Eaglercraft (every player can show up with the
proxy's IP). So someone could join as you and spend your PlebMc Coins.

**With the panel:** every name can have a PlebMc account with a password. PlebioAPI on the
proxy won't let a player buy, equip or open the admin menu until they
sign in to the account that owns their name. Names with an account must sign in within
90 seconds of joining or get kicked. Nothing depends on IP addresses.

| Who | What they get |
| --- | --- |
| Everyone | Home page with server status and the latest PlebClient, leaderboard, shop catalog, the **Gallery** of shared hats and cloaks (like them, remix them), and the **Hat Maker** and **Cloak Maker**. |
| Players | Make an account on the PlebClient's start screen (the client can't be used until you sign in or create one) or on the website's **Create account** page. The client remembers the sign-in. On the website: coins, cosmetics, streak, saved designs (up to 30, private or shared), change password, sign out everywhere. |
| Admins | Dashboard (online, sign-ups chart, active players, coins in circulation, most owned cosmetics, client versions in use), players list (search, filters, sorting, 50 at a time with **Show more**), give/take/set coins, give/remove/put on cosmetics, kick, lock accounts, reset passwords, staff notes on players, announcements (website banner and/or in-game chat), **Publish to shop** (a cloak PNG or hat .bbmodel goes straight into PlebioAPI's shop), admin log with search. Players with the admin or owner role also get the client's Admin menu (after `/adminlogin`). |
| Owners | All of that, plus making admins and owners and setting the **client update checker** (latest version, oldest allowed version, download link, message). |

### The homepage and Site content

The panel is also the PlebMc homepage, in PlebMc's own look (blue, blocky, the TNT art):
the big picture with the name in pixel letters, the server address drawn like a server-list
row (click to copy), live numbers in a hotbar, feature rows with screenshots, a podium for
each game's top players, "Playing in 3 steps", an FAQ and a TNT banner. The other pages
(Download, Stats, Leaderboards, Shop, My account, Admin) share the same look.

Owners change it on **Admin > Site content** without touching code: the logo, the big
picture, a name logo (your blocky PLEBMC art, shown instead of the pixel letters), the
accent color, tagline, text, server address, Discord link, feature rows (with pictures),
FAQ, the TNT banner and footer. Pictures (PNG, JPEG, GIF or WebP, up to 5 MB) are uploaded
to the panel and kept in its database. **Reset to defaults** puts everything back.

### Voting

The **Vote** page lists the server lists players vote on (by default `https://vote.plebmc.top/`),
with the steps and the reward. The browser remembers which sites a visitor voted on today:
until they're done, the Vote link shows a red dot and a small reminder pops up in the corner
(one **Later** hides it for the day). Owners change the vote sites, the reward shown and the
note on **Admin > Site content > Voting**; with no vote sites, all of it is hidden. The site
only links out: PlebioAPI still pays the coins when the vote comes in over Votifier
(`voting.reward` in its config), so keep the number on the site the same as that one.

### The website updating itself

The panel keeps itself up to date. Every 10 minutes it asks GitHub for the newest `panel/` folder on the
`main` branch of `Dmighty9mm/test2`. When that is newer than what is running, it downloads it, checks every
file against git's hashes, tests that the new build starts, and restarts into it (a few seconds). So once a
panel change is merged into `main`, the site picks it up on its own: no zip to upload.

- New builds are stored next to the database (`updates/` beside `panel.db`), never written over the
  installed files, so they survive a Docker rebuild and the installed copy is always there to fall back on.
- A build that fails its start-up test is not installed. A build that fails to start twice is dropped and
  the site goes back to the installed copy. Deleting the `updates/` folder does the same by hand.
- It never goes backwards: a zip built from changes that are not merged yet is newer than `main`, so it is
  left alone until `main` catches up.
- Uploading a new zip (or rebuilding the image) still works and wins over older downloads.
- **Admin > Updates > Website updates** (owners): see the running build, *Check now*, *Update now*, and
  switch automatic installs off. Updates show in the admin log.
- Accounts, coins, settings and uploaded pictures live in the database and are not touched.

Whoever can merge into that branch decides what code the site runs, so keep the repository's write access tight.

| Setting | Default | What it does |
| --- | --- | --- |
| `PANEL_AUTO_UPDATE` | `1` | `0` turns the updater off completely |
| `PANEL_UPDATE_REPO` | `Dmighty9mm/test2` | The GitHub repository to follow |
| `PANEL_UPDATE_BRANCH` | `main` | The branch to follow |
| `PANEL_UPDATE_MINUTES` | `10` | How often to check |
| `PANEL_UPDATE_TOKEN` | none | A read-only GitHub token, only needed if the repository is private |

### Client updates

Each PlebClient tells the panel its version when it starts (`/api/client/version`). On
**Updates**, set *Latest version* to the newest release: older clients show an
Update screen with a Later button. Set *Oldest allowed version* to block clients that are
too old (they can't get past the Update screen). This build of the client is version 2.0.

### Publishing and big uploads

Publishing a cloak and saving designs send files up to about 12 MB. nginx allows only
1 MB by default, so in the panel's `location / { ... }` block add:

```
client_max_body_size 16m;
```

then `sudo nginx -t && sudo systemctl reload nginx`. Without it, publishing shows "too big
for the panel's web server".

Coins and cosmetics still live in PlebioAPI on the proxy (one place, so nothing can be
spent twice). The proxy sends them to the panel every 20 seconds when they change, and
picks up admin changes from the panel every 3 seconds.

## Put it on a VPS (Docker, about 10 minutes)

You need a small Linux VPS (1 GB RAM is plenty) and a domain or subdomain, for example
`plebmc.top`.

1. **Point the domain at the VPS.** At your DNS provider add an `A` record:
   `client` → the VPS's IP address. (Cloudflare: set it to "DNS only", grey cloud, the
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

   Set `PANEL_DOMAIN=plebmc.top`, `PANEL_PUBLIC_URL=https://plebmc.top` and
   `PANEL_SERVER_KEY=` the key you just made. Save (Ctrl+O, Enter, Ctrl+X).

5. **Open the firewall** for the website (if you use ufw): `ufw allow 80,443/tcp`

6. **Start it:**

   ```sh
   docker compose up -d --build
   docker compose logs -f        # Ctrl+C to stop watching
   ```

   Caddy gets the HTTPS certificate by itself. Open `https://plebmc.top`: you should
   see the sign-in page.

7. **Connect the proxy.** In the BungeeCord proxy's `plugins/PlebioAPI/config.yml`:

   ```yaml
   accounts:
     enabled: true
     url: 'https://plebmc.top'
     public-url: ''
     server-key: 'the same key as PANEL_SERVER_KEY'
     sign-in-seconds: 90
   ```

   Put the new `PlebioAPI.jar` in `plugins/`, restart the proxy (or `/pleb reload` if
   the jar was already new). The console says `PlebMc accounts ON`. Within a minute the
   panel's Admin page says "Proxy connected" and lists your players.

8. **Make yourself owner.** Make your account first, either on the website
   (`https://plebmc.top` > **Create account**, name + password, no code needed) or on
   the PlebClient's start screen (**Create account**). Use the exact name you play as. Then on
   the VPS:

   ```sh
   cd /opt/plebmc-panel
   docker compose exec panel python app.py set-role YourName owner
   ```

   Sign out and back in on the website: you now have the **Admin** tab. (No account yet? You
   can also make it straight from the VPS:
   `docker compose exec panel python app.py create-account YourName owner`, which asks for the
   password.)

### The client's start screen

The PlebMc client opens on a **Sign in / Create account** screen and can't be used until the
player signs in. It talks to `https://plebmc.top` (built into the client), so host the
panel at that address, or tell us your domain and we change it in the client. Settings in `.env`:

| Setting | Default | What |
| --- | --- | --- |
| `PANEL_CLIENT_REGISTRATION` | `1` | `0` turns off making accounts from the client (sign-in still works) |
| `PANEL_CLIENT_REGISTRATIONS_PER_HOUR` | `3` | New accounts per IP address per hour from the client |

If the panel is down, players with a saved sign-in still get in; new players can't sign up
until it's back.

### If the proxy runs on the same VPS

You can skip HTTPS between them: in `docker-compose.yml` add `ports: ["127.0.0.1:8080:8080"]`
under `panel`, and set `url: 'http://127.0.0.1:8080'` and
`public-url: 'https://plebmc.top'` in PlebioAPI's config.

### Pterodactyl / no Docker

It's one Python file with no dependencies, so any host with Python 3.9+ works:

```sh
PANEL_SERVER_KEY=... PANEL_PUBLIC_URL=https://plebmc.top PANEL_PORT=8080 python3 app.py
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

PlebClient players don't need any of these: the start screen signs them in. They're for
players on other clients (who also don't earn coins, see `plebclient-only`).

| Command | What |
| --- | --- |
| `/account` | Are you signed in? What to do next |
| `/account register <password> <password>` | Make an account for the name you're on (signs you in) |
| `/account logout` | Sign out for this session |
| `/account code` | A code (15 minutes) to make the account on the website instead |

`/account` is handled by the proxy before BungeeCord sees it as a command, so the password
never reaches a backend server or the proxy's command log.

## Profile pictures and usernames

**Profile pictures are skin faces.** Everywhere the site shows a player (header, leaderboards,
stats, the players list) it draws the face of their Minecraft skin, hat layer included. A skin
comes from, in this order:

1. The player's own: picked on **My account > Profile picture** (a 64x64 skin PNG), or sent by
   the PlebMc client with `POST /api/client/skin` and `{name, token, png}` (base64 PNG) or
   `{name, token, pixels}` (base64 of the raw 64x64 RGBA bytes).
2. The skin of the Minecraft account with the same name, looked up at Mojang by the panel
   (only for names this server knows, one at a time, remembered for a day). Owners can switch
   this off under **Site content > Brand**.
3. Neither: the colored letter square, as before.

**Usernames can be changed.** Signed-in players change theirs on **My account > Change
username** (password needed, once every 7 days). Admins rename anyone from the player's page
with no wait. On an offline-mode server the name *is* the player, so the panel queues a
`rename` for PlebioAPI, which carries coins, cosmetics, streak, quest progress and LuckPerms
ranks over to the new name and kicks the old one if it is online. Whatever a game server saved
under the old name (inventory, world progress) does not follow. A name with an account, or one
somebody has already played under, can't be taken.

## Ideas board (admins)

**Admin > Ideas** is the team's own board, hidden from players. Make sections (it starts with
PlebMc, PlebNite, PlebRPG and Other), and add ideas to them: a name, an optional description,
a priority (urgent, high, medium, low) and a status (idea, planned, working on it, done,
dropped). The most urgent ideas come first, finished ones last, and "Hide finished ideas"
tucks them away. Sections can be renamed, reordered and deleted.

## Stats and leaderboards

**Stats**: type a player's name to see their stats, one game at a time: PlebMc
(first: coins, cosmetics, daily streak), PlebNite and PlebRPG. **Leaderboards**: pick a
game and "All stats" shows the top 10 of every stat side by side (Kills, Wins, Deaths,
Play time and so on, like the Discord bot); pick one stat for its top 100. PlebNite and
PlebRPG show their boards (empty) before their servers send anything. Click a name to
open their stats.

The game servers send stats with **PlebMcBridge** (2.1+):

1. Put `PANEL_STATS_KEY=` (make one with `openssl rand -hex 32`) in `.env`, then
   `docker compose up -d`. This key can only send stats.
2. On PlebNite and PlebRPG, put PlebMcBridge.jar in `plugins/`, start once, and edit
   `plugins/PlebMcBridge/config.yml`: `stats.enabled: true`, `game:` (`plebnite` or
   `plebrpg`), `key:` the stats key, and the `values:` to send (PlaceholderAPI
   placeholders or `statistic:NAME`; the file has examples for both servers).
3. Restart, then run `/plebstats <player>` to check what is read and send it right away.

Online players are sent every 5 minutes and when they leave, so boards fill up as people
play. Anyone can read `GET /api/stats/games`, `/api/stats/top?game=&stat=`,
`/api/stats/boards?game=` (every stat's top 10) and `/api/stats/player?name=`.

## Ranks with LuckPerms

Put LuckPerms on the BungeeCord proxy (and keep it on your backends), with every
LuckPerms install pointed at the same MySQL/MariaDB database and `messaging-service: sql`
(or `pluginmsg`) so a change on the proxy reaches the backends straight away.
PlebioAPI finds LuckPerms on the proxy by itself. The panel then gets a **Ranks**
page: owners can make and delete ranks, set prefix/suffix (`&` and `&#hex` codes),
display name and weight, add or remove permissions and parents, and set a player's
rank from their player page. Admins can look but only owners can change ranks.
If LuckPerms is not on the proxy, the Ranks page just says so.

## How it keeps people out

- A name with an account can't spend, buy, equip, link the website or open the admin menu
  until it signs in, and is kicked if it doesn't sign in within `sign-in-seconds`.
- A look-alike spelling ("owner" for "Owner") is kicked: it would be a different UUID.
- 5 wrong passwords for a name lock that name's sign-in for 10 minutes (per name, because
  Eaglercraft players can share an IP). A saved client sign-in still works meanwhile.
- "Remember me" saves a random token in the browser, never the password. Tokens expire
  after 60 days unused, at most 10 per account, and are wiped by a password change,
  "Sign out everywhere", a lock or a delete.
- New accounts (website or client start screen) are limited to
  `PANEL_CLIENT_REGISTRATIONS_PER_HOUR` per IP. Set `PANEL_OPEN_REGISTRATION=0` to make the
  website ask for a code from `/account code` in game instead (only useful for players on
  other clients).
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

### Plebio and motion

Plebio, the mascot, stands around the site: by the homepage numbers, next to page headings, on the vote strip and reminder, on the TNT banner, on the sign-in card and sitting on the footer. Click him and he reacts. Owners can turn him off under Admin > Site content > Brand. His pictures live in `static/plebio/`.

Pages animate as they load and homepage sections slide in as you scroll. Everything is transform and opacity only, and it all switches off (Plebio stands still too) for people whose device asks for reduced motion.

### News posts

The **News** page (`#news`) lists posts owners write: server and client changelogs, events, anything. Each post has a
title, a kind (News, Server update, Client update, Event), an optional pin, and a body in markdown. The three newest
also show on the homepage.

- Owners write, edit and delete posts under **Admin > Announcements**, with a live preview. (The old banner and
  game-chat announcement is on the same page as "Quick announcement".)
- Markdown that works: `## headings`, `**bold**`, `*italic*`, `~~strike~~`, `- lists` (one level of nesting),
  `1. numbered lists`, `> quotes`, `` `code` `` and fenced code blocks, `[links](https://…)`, bare links, and `---`.
  Pictures are not supported. Posts are drawn as text, never as HTML, so nothing typed in a post can run on the page.
- API: `GET /api/posts?offset=&limit=&tag=`, `GET /api/posts/<id>`, `POST /api/admin/posts` (owner).

### Giving coins to everyone online

The dashboard's gift has a **PlebClient players only** switch for that one gift. On: only players on PlebClient get
it. Off: everyone online does. It doesn't change `plebclient-only` in PlebioAPI's config. PlebioAPI needs to be the
build from this update or newer; an older one ignores the switch and follows `plebclient-only`.
