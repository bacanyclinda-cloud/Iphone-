"""LoopPhone-focused native usability patch for MIT-licensed Folio v0.6.7.3.
Apply *after* iphone native v2 phone layout patch.
Native Android only: no WebView UI, no system shade suppression without Device Owner.
Never overwrites a manually customized widget layout or the existing app order.
"""
from pathlib import Path
import sys
root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('/mnt/data/iphone_v2_work')
p = root/'app/src/main/java/com/mccal/folio'

def replace(name, old, new, count=1):
    f = p/name
    data=f.read_text()
    got=data.count(old)
    if got != count:
        raise RuntimeError(f'{name}: expected {count}, found {got}: {old[:100]}')
    f.write_text(data.replace(old,new))

# Comfortable launch experience, preserving third-party app icons while applying iPhone-style rounded geometry.
replace('LauncherModel.kt', 'val iconShape: IconShape = IconShape.DEFAULT,', 'val iconShape: IconShape = IconShape.SQUIRCLE,')
replace('LauncherModel.kt', 'val addNewAppsToHome: Boolean = false,', 'val addNewAppsToHome: Boolean = true,')
replace('LauncherModel.kt', 'addNewAppsToHome = j.optBoolean("addNewAppsToHome", false),', 'addNewAppsToHome = j.optBoolean("addNewAppsToHome", true),')
# Stronger legibility on a light mountain wallpaper without harsh opaque cards.
replace('LauncherModel.kt','val glassOutline: Float = .24f,','val glassOutline: Float = .29f,')

# Do not place the home grid in the vertical center of a tall phone while wasting hundreds of pixels.
replace('LayoutModel.kt',
'''val contentTop = if (p.pageTop) 16f else ((height - pageHeight - homeBottomSpace) / 2f).coerceIn(16f, maxOf(72f, height * .18f))''',
'''val contentTop = if (horizontalDock && !expanded) 48f  // room for genuine time, signal and battery at top
        else if (p.pageTop) 16f else ((height - pageHeight - homeBottomSpace) / 2f).coerceIn(16f, maxOf(72f, height * .18f))''')

# Automatically fill Home with installed apps. Preserve old order, dock and real user-added widgets.
replace('LauncherModel.kt',
'''                    val homeArrivals = (if (old.addNewAppsToHome) arrivals else emptyList()) + newShortcuts''',
'''                    // One-time migration: fill previously sparse Home pages instead of hiding most apps in Library.
                    // Existing app order and all user-custom widgets are preserved. Only Folio's two stock
                    // clock/date placeholders are removed to make room for the first 8 icons on small phones.
                    val seedAllApps = !prefs.getBoolean("iphone_v3_home_seeded", false)
                    val stockWidgetsOnly = seedAllApps && old.widgetPlacements == DEFAULT_WIDGET_PLACEMENTS
                    val homeCandidates = if (seedAllApps) entries.filter { !it.isWork && !it.isShortcut && it.available &&
                        it.id !in old.hiddenApps && it.id !in dock && it.id !in old.leadingSlots &&
                        old.folders.none { folder -> it.id in folder.appIds } } else emptyList()
                    val homeArrivals = (if (old.addNewAppsToHome) arrivals else emptyList()) + newShortcuts + homeCandidates''')
replace('LauncherModel.kt',
'''                    val blockedForArrivals = (old.widgetPlacements.filter { it.page >= 0 }.flatMap { it.coveredIndices() } + hiddenCells).toSet()
                    val withArrivals = homeArrivals.fold(pins)''',
'''                    val blockedForArrivals = ((if (stockWidgetsOnly) emptyList() else old.widgetPlacements.filter { it.page >= 0 }.flatMap { it.coveredIndices() }) + hiddenCells).toSet()
                    val compactHomePins = if (stockWidgetsOnly) pins.filterNotNull() else pins
                    val withArrivals = homeArrivals.fold(compactHomePins)''')
replace('LauncherModel.kt',
'''old.copy(apps = entries, profiles = profiles, homeSlots = reconciled.slots, leadingSlots = reconciled.leadingSlots,
                        dock = reconciled.dock, folders = reconciled.folders,''',
'''old.copy(apps = entries, profiles = profiles, homeSlots = reconciled.slots, leadingSlots = reconciled.leadingSlots,
                        dock = reconciled.dock, folders = reconciled.folders,
                        widgetPlacements = if (stockWidgetsOnly) emptyList() else old.widgetPlacements,
                        addNewAppsToHome = true,
                        iconShape = if (seedAllApps && old.iconShape == IconShape.DEFAULT) IconShape.SQUIRCLE else old.iconShape,''')
replace('LauncherModel.kt',
'''                needsMigration = false
                persist()''',
'''                needsMigration = false
                persist()
                if (!prefs.getBoolean("iphone_v3_home_seeded", false))
                    prefs.edit().putBoolean("iphone_v3_home_seeded", true).apply()''')

# Prefer real reported camera cutout, never a stale manual position on a portrait phone.
replace('CutoutIsland.kt',
'''val geometry = custom?.clamped(windowWidth, windowHeight, density.density, cameraGeometry.pillH)
            ?.let { islandGeometryAt(it.xFraction * windowWidth, it.topDp, windowWidth, density.density) } ?: cameraGeometry''',
'''val geometry = custom?.takeIf { cutout == null || landscape || wide }
            ?.clamped(windowWidth, windowHeight, density.density, cameraGeometry.pillH)
            ?.let { islandGeometryAt(it.xFraction * windowWidth, it.topDp, windowWidth, density.density) } ?: cameraGeometry''')
replace('EverywhereOverlay.kt',
'''val g = (IslandPosition.load(service, wide, metrics.bounds.width() > metrics.bounds.height())?.let { islandGeometryAt(it.xFraction * metrics.bounds.width(), it.topDp, metrics.bounds.width(), density) }
            ?: islandGeometry(cutout, metrics.bounds.width(), density)).also { geometry = it }''',
'''val g = (IslandPosition.load(service, wide, metrics.bounds.width() > metrics.bounds.height())
            ?.takeIf { cutout == null || wide || metrics.bounds.width() > metrics.bounds.height() }
            ?.let { islandGeometryAt(it.xFraction * metrics.bounds.width(), it.topDp, metrics.bounds.width(), density) }
            ?: islandGeometry(cutout, metrics.bounds.width(), density)).also { geometry = it }''')
# Avoid a too-narrow pill on devices with a large OEM camera-safe rectangle, and cap width to safe edges.
replace('CutoutIsland.kt',
'''val camW = (cutoutLtrb?.let { it[2] - it[0] } ?: 0) / density''',
'''val camW = (cutoutLtrb?.let { (it[2] - it[0]).coerceAtLeast(0) } ?: 0) / density''')
replace('CutoutIsland.kt',
'''val pillH = maxOf(camBottom + ISLAND_CAMERA_MARGIN - top, ISLAND_MIN_HEIGHT)''',
'''val pillH = maxOf(camBottom + ISLAND_CAMERA_MARGIN - top, ISLAND_MIN_HEIGHT).coerceAtMost(52f)''')

# The original status rail disappears on a portrait bottom dock. Replace its info with a real, legible native top row.
replace('LauncherScreen.kt',
'''            if (state.verticalStatus && !geometry.horizontalDock) StatusRail(deviceStatus,''',
'''            if (geometry.horizontalDock && !geometry.expanded) LoopPhoneNativeStatus(deviceStatus,
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp))
            if (state.verticalStatus && !geometry.horizontalDock) StatusRail(deviceStatus,''')
# Permanent discoverable shortcuts to real native Control Center and Notification Center on Home.
replace('LauncherScreen.kt',
'''                    IconButton(onClick = openLibrary, Modifier.size(32.dp).testTag("library-page-link")) {''',
'''                    if (geometry.horizontalDock && !geometry.expanded) {
                        IconButton(onClick = { launcherActivity.openSystemShade(ShadePanel.NOTIFICATIONS) },
                            Modifier.size(32.dp).testTag("phone-notifications-shortcut")) {
                            Icon(Icons.Rounded.Notifications, "Notifications", tint = LocalHomeInk.current.primary, modifier = Modifier.size(19.dp))
                        }
                        IconButton(onClick = { launcherActivity.openSystemShade(ShadePanel.QUICK_SETTINGS) },
                            Modifier.size(32.dp).testTag("phone-control-center-shortcut")) {
                            Icon(Icons.Rounded.Tune, "Control Centre", tint = LocalHomeInk.current.primary, modifier = Modifier.size(19.dp))
                        }
                    }
                    IconButton(onClick = openLibrary, Modifier.size(32.dp).testTag("library-page-link")) {''')

status_fn='''
/** Portrait LoopPhone Home: compact status, not a second Android navigation or notification bar.
 * Drawn below Android's real display-cutout inset; always uses real battery/network state.
 * Android navigation bar is never hidden or reimplemented. */
@Composable
private fun LoopPhoneNativeStatus(status: DeviceStatus, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = displayNow(rememberMinuteTick().value)
    val fmt = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val time = now.format(java.time.format.DateTimeFormatter.ofPattern(fmt))
    val ink = LocalHomeInk.current.primary
    val capsule = RoundedCornerShape(16.dp)
    val glass = Glass.copy(alpha = .44f)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(time, color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.background(glass, capsule).padding(horizontal = 11.dp, vertical = 5.dp))
        Row(Modifier.background(glass, capsule).padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (status.airplane) Icon(Icons.Rounded.AirplanemodeActive, "Airplane mode", tint = ink, modifier = Modifier.size(15.dp))
            else Icon(Icons.Rounded.SignalCellularAlt, "Cellular signal", tint = ink, modifier = Modifier.size(15.dp))
            if (status.wifiConnected) Icon(Icons.Rounded.Wifi, "Wi-Fi connected", tint = ink, modifier = Modifier.size(15.dp))
            Text("${status.battery?.toString() ?: "--"}%", color = ink,
                fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Icon(if (status.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
                "Battery", tint = ink, modifier = Modifier.size(17.dp))
        }
    }
}
'''
with (p/'LauncherScreen.kt').open('a') as f: f.write(status_fn)

# The custom shade stays accessible. Only Device Owner apps can legally disable OS-wide Quick Settings;
# do not claim that capability or break emergency/navigation controls in this APK.
print('LoopPhone V3: complete native defaults, auto camera alignment, full Home pages and reliable status/controls')