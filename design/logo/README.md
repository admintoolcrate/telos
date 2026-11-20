# The Telos mark

An open ring around a point (option E in `alternatives.png`), in the ToolCrate family style
(`docs/family-look.md`). *Telos* is the end a thing
aims at: the point. The ring is left open because the work is still on its way there.

- Drawn in the 108-unit adaptive-icon canvas: an arc of radius 24 centred on (54, 54), from
  (66, 33.2) round to (77.5, 49), stroke 3 with round caps, leaving a gap at the upper right;
  and a point of radius 5.5 at the centre. The status-bar version uses stroke 5 and a point of
  6.5 so it holds at 24dp.
- Colours: `#FAFAF8` on ink `#111111` (launcher) or `#0F0F0F` (Play), like Stagira's tally.

| File | Use |
|---|---|
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Launcher icon (ink background from `@color/launcher_background`) |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | Android 13+ themed icons |
| `app/src/main/res/drawable/ic_notification.xml` | Status-bar icon |
| `play-icon-512.png` | Google Play app icon (512 × 512, full bleed; Play applies the mask) |
| `play-feature-1024x500.png` | Google Play feature graphic (both from `tools/family_graphics.py`) |
| `alternatives.png` | The options that were considered |
