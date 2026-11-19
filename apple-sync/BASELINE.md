# Baseline (the epoch)

Recorded 2026-11-18 by the read-only audit on the real account, before any code in this fork could
write anything.

| | |
|---|---|
| Storefront | `in` |
| Songs in the library | 1490 |
| Library playlists | 26 |
| Objects recorded in the epoch | 1516 (1490 songs + 26 playlists) |
| Audit result | unchanged, `trusted = true` |
| Where it lives | `epoch` table in `apple_sync.db` on the device |

Everything in that set is immutable to this app: `AppleGuard` refuses to delete, rename or remove any
object whose `kind:id` appears in the epoch, whatever else it thinks it owns. The audit re-reads the
account and reports anything missing from it.

When the app is reinstalled, `apple_sync.db` is gone and the next audit takes a new baseline. That is
deliberate: a baseline taken after a reinstall still protects the library as it is at that moment, but
it cannot know what was there before, so it should be re-taken before any test that writes.
