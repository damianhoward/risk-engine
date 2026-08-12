# Deploy

The live risk view runs as a systemd JVM service behind Caddy, alongside the order book on the
same box. The pipeline (`.github/workflows/deploy.yml`) builds once via `installDist`, then asks
the box for a release over SSH against a pinned host key, with the bundle on stdin. The release
unpacks into `/srv/risk-engine/releases/<commit>` and `/srv/risk-engine/current` moves onto it with
a symlink rename; success is gated on a `/readyz` 200. A release that does not come up is rolled
back to its predecessor by the script on the box; three releases are retained.

## Service

The systemd unit runs the `installDist` launcher on `PORT=8081` with `-Xmx160m` — the process is
light (no Kafka, no ring buffer), sized to fit beside the order book on a 1 GB box. Port 8081 is
bound to localhost; only Caddy is public.

The unit is not in this repository. A unit file is a request to run anything as anyone, so a
deploy account able to install one holds root by another name; it is owned as host configuration
and applied by an operator. `DEPLOY_SSH_KEY` is a key of CI's own, pinned on the box to a forced
command — it can ask for a release and nothing else, and the account behind it may run exactly one
command as root, `systemctl restart risk-engine`.

## GitHub Actions secrets (repo settings → Secrets → Actions)

Same box as the order book, so the same three values:

- `DEPLOY_SSH_KEY` — the deploy private key
- `DEPLOY_HOST` — the box IP
- `DEPLOY_USER` — the login user (`ubuntu`)

## Host setup

A Cloudflare A record for `risk.damianhoward.com` points at the box, **DNS only / grey cloud** —
proxying it breaks Caddy's ACME challenge. The Caddy route is one operator step rather than none:
the host's configuration is version-controlled in the private infrastructure repository as one
whole file per box and installed by a script there, validated and backed up first. A deploy of
this service does not touch it — a bad Caddyfile takes every site on the box down at once, which
should not be reachable as a side effect of shipping one service. The service adds no publicly
reachable port.

## Rollback

Automatic, and decided on the box rather than by the runner. A release that does not answer
`/readyz` has the `~/risk-engine` symlink moved back onto the previous release directory and the
service restarted, so a runner that dies mid-deploy cannot leave a broken release serving. Three
releases are retained, which is what makes the previous one still there to point at.
