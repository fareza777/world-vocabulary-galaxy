# Packaging notes — shipping the 396 MB of media

## The constraint

VerbiLume is offline-first by design: the app declares **no `INTERNET` permission**, so every
word, illustration and recording has to live on the device. That payload is large.

| Content | Size |
| --- | ---: |
| 7,298 WebP illustrations | ~185 MB |
| 22,905 m4a/mp3 recordings | ~209 MB |
| Immutable content database (`atlas.db`) | ~9 MB |
| Brand art | ~2 MB |
| **Total bundled media** | **~396 MB** |

Google Play's limits that matter here:

| Artifact | Limit |
| --- | --- |
| APK uploaded directly | 100 MB |
| App bundle — base module download size | 200 MB |

So the release **AAB cannot be uploaded as-is**: its base module is well over 200 MB. This is a
distribution decision, not a bug, and it has to be made before publishing.

## Option A — Play Asset Delivery (install-time pack) ← recommended for Play

Move `assets/atlas/**` into a separate asset-pack module delivered at install time. Play then
splits the download: the base APK stays small, and the media arrives with the install.

What it costs:

- a new `:media_pack` module with `assetPacks { installTime { } }` in the delivery DSL;
- the media moved out of `src/main/assets` into the pack;
- runtime reads go through Play Core's asset access instead of the plain `AssetManager`;
- `noCompress` must be declared for the pack so `openFd` streaming keeps working;
- on-demand/fast-follow variants need a download UI (install-time needs none);
- it can only be end-to-end tested through the Play Console (internal testing track) or with
  `bundletool --local-testing`.

The app currently reads media through `AssetManager` with `assets.openFd`, which works for
install-time packs once the pack name prefix is accounted for. Expect a focused day of work
plus a Play internal-test cycle.

## Option B — ship the signed APK outside Play

The signed release APK produced by `assembleRelease` is a complete, working app with every
asset bundled. It can be distributed directly (your own site, an MDM, a beta group) with no
size limit from Play. It cannot be listed on Google Play.

## Option C — trim the payload, then use a normal AAB

If Play listing is the priority and asset packs are out of reach, the media can be reduced so
the base module fits under 200 MB:

- drop the example-sentence recordings and keep only the 7,298 headword pronunciations
  (~40 MB instead of 209 MB), or
- ship images at 256 px instead of 512 px (~50 MB instead of 185 MB), or
- both, for a comfortable ~90 MB base.

This changes the product promise ("22,905 recordings"), so the store copy would need to match.

## Current artifacts

| File | Notes |
| --- | --- |
| `app/app/build/outputs/apk/release/app-release.apk` | Signed with `keystore/verbilume-release.jks`, R8 enabled. Complete app, Play-asset-pack free. |
| `app/app/build/outputs/bundle/release/app-release.aab` | Signed bundle, correct for Play tooling — but too large for the 200 MB base-module limit until Option A or C is applied. |

## Signing

| Item | Value |
| --- | --- |
| Keystore | `keystore/verbilume-release.jks` |
| Alias | `verbilume` |
| Key | RSA 4096, self-signed, 30-year validity |
| Credentials file | `keystore.properties` (project root) |

> **Back these two files up now.** Google Play ties every future update of an app to the key it
> was first uploaded with. If the keystore is lost, the app can never be updated again under the
> same listing. Keep an offline copy somewhere you will still have in five years.

For Play App Signing, enrol in it before the first upload; Google will hold the app signing key
and this upload key can be rotated if it is ever compromised.