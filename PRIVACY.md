# NeroDecor privacy & telemetry disclosure

NeroDecor includes anonymous error reporting so that crashes and bugs caused by the mod can be
found and fixed. It is **on by default** and **opt-out** (see [How to opt out](#how-to-opt-out)).
This page is the full disclosure required by CurseForge's moderation rules for mods that use an
external analytics/error service, and it documents how the system is designed to comply with the
GDPR (EU) and POPIA (South Africa).

## Player data

From 0.8.0 ("Chroma") NeroDecor stores a small amount of player data on the **server**: who owns and
who may edit each Chroma hub network, and the names players give to zones and scenes. Everything
else about the mod is cosmetic world state. None of it is sent with error reports. Some of it has to
reach players' games for the hub to work; exactly what goes to whom is listed under
[What is sent to players' games](#what-is-sent-to-players-games).

### What Chroma stores

| Data | Where | Why |
|------|-------|-----|
| Hub owner's player UUID | The dimension's `data/nerodecor/chroma_networks.dat` on Minecraft 26.x (`data/nerodecor_chroma_networks.dat` on 1.21.x), plus Neroland Core's last-known-good copy `data/nerodecor_chroma_networks_backup.dat` | Only the owner may manage the hub |
| Trusted players' UUIDs, each with a role (viewer, painter, admin); at most 64 per hub, and only players the server knows (online or in its profile cache) | Same file | Lets the owner share editing |
| Zone names and scene names typed by players | Same file | Shown in the hub editor |
| The hub's position, volume, linked blocks, palette, zone channels, broken/orphaned timestamps | Same file | Runs the colours; the timestamps drive retention |

Player **names** are never stored, only UUIDs. Scenes and Scene Chips carry **no author**: a scene is
a name plus colour keyframes, so a share code or a chip says nothing about who made it.

Linked blocks, colours, palettes and channels are decoration, not personal data, so they outlive an
erasure request (see below).

### What is sent to players' games

| Data | Sent to | Why |
|------|---------|-----|
| Trusted players' UUIDs and roles | The hub screen of the hub's **owner and admins** only; viewers and painters receive an empty list | So the owner can see and revoke every entry |
| Zone names, scene slot names, palette, linked blocks (as offsets from the hub) | Anyone with at least the **viewer** role who opens the hub screen | Shown in the hub editor |
| The hub's position, its linked blocks, palette and the scene playing | Every player whose game is **tracking the hub's chunk** | Their game needs it to draw the colours, exactly as it needs any block's position |

The owner's UUID is not sent to anyone. Hub-screen edits are accepted only from a player standing
near the hub (inside its volume plus 8 blocks, or within 16 blocks of it) who passes the world's
build and claim protection. NeroLink remote actions (below) skip the proximity check but need the
painter role and the player online, and a zone recolour still passes the build and claim protection
for every block it changes.

### Holo signs, wall screens and other display text

Text typed into a Holo Sign, Wall Screen, Holo Projector or Holo Table, or pushed to one through
NeroLink, lives in that block's block entity in the world, exactly like vanilla sign text. NeroDecor
records **no author** for it and keeps no index of who wrote what, so it is not linked to any player
and **erasure does not touch it**, just as vanilla does not erase sign text. The same holds for
display text that another mod pushes to these blocks: it persists in the world like vanilla sign
text. Anyone may break or edit the block as the world's protection rules allow. Do not put personal
information on signs.

### Scene Chips

A Scene Chip is an item holding a copy of a scene: its name and colour keyframes, no author. Chips are
items in the world (in chests, inventories, item frames), so an erasure request does **not** reach
them and they **keep their scene names** after the hub they were copied from is orphaned. Whoever
holds a chip can rename it by writing another scene to it, or destroy it.

A block's paint colour is a blockstate property (and the `nerodecor:color` data component on its
item); it records nothing about who applied it.

### Erasure

NeroDecor registers with Neroland Core's shared per-player erasure hook (`PlayerDataErasure`), so a
single erasure request (for example Core's `/neroland data eraseme`, or an operator acting on a
request) reaches Chroma along with every other Neroland mod. For player **P**:

- P is removed from **every** trusted list on every hub in every dimension.
- Each hub P **owns** becomes **orphaned**: the owner is cleared, its trusted list is emptied, every
  zone and scene name is replaced with a neutral default ("Default", "Zone 2", "Scene 1" ...),
  auto-adopt is switched off, and the orphan clock starts. The hub keeps colouring its blocks, so the
  build does not suddenly change, but **nobody can edit it** until a server operator claims it with
  `/nerodecor chroma claim` while standing in its volume.
- Any export file for P (below) and P's in-memory rate-limit bucket are deleted.
- Then every dimension's network file **and** its last-known-good backup are written to disk at
  once, in the same request rather than at the next autosave, so P's UUID lingers in neither copy.
  Every step runs even if an earlier one failed; a failure is reported once (exception type only, no
  player data) and Core records the erasure as incomplete.
- Scene Chips and sign/screen text are not touched (see above).

### Retention

Two separate timers delete networks automatically:

| Config key | Default | Deletes |
|------------|---------|---------|
| `chroma.orphanRetentionDays` | **30** days | Orphaned networks (owner erased) that no operator has claimed |
| `chroma.inactiveRetentionDays` | **90** days | Networks whose hub block has been broken and not re-placed |

Whichever applies first wins. Once a network is deleted its blocks show their own painted colour.
Export files are deleted after **7** days. All three sweeps run when the server starts and every 10
minutes after that, **even when Chroma is switched off** (`chroma.enabled=false`); a deleted network
is written out of both the network file and its backup straight away.

### Export (access and portability)

Any player can run `/nerodecor privacy export` (once a minute) to get a JSON copy of their own
Chroma data at `<world>/nerodecor/exports/<uuid>.json`. On Linux and macOS the folder and file are
made readable by the server's own account only; on Windows the world folder's normal permissions
apply.

- **Hubs you own**: dimension, network id, the hub's **coordinates** (your own build, so yours to
  see; players near the hub also receive its position, see above), volume, whether it is placed,
  linked-block count, zones (name and channel), scenes (name and share code) and the players you
  trusted (UUID and role, plus a count per role): who you chose to trust is your own data.
- **Roles you hold** on other players' hubs: dimension, network id and role only, with **no
  coordinates and no owner**.
- The retention periods in force.

Ask the server operator for the file. It is deleted after 7 days or on erasure, whichever is first.

### What Chroma does NOT store

- No player names, chat, IP addresses or play-time statistics.
- No record of who linked, painted or recoloured a block, and no edit history.
- No author on scenes, share codes, Scene Chips, signs or screens.
- No position history: a hub's current anchor is the only position stored, and only because the hub
  needs it to work.
- Logs never contain player UUIDs, names, coordinates, or zone, scene or sign text.

### NeroLink companion module

The **NeroLink companion module** (`enableLinkModule`, default on, server-authoritative) publishes:

- the shared Neroland palette and the list of NeroDecor blocks: registry ids, hex colours and enum
  names, identical for every requester and already visible to anyone who opens the creative tab;
- to each player, **only the Chroma hubs they own or are trusted on**: the hub's random network id,
  their own role, block/zone/scene counts, the scene playing, brightness, zone names, channels and
  colours, and scene slot names. **No coordinates, no dimension and no other player**: trusted
  players appear only as a count per role. An orphaned hub is shown to nobody;
- live events about those hubs (scene changed, trigger fired, hub broken), sent only to the hub's
  owner and trusted players.

It lets painters and above apply a scene, recolour a zone, start or stop the animation and set the
brightness remotely, at most once a second per player. It stores nothing new: in memory only, a
one-second rate-limit bucket per requester and the last scene each hub played, both gone when the
server stops. Nothing is logged with a player, a position or scene text. The module only serves
data through the separate NeroLink bridge mod, if a server installs it; NeroDecor itself opens no
network connection for it. Set `enableLinkModule=false` to leave it unregistered. See the
[Link Module wiki page](https://github.com/Neroland/nerodecor/blob/main/wiki/Link-Module.md).

Apart from that, and the export file you ask for, the only data NeroDecor ever sends anywhere is the
error reporting described below.

## What is collected (error reporting)

When an error **caused by NeroDecor code** occurs, a report is sent to
[Sentry](https://sentry.io) (hosted in the **EU** — `ingest.de.sentry.io`) containing:

- the stack trace of the error and the error message (file paths containing your
  operating-system account name, UUIDs and world coordinates are masked before sending),
- the NeroDecor version, the loader (NeoForge / Forge / Fabric) and its version, the Minecraft
  version, and whether the game is a client or dedicated server,
- the operating system name/version and Java version,
- the mod's own configuration values (e.g. whether connected textures and emissive rendering are
  enabled), so a crash that only happens at non-default settings can be reproduced,
- the list of **other loaded mods** (their public ids and versions only), so a crash caused by a
  mod conflict can be identified,
- a random event ID generated by Sentry for the individual report.

To measure overall stability, the mod also sends **release-health sessions**: a count that a play
session started and whether it ended cleanly or crashed, tagged only with the mod / loader /
Minecraft version. Each session uses a random identifier that exists only for that single play
session and is **not** linked to any other session, to your account, or to your machine.

## What is NOT collected

- **No IP address** is stored (`sendDefaultPii` is disabled; Sentry discards the sender IP).
- **No username, player UUID, or machine/host name** (`attachServerName` is disabled and the
  server name is cleared from every event before it is sent).
- **No world data, chat, coordinates, inventories, block positions, paint choices, hub, zone,
  scene or sign text, or gameplay analytics** of any kind.
- **No breadcrumbs**: the error reporter keeps no trail of earlier log lines or actions.
- **No advertising or tracking identifiers** — there is no persistent ID linking reports from
  the same player or machine.
- Errors caused by other mods or by Minecraft itself are filtered out and never sent: an event
  whose stack trace does not name NeroDecor's own package is dropped before sending.

Reports are also de-duplicated and hard-capped at 10 events per game session.

## How to opt out

Set the following in `config/nerodecor.properties` (created on first launch):

```properties
telemetryEnabled=false
```

This single switch covers **everything** the mod sends — error reports, release-health sessions
and the loaded-mod list alike. It is a client-local setting: a server can never force it on or off.

The change takes effect on next launch and no data is sent while disabled. Server owners can opt
out for the whole server the same way. Nothing is ever sent before the config — and therefore
your choice — has been loaded.

## Legal basis and your rights (GDPR / POPIA)

Error reports are processed on the basis of **legitimate interest** (GDPR Art. 6(1)(f);
POPIA s11(1)(f)): keeping the mod functional and crash-free, using the minimum data necessary.
The reports are engineered to contain no directly identifying personal information, are stored
in the EU, and are retained only as long as Sentry's standard retention period (90 days) before
automatic deletion.

If you believe a report contains personal data, or wish to exercise any data-subject right
(access, deletion, objection), contact:
**[info@neroland.co.za](mailto:info@neroland.co.za)**. Include the approximate date/time of the
crash so the matching event can be located and removed.

Sentry acts as a data processor; see the [Sentry privacy policy](https://sentry.io/privacy/) and
[data processing addendum](https://sentry.io/legal/dpa/).

## Changes to this policy

If a future version of NeroDecor collects additional data, this page will be updated **before**
that version is released and the change will be called out in the changelog. The same opt-out
(`telemetryEnabled = false`) will continue to cover everything the mod sends. The policy shipped
alongside a given mod version describes exactly what that version does; 0.7.x and earlier stored
no player data at all.

## Developer note

The DSN is embedded (a write-only public client key), so no env file or credentials are needed.
Development (IDE) runs also report, tagged `environment=development` so they are filtered out of
production metrics; `telemetryEnabled=false` silences those too.

---

## CurseForge telemetry blurb

Keep the following (or equivalent) in the project description while telemetry is enabled, as
required by CurseForge moderation policy:

> **Telemetry notice:** NeroDecor sends anonymous error reports (stack trace + mod/game
> versions only — never IPs, usernames, UUIDs, or world data) to the developers via Sentry
> (EU servers) so crashes can be fixed. On by default — opt out any time by setting
> `telemetryEnabled = false` in `config/nerodecor.properties`. Full details:
> [PRIVACY.md](https://github.com/Neroland/nerodecor/blob/main/PRIVACY.md).
