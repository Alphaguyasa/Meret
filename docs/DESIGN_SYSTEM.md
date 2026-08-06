# DESIGN_SYSTEM.md

## Design premise

This app is used outdoors, in direct equatorial sun, one-handed, sometimes by someone
wearing gloves, on a device with a scratched screen and 30% battery, by a person who has
been walking for six hours. Every decision follows from that.

It is not a consumer app. Restraint reads as competence here.

---

## Field usability rules

1. **Minimum touch target 56 dp** for any control used while walking. 48 dp is the
   Android floor; it is not enough with one hand and no attention to spare.
2. **Primary actions in the bottom third.** Thumb reach on a 6.7" phone held one-handed.
3. **Never rely on colour alone.** Sunlight washes out hue before it washes out
   luminance. Pair every colour signal with an icon or text.
4. **No destructive action without an undo window.** Stopping a track, deleting a
   waypoint — 5-second snackbar undo, no confirmation dialog. Dialogs get dismissed
   reflexively; undo does not.
5. **Every state is legible at arm's length.** Track status, GPS quality, and sync state
   must be readable without focusing.
6. **Amharic first-class.** Ethiopic has a taller x-height and needs more line height.
   Test every screen in `am` at the largest font scale before calling it done.
7. **Show the number.** Field workers do not trust progress bars. Show "1,204 points ·
   4.8 km · 01:42" not a spinner.

---

## Tokens

### Colour

Light theme is the default and the one that matters — nobody surveys at night. Dark theme
exists for evening data review.

```
Brand
  primary          #1B5E4A   deep green, "meret" — land
  onPrimary        #FFFFFF
  primaryContainer #C8E6DA
  secondary        #B45309   ochre, Ethiopian earth
  tertiary         #1E3A5F   deep blue, water/admin boundaries

Semantic — status
  tracking         #16A34A   actively recording
  paused           #D97706
  stopped          #64748B
  error            #DC2626

Semantic — GPS quality  (always paired with a bar icon)
  gpsExcellent     #16A34A   accuracy ≤ 5 m
  gpsGood          #65A30D   ≤ 10 m
  gpsFair          #D97706   ≤ 25 m
  gpsPoor          #DC2626   > 25 m or no fix

Semantic — sync
  syncPending      #64748B
  syncActive       #2563EB
  syncDone         #16A34A
  syncConflict     #B45309
  syncFailed       #DC2626

Map overlay
  trackActive      #16A34A   4 dp, round cap
  trackHistoric    #1E3A5F   3 dp, 60% opacity
  trackPaused      dashed, #94A3B8
  waypoint         #B45309
  assignment       #1E3A5F   1.5 dp outline, 12% fill
  coverage         #16A34A   25% fill
  measureVertex    #DC2626
```

Map overlays need a 1 dp white halo or a subtle outer stroke — a green line on a green
landcover tile is invisible.

### Typography

Inter for Latin, Noto Sans Ethiopic for Amharic, in one `FontFamily` stack so mixed
strings render consistently.

```
displaySmall   32 / 40   w600   big metrics on the tracking screen
headlineSmall  24 / 32   w600   screen titles
titleMedium    16 / 24   w600   list items, cards
bodyLarge      16 / 26   w400   default body — 26 not 24, for Ethiopic
bodyMedium     14 / 22   w400
labelLarge     14 / 20   w600   buttons
metricLarge    28 / 34   w700   tabular figures — distance, duration
metricSmall    18 / 24   w600   tabular figures
```

Use `FontFeature` tabular numerals for anything that ticks upward, or the layout jitters
once per second for eight hours.

### Spacing and shape

4 dp base scale: 4, 8, 12, 16, 24, 32, 48.
Corner radii: 8 dp cards, 12 dp sheets, 16 dp dialogs, full for pills and FABs.
Elevation: flat surfaces, 1 dp cards, 3 dp bottom sheets, 6 dp FAB. Nothing higher.

---

## Key components

**`GpsQualityChip`** — icon + accuracy in metres + satellite count. Tap opens
diagnostics. Persistent on the map screen.

**`TrackingBar`** — bottom bar on the map screen. Collapsed: status dot, duration,
distance, expand chevron. Expanded: point count, current accuracy, segment count,
pause/resume, stop, add waypoint.

**`RecordButton`** — 72 dp FAB. Idle → filled green with a record glyph. Recording →
pulsing ring, 1 s period, but the pulse stops when the screen is on and battery is low.
Long-press to stop, tap to pause. Long-press for the destructive action is deliberate.

**`SyncBadge`** — pending count, spinner while active, conflict count if any. Tapping
opens the sync screen. Never blocks anything.

**`WaypointSheet`** — bottom sheet, expands to full height for long forms. Photo strip at
the top, category chips, then form fields. Saves a draft on every field change so a
process death mid-form loses nothing.

**`MeasurementOverlay`** — vertex markers, live perimeter and area, unit toggle
(m² / hectare / *timad*), undo-last-vertex, close-polygon.

**`OfflineIndicator`** — a slim persistent strip when there is no network. Neutral grey,
not red. Offline is the expected state, not an error, and styling it as a failure
undermines the entire product promise.

---

## Screen inventory

| Screen | Notes |
|---|---|
| Map | The home screen. Everything else is reachable from here. |
| Track list | Grouped by day, with distance and sync state |
| Track detail | Map, stats, elevation profile, segment list, export |
| Waypoint capture | Bottom sheet over the map |
| Waypoint list | Filter by category and sync state |
| Measure | Full-screen map with the measurement overlay |
| Tile packs | Installed packs, import, download region, storage used |
| Sync | Outbox depth, conflicts, manual sync, peer sync |
| Assignments | List + map, coverage percentage per area |
| Settings | Profile, sampling, units, language, filters |
| Onboarding | Permissions, OEM battery setup, tile pack install |
| Diagnostics | The screen that saves you |

## Accessibility

- `contentDescription` on every icon-only control
- Support font scale to 200% — the tracking bar must reflow, not clip
- Minimum contrast 4.5:1 for body, 3:1 for large text; check the ochre and green against
  their containers specifically
- Announce tracking start/stop and GPS quality changes via accessibility live regions
