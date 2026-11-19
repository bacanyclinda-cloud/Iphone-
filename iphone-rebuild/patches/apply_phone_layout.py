"""Reproducible, fail-fast phone dock & wallpaper changes for Folio v0.6.7.3 (MIT).
Run: python3 iphone_native_v2_patch.py path/to/native-source
Does not rename/remove any original author or license notices.
"""
from pathlib import Path
import sys
root = Path(sys.argv[1]) if len(sys.argv)>1 else Path('/mnt/data/iphone_v2_work')
base = root/'app/src/main/java/com/mccal/folio'

def patch(name, before, after, count=1):
    path = base/name
    text = path.read_text()
    got = text.count(before)
    if got!=count:
        raise RuntimeError(f'Patch mismatch {name}: expected {count}, found {got} for {before[:95]!r}')
    path.write_text(text.replace(before,after))

# Only portrait phones get the iPhone-style horizontal dock; landscape and tiny split windows retain rail.
patch('LayoutModel.kt', 'DockPlacement.AUTOMATIC -> tallRegular',
      'DockPlacement.AUTOMATIC -> tallRegular || (height > width && height >= HOME_REGULAR_MIN_HEIGHT_DP)')
patch('LayoutModel.kt', 'val dockBesideRail = horizontalDock && !tallRegular',
      'val dockBesideRail = false // no status rail alongside a portrait bottom dock')
patch('LayoutModel.kt', 'var gridWidth = if (tallRegular && horizontalDock)',
      'var gridWidth = if (horizontalDock && !tallRegular) minOf(width - 32f, 4f * p.iconSize * 1.55f).coerceAtLeast(192f)\n        else if (tallRegular && horizontalDock)')

# The phone now gets the full width for home screens; Android's genuine status icons remain on top.
patch('LauncherScreen.kt', 'if (state.verticalStatus) StatusRail(deviceStatus,',
      'if (state.verticalStatus && !geometry.horizontalDock) StatusRail(deviceStatus,')
# Move bottom bar down; put home search/page control *above* bar like iOS.
patch('LauncherScreen.kt', '.padding(bottom = controlsSpace + FolioSpace.SMALL.dp)\n                    .width(dockBarWidth)',
      '.padding(bottom = if (geometry.horizontalDock && !geometry.expanded) FolioSpace.MEDIUM.dp else controlsSpace + FolioSpace.SMALL.dp)\n                    .width(dockBarWidth)')
patch('LauncherScreen.kt', 'end = if (state.leftHanded) besideHome + FolioSpace.LARGE.dp else 0.dp, bottom = FolioSpace.SNUG.dp), horizontalAlignment',
      'end = if (state.leftHanded) besideHome + FolioSpace.LARGE.dp else 0.dp,\n                    bottom = if (geometry.horizontalDock && !geometry.expanded) (geometry.dockBarHeight + 16f).dp else FolioSpace.SNUG.dp), horizontalAlignment')
patch('LauncherScreen.kt', '.background(Glass.copy(alpha = state.statusStyle.railGlass), RoundedCornerShape(30.dp))',
      '.background(Glass.copy(alpha = if (geometry.horizontalDock) maxOf(.56f, state.statusStyle.railGlass) else state.statusStyle.railGlass), RoundedCornerShape(30.dp))')
# Make the automatic setting description accurate for first-time and upgrade installs.
patch('CustomizationSheet.kt', 'DockPlacement.AUTOMATIC -> if (wide) stringResource(R.string.the_dock_stays_on_the_side_bar_and_moves) else stringResource(R.string.the_dock_stays_on_the_side_bar)',
      'DockPlacement.AUTOMATIC -> "Portrait phone: bottom glass dock. Landscape: side dock."')

# Gallery additions: licensed original new artwork, no uncredited image source.
wallpapers = [
    ('alpine-dawn','Alpine Dawn'), ('aurora-fjord','Aurora Fjord'), ('violet-liquid','Violet Liquid'), ('golden-coast','Golden Coast'),
    ('blue-hour-lake','Blue Hour Lake'), ('northern-lights','Northern Lights'), ('sapphire-glass','Sapphire Glass'), ('cliffside-sunset','Cliffside Sunset'),
    ('mirror-mountain','Mirror Mountain'), ('arctic-night','Arctic Night'), ('lavender-glass','Lavender Glass'), ('ocean-waves','Ocean Waves'),
    ('evergreen-lake','Evergreen Lake'), ('polar-glow','Polar Glow'), ('crystal-ribbons','Crystal Ribbons'), ('sunlit-cliffs','Sunlit Cliffs'),
]
items = ''.join(f'''        Artwork(\n            id = "{id}", title = "{title}",\n            artist = "iPhone Native Rebuild", license = "Original AI-assisted artwork",\n            detail = "Made for this Android launcher, 2026.", builtIn = true,\n        ),\n''' for id,title in wallpapers)
patch('BackgroundLibrary.kt', '    private val BUILT_IN = listOf(\n', '    private val BUILT_IN = listOf(\n'+items)
# Only replace an unselected legacy gray background once (never overwrite user's chosen wallpaper).
patch('BackgroundLibrary.kt', '''    if (saved == null) {
        val hadPhoto = prefs.getBoolean("photoEnabled", false) && launcherPhotoFile(context).isFile
        val migrated = if (hadPhoto) BackgroundChoice.Photo else BackgroundChoice.None
''', '''    if (saved == null) {
        val hadPhoto = prefs.getBoolean("photoEnabled", false) && launcherPhotoFile(context).isFile
        val migrated = if (hadPhoto) BackgroundChoice.Photo else BackgroundChoice.Art("alpine-dawn")
''')
patch('BackgroundLibrary.kt', '''    // exists(), not artFile().isFile: art that ships with Folio is an asset inside the APK and has no file on
    // disk, so testing for a file read every built-in choice straight back as "none" and Home fell through to the
    // default. Caught by picking one on a phone, which is the only place it showed.
    return BackgroundChoice.parse(saved) { id -> BackgroundLibrary.exists(context, id) }
''', '''    // Migrate only a gray/empty Home from the previous test build; preserve any selected photo or artwork.
    // The user's Android system wallpaper preference remains independent of Folio's own background choice.
    if (saved == BackgroundChoice.NONE && !prefs.getBoolean("native_phone_v2_gray_migrated", false)) {
        prefs.edit { putBoolean("native_phone_v2_gray_migrated", true) }
        val updated = BackgroundChoice.Art("alpine-dawn")
        prefs.edit { putString(BACKGROUND_CHOICE, updated.save()) }
        return updated
    }
    return BackgroundChoice.parse(saved) { id -> BackgroundLibrary.exists(context, id) }
''')

# Make new installs use a slightly less intrusive glass tint and sharper glass outlines.
patch('LauncherModel.kt', 'val glassTint: Float = .5f,', 'val glassTint: Float = .38f,')
patch('LauncherModel.kt', 'val glassOutline: Float = .16f,', 'val glassOutline: Float = .24f,')
print(f'Patched native phone dock and wallpaper gallery at {root}')