package za.co.velocity.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Real HOME activity. Icons, apps, battery, notifications, wallpaper and torch use
 * Android APIs rather than fake UI data. The custom notification panel does not
 * replace Android SystemUI globally; that is an OS privilege boundary.
 */
public final class LauncherActivity extends Activity {
    private static final int REQUEST_GALLERY = 100;
    private static final int REQUEST_CAMERA = 101;
    private static final String PREFS = "velocity_prefs";
    private static final String[] THEMES = {
        "Original device wallpaper", "Midnight blue", "Graphite", "Aurora", "Dusk", "Ocean",
        "Sandstone", "Amethyst", "Deep green", "Steel", "Copper", "Monochrome",
        "Blue Prism", "Violet Glass", "Blue Horizon", "Crystal Waves", "Midnight Sapphire",
        "Purple Haze", "Azure Drift", "Sky Blue", "Pearl Blue", "Royal Indigo",
        "Aqua Glow", "Cosmic Violet"
    };
    // Downloadable HTTPS photos; downloaded and cached only when the user chooses one.
    private static final String[] PHOTO_IDS = {
        "photo-1503376780353-7e6692767b70",
        "photo-1492144534655-ae79c964c9d7",
        "photo-1555215695-3004980ad54e",
        "photo-1511919884226-fd3cad34687c",
        "photo-1493238792000-8113da705763",
        "photo-1507136566006-cfc505b114fc",
        "photo-1533473359331-0135ef1b58bf",
        "photo-1549317661-bd32c8ce0db2",
        "photo-1519608487953-e999c86e7455",
        "photo-1519641471654-76ce0107ad1b"
    };

    private SharedPreferences preferences;
    private TouchRoot root;
    private ImageView wallpaper;
    private FrameLayout island, panel, setupLayer;
    private ControlCenterOverlay controlCenter;
    private TextView islandLabel, statusTime, statusBattery, widgetTime, widgetBattery, cutoffLabel;
    private LinearLayout panelContent;
    private SnapPager pager;
    private final List<ResolveInfo> installed = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Rect physicalCutout = new Rect();
    private boolean foundCutout = false, islandExpanded = false, torchEnabled = false;
    private boolean receiversRegistered = false;
    private int batteryPercent = 0;

    private final BroadcastReceiver events = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                batteryPercent = scale == 0 ? level : (level * 100 / scale);
                refreshStatus();
            } else if (NotificationBridge.ACTION_CHANGED.equals(intent.getAction())) {
                refreshIsland();
                if (panel != null && panel.getVisibility() == View.VISIBLE) renderNotifications();
            }
        }
    };

    private int d(float dp) { return Math.round(getResources().getDisplayMetrics().density * dp); }
    private int white(int a) { return Color.argb(a, 255, 255, 255); }
    private int parse(String hex) { return Color.parseColor(hex); }
    private FrameLayout.LayoutParams fl(int w, int h, int gravity) { return new FrameLayout.LayoutParams(w, h, gravity); }
    private LinearLayout.LayoutParams ll(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable b = new GradientDrawable();
        b.setColor(color); b.setCornerRadius(d(radius));
        if (stroke != 0) b.setStroke(d(1), stroke);
        return b;
    }
    private GradientDrawable gradient(int from, int to, int radius) {
        GradientDrawable b = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{from,to});
        b.setCornerRadius(d(radius)); return b;
    }
    private TextView text(String s, int size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(color); t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        t.setGravity(Gravity.CENTER_VERTICAL); t.setIncludeFontPadding(false); return t;
    }
    private TextView action(String label, View.OnClickListener listener) {
        TextView t = text(label, 14, true, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setPadding(d(14), d(13), d(14), d(13));
        t.setBackground(shape(parse("#344A64"), 15, white(35)));
        t.setOnClickListener(listener);
        return t;
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private void addSpace(LinearLayout c, int heightDp) { c.addView(new View(this), ll(1,d(heightDp))); }
    private TextView heading(String s) { return text(s, 23, true, Color.WHITE); }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(parse("#0C1426"));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        hideLauncherStatusBar();
        readInstalledApps();
        createUi();
        registerEvents();
        if (!preferences.getBoolean("setupComplete", false)) showFirstRun();
    }

    private void hideLauncherStatusBar() {
        int flags = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        getWindow().getDecorView().setSystemUiVisibility(flags); // Navigation bar intentionally NOT hidden.
        if (Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) {
            getWindow().getInsetsController().hide(WindowInsets.Type.statusBars());
        }
    }

    @Override protected void onResume() {
        super.onResume(); hideLauncherStatusBar();
        if (wallpaper != null) renderWallpaper();
        refreshStatus(); refreshIsland();
    }
    @Override protected void onDestroy() {
        if (receiversRegistered) { try { unregisterReceiver(events); } catch (Exception ignored) {} }
        handler.removeCallbacksAndMessages(null); super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (setupLayer != null && setupLayer.getVisibility() == View.VISIBLE) return;
        if (controlCenter != null && controlCenter.opened()) { controlCenter.close(); return; }
        if (panel != null && panel.getVisibility() == View.VISIBLE) { panel.setVisibility(View.GONE); return; }
        if (pager != null) pager.goTo(0);
    }
    private void registerEvents() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(NotificationBridge.ACTION_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(events,f,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(events,f);
        receiversRegistered = true;
        Intent battery = registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) events.onReceive(this,battery);
    }

    private void readInstalledApps() {
        installed.clear(); PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_LAUNCHER);
        try { installed.addAll(pm.queryIntentActivities(i,0)); }
        catch (Exception e) { toast("Cannot query installed apps"); }
        Collections.sort(installed, (a,b) ->
                a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
        Set<String> seen = new HashSet<>();
        installed.removeIf(info -> info.activityInfo == null ||
                !seen.add(info.activityInfo.packageName + "/" + info.activityInfo.name) ||
                getPackageName().equals(info.activityInfo.packageName));
    }

    private void launchApp(ResolveInfo app) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(app.activityInfo.packageName,app.activityInfo.name));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
        } catch (Exception e) { toast("Could not open " + app.loadLabel(getPackageManager())); }
    }

    private View appTile(ResolveInfo app) {
        LinearLayout item=column();item.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        int iconDp=preferences.getInt("iconSize",60);
        iconDp=Math.max(48,Math.min(68,iconDp));
        FrameLayout holder=new FrameLayout(this);
        holder.setBackground(shape(0x22FFFFFF,Math.round(iconDp*.23f),0));
        holder.setClipToOutline(true);
        ImageView image=new ImageView(this);
        try{image.setImageDrawable(app.loadIcon(getPackageManager()));}catch(Exception ignored){}
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        String style=preferences.getString("iconStyle","original");
        if("mono".equals(style)) {
            ColorMatrix matrix=new ColorMatrix();matrix.setSaturation(0f);
            image.setColorFilter(new ColorMatrixColorFilter(matrix));
            holder.setBackground(shape(0xB91C2B43,Math.round(iconDp*.23f),0x22FFFFFF));
        }else if("clear".equals(style)){
            holder.setBackground(shape(0x40FFFFFF,Math.round(iconDp*.23f),0x44FFFFFF));
            holder.setAlpha(.94f);
        }
        holder.addView(image,new FrameLayout.LayoutParams(-1,-1));
        item.addView(holder,ll(d(iconDp),d(iconDp)));
        TextView label=text(app.loadLabel(getPackageManager()).toString(),11,false,Color.WHITE);
        label.setGravity(Gravity.CENTER);label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setShadowLayer(d(3),0,d(1),0xC8000000);
        LinearLayout.LayoutParams nameP=ll(-1,d(20));nameP.topMargin=d(5);
        item.addView(label,nameP);
        item.setContentDescription("Open "+app.loadLabel(getPackageManager()));
        item.setOnClickListener(v->launchApp(app));
        item.setOnLongClickListener(v->{
            new android.app.AlertDialog.Builder(this)
                .setTitle(app.loadLabel(getPackageManager()))
                .setItems(new String[]{"Open","App info"},(dialog,which)->{
                    if(which==0)launchApp(app);
                    else try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:"+app.activityInfo.packageName)));}catch(Exception ignored){}
                }).show();return true;
        });
        return item;
    }

    private GridLayout iconsGrid(List<ResolveInfo> data){
        GridLayout grid=new GridLayout(this);grid.setColumnCount(4);grid.setUseDefaultMargins(false);
        int screenWidth=getResources().getDisplayMetrics().widthPixels;
        int width=Math.max(d(67),(screenWidth-d(28))/4);
        for(ResolveInfo r:data){
            GridLayout.LayoutParams params=new GridLayout.LayoutParams();
            params.width=width;params.height=d(preferences.getInt("iconSize",60)+37);
            params.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
            grid.addView(appTile(r),params);
        }
        return grid;
    }

    private void createUi() {
        root = new TouchRoot(this);
        setContentView(root);
        wallpaper = new ImageView(this); wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper,fl(-1,-1,Gravity.FILL)); renderWallpaper();
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{
                parse("#A600050C"),parse("#1200060D"),parse("#3000060D"),parse("#C6000308")}));
        root.addView(shade,fl(-1,-1,Gravity.FILL));

        pager = new SnapPager(this);
        root.addView(pager,fl(-1,-1,Gravity.FILL));
        LinearLayout pages = new LinearLayout(this); pages.setOrientation(LinearLayout.HORIZONTAL);
        pager.addView(pages,new HorizontalScrollView.LayoutParams(-2,-1));
        int pageWidth = getResources().getDisplayMetrics().widthPixels;
        pages.addView(homePage(),ll(pageWidth,-1));
        pages.addView(appsPage(),ll(pageWidth,-1));
        pages.addView(garagePage(),ll(pageWidth,-1));
        pages.addView(settingsPage(),ll(pageWidth,-1));
        buildDock(); buildStatusBar(); buildIsland();
        panel = new FrameLayout(this); panel.setVisibility(View.GONE);
        root.addView(panel,fl(-1,-1,Gravity.FILL));
        controlCenter = new ControlCenterOverlay(this,new ControlCenterOverlay.Callbacks() {
            @Override public void onTorch() { toggleTorch(); }
            @Override public boolean isTorchOn() { return torchEnabled; }
            @Override public void onNotifications() { showNotifications(); }
            @Override public void onOpenMusic() { openMusicApp(); }
            @Override public void onAndroidSettings() { openSystemSettings(); }
        });
        root.addView(controlCenter,fl(-1,-1,Gravity.FILL));
        setupLayer = new FrameLayout(this); setupLayer.setVisibility(View.GONE);
        root.addView(setupLayer,fl(-1,-1,Gravity.FILL));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if (Build.VERSION.SDK_INT >= 28) {
                android.view.DisplayCutout cut = insets.getDisplayCutout();
                Rect candidate = null;
                if (cut != null) for (Rect r : cut.getBoundingRects()) {
                    if (r.centerY() < d(120)) {
                        if (candidate == null || r.width()>candidate.width()) candidate=r;
                    }
                }
                foundCutout = candidate != null;
                if (foundCutout) physicalCutout.set(candidate);
            }
            root.post(this::alignIsland);
            return insets;
        });
        root.addOnLayoutChangeListener((v,a,b,c,d,e,f,g,h)->alignIsland());
    }

    private LinearLayout pageBase() {
        LinearLayout content=column(); content.setPadding(d(21),d(77),d(21),d(134));
        return content;
    }
    private View scroll(LinearLayout body) {
        ScrollView s = new ScrollView(this); s.setFillViewport(false); s.setVerticalScrollBarEnabled(false);
        s.addView(body,new ScrollView.LayoutParams(-1,-2)); return s;
    }
    private View homePage() {
        LinearLayout body=pageBase();
        body.setPadding(d(13),d(70),d(13),d(130));
        // The launcher's home screen follows the familiar four-column iPhone spacing.
        // No fake logos or cartoon illustrations: each app uses its real package icon.
        LinearLayout top=row();top.setPadding(d(8),0,d(8),0);
        TextView date=text(new SimpleDateFormat("EEEE  d MMMM",Locale.getDefault()).format(new Date()),14,true,Color.WHITE);
        date.setShadowLayer(d(2),0,d(1),0xBB000000);
        top.addView(date,new LinearLayout.LayoutParams(0,d(25),1));
        TextView edit=text("Edit",13,true,Color.WHITE);
        edit.setPadding(d(12),d(6),d(12),d(6));
        edit.setGravity(Gravity.CENTER);
        edit.setBackground(shape(0x44FFFFFF,18,0x33FFFFFF));
        edit.setOnClickListener(v->pager.goTo(3));top.addView(edit);
        body.addView(top);
        addSpace(body,13);
        if(preferences.getBoolean("showWidget",true)){
            LinearLayout card=column();card.setPadding(d(19),d(15),d(19),d(13));
            card.setBackground(gradient(0xA944566E,0x9745517E,24));
            LinearLayout first=row();
            TextView title=text("BATTERIES",12,true,Color.WHITE);title.setLetterSpacing(.07f);
            first.addView(title,new LinearLayout.LayoutParams(0,d(27),1));
            TextView icon=text("◉",21,false,Color.WHITE);first.addView(icon);
            card.addView(first);
            LinearLayout batteryRow=row();
            TextView batteryIcon=text("▰",37,true,0xFF78EA9F);
            batteryIcon.setGravity(Gravity.CENTER);
            batteryRow.addView(batteryIcon,ll(d(62),d(60)));
            LinearLayout info=column();info.setGravity(Gravity.CENTER_VERTICAL);
            widgetBattery=text("--%",26,true,Color.WHITE);info.addView(widgetBattery);
            TextView subtitle=text("This Android device",12,false,white(220));info.addView(subtitle);
            batteryRow.addView(info);card.addView(batteryRow);
            LinearLayout.LayoutParams cp=ll(-1,d(126));cp.leftMargin=d(4);cp.rightMargin=d(4);
            body.addView(card,cp);
            addSpace(body,19);
        }else addSpace(body,22);
        List<ResolveInfo> favourites=preferredHomeApps(24);
        body.addView(iconsGrid(favourites));
        addSpace(body,16);
        TextView search=action("Search all installed apps",v->pager.goTo(1));
        search.setBackground(shape(0x4DFFFFFF,21,0x33FFFFFF));
        body.addView(search);
        return scroll(body);
    }

    private List<ResolveInfo> preferredHomeApps(int maximum){
        List<ResolveInfo> chosen=new ArrayList<>();
        String[] preferred={"phone","message","camera","photos","gallery","weather","calendar",
           "clock","maps","whatsapp","chrome","settings","music","spotify","youtube","gmail",
           "files","notes","contacts","calculator","play store","wallet","health","gallery","discord"};
        for(String match:preferred){
            for(ResolveInfo r:installed){
                String label=r.loadLabel(getPackageManager()).toString().toLowerCase(Locale.ROOT);
                if(label.contains(match)&&!chosen.contains(r)){chosen.add(r);break;}
            }
            if(chosen.size()>=maximum)break;
        }
        for(ResolveInfo r:installed){if(chosen.size()>=maximum)break;if(!chosen.contains(r))chosen.add(r);}
        return chosen;
    }

    private View appsPage() {
        LinearLayout body=pageBase();
        body.addView(heading("Apps"));addSpace(body,7);
        body.addView(text(installed.size()+" installed launchable apps",13,false,white(200)));addSpace(body,14);
        android.widget.EditText search=new android.widget.EditText(this);
        search.setSingleLine(true);search.setHint("Search installed apps");search.setHintTextColor(white(165));
        search.setTextColor(Color.WHITE);search.setTextSize(15);search.setPadding(d(15),d(9),d(15),d(9));
        search.setBackground(shape(parse("#6A273240"),17,white(36)));
        body.addView(search,ll(-1,d(52)));addSpace(body,19);
        GridLayout grid=iconsGrid(installed);body.addView(grid);
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int co,int af){}
            public void onTextChanged(CharSequence s,int st,int before,int count){
                String q=s.toString().toLowerCase(Locale.ROOT);
                for(int i=0;i<installed.size();i++) grid.getChildAt(i).setVisibility(
                    installed.get(i).loadLabel(getPackageManager()).toString().toLowerCase(Locale.ROOT).contains(q)
                    ? View.VISIBLE : View.GONE);
            }
            public void afterTextChanged(android.text.Editable e){}
        });
        return scroll(body);
    }
    private View garagePage() {
        LinearLayout body=pageBase();
        body.addView(text("WIDGETS  /  SHORTCUTS",11,true,white(210)));addSpace(body,8);
        body.addView(heading("Today View"));addSpace(body,7);
        body.addView(text("Tools that use your phone's actual hardware and apps.",13,false,white(200)));
        addSpace(body,24);
        body.addView(settingsRow("Flashlight", "Toggle the phone camera torch", "Turn on", v->toggleTorch()));
        addSpace(body,12);
        body.addView(settingsRow("Control Centre", "Brightness, volume, torch and Android quick settings", "Open", v->showControlCenter()));
        addSpace(body,12);
        body.addView(settingsRow("Notifications", "Read real notifications after permission", "Open", v->showNotifications()));
        addSpace(body,12);
        body.addView(settingsRow("App drawer", "All apps installed on the device", "Browse", v->pager.goTo(1)));
        addSpace(body,12);
        body.addView(settingsRow("Android Settings", "Open your phone's actual Settings app", "Open", v->openSystemSettings()));
        addSpace(body,22);
        TextView note=text("Vehicle theme is visual styling only. It does not claim to connect to or control a real car.",12,false,white(170));
        note.setLineSpacing(d(4),1f);body.addView(note);
        return scroll(body);
    }
    private View settingsRow(String title,String sub,String buttonText,View.OnClickListener click) {
        LinearLayout parent=row();parent.setPadding(d(13),d(15),d(11),d(15));
        parent.setBackground(shape(parse("#7A1C2938"),20,white(32)));
        LinearLayout details=column();
        details.addView(text(title,15,true,Color.WHITE));addSpace(details,5);
        TextView description=text(sub,12,false,white(190)); description.setMaxLines(2);details.addView(description);
        parent.addView(details,new LinearLayout.LayoutParams(0,-2,1));
        TextView btn=text(buttonText,12,true,white(240));btn.setGravity(Gravity.CENTER);
        btn.setPadding(d(10),d(10),d(10),d(10));btn.setBackground(shape(parse("#704E6682"),11,white(20)));
        btn.setOnClickListener(click);parent.addView(btn);
        parent.setOnClickListener(click);
        return parent;
    }
    private View settingsPage() {
        LinearLayout body=pageBase();body.setPadding(d(17),d(76),d(17),d(145));
        body.addView(heading("Settings"));addSpace(body,6);
        body.addView(text("Personalize your Home screen",13,false,white(230)));
        addSpace(body,21);
        body.addView(groupHeading("APPEARANCE"));addSpace(body,10);
        body.addView(settingsRow("Wallpaper Gallery","Browse real offline images and choose your favorite","›",v->startActivity(new Intent(this,WallpaperGalleryActivity.class))));
        addSpace(body,9);
        body.addView(settingsRow("Choose from my photos","Apply a picture stored on your phone","›",v->openGallery()));
        addSpace(body,13);
        body.addView(text("APP ICON APPEARANCE",12,true,white(230)));addSpace(body,8);
        LinearLayout choices=row();
        for(String[] option:new String[][]{{"Original","original"},{"Dark","mono"},{"Clear","clear"}}){
            boolean chosen=option[1].equals(preferences.getString("iconStyle","original"));
            TextView b=text(option[0]+(chosen?" ✓":""),13,true,Color.WHITE);
            b.setGravity(Gravity.CENTER);
            b.setBackground(shape(chosen?0xCB347EC4:0x70485370,18,0x55FFFFFF));
            LinearLayout.LayoutParams param=new LinearLayout.LayoutParams(0,d(47),1);
            param.rightMargin=d(7);choices.addView(b,param);
            b.setOnClickListener(v->{
                preferences.edit().putString("iconStyle",option[1]).apply();
                createUi();pager.goTo(3);
            });
        }
        body.addView(choices);addSpace(body,13);
        body.addView(text("APP ICON SIZE",12,true,white(222)));addSpace(body,7);
        LinearLayout sizes=row();
        for(int size:new int[]{52,60,66}){
            String label=size==52?"Small":size==60?"Normal":"Large";
            boolean chosen=preferences.getInt("iconSize",60)==size;
            TextView b=text(label+(chosen?" ✓":""),13,true,Color.WHITE);
            b.setGravity(Gravity.CENTER);
            b.setBackground(shape(chosen?0xCB347EC4:0x70485370,18,0x55FFFFFF));
            LinearLayout.LayoutParams param=new LinearLayout.LayoutParams(0,d(44),1);
            param.rightMargin=d(7);sizes.addView(b,param);
            b.setOnClickListener(v->{
                preferences.edit().putInt("iconSize",size).apply();createUi();pager.goTo(3);
            });
        }
        body.addView(sizes);addSpace(body,14);
        body.addView(settingsRow("Battery widget",preferences.getBoolean("showWidget",true)?"Visible on Home":"Hidden from Home",
                preferences.getBoolean("showWidget",true)?"On":"Off",v->{
                    boolean yes=preferences.getBoolean("showWidget",true);
                    preferences.edit().putBoolean("showWidget",!yes).apply();createUi();pager.goTo(3);
                }));
        addSpace(body,23);
        body.addView(groupHeading("CONTROLS & PERMISSIONS"));addSpace(body,10);
        body.addView(settingsRow("Control Centre","Brightness, volume, torch, Wi-Fi and Focus","›",v->showControlCenter()));
        addSpace(body,8);
        body.addView(settingsRow("Notification Centre","Real app alerts after notification access","›",v->showNotifications()));
        addSpace(body,8);
        body.addView(settingsRow("Notification access","Enable Android notification listener","›",v->requestNotificationAccess()));
        addSpace(body,8);
        body.addView(settingsRow("System Settings","Open the actual Android Settings app","›",v->openSystemSettings()));
        addSpace(body,8);
        body.addView(settingsRow("Default Home app","Choose this app as your Home launcher","›",v->requestHomeRole()));
        addSpace(body,23);
        body.addView(groupHeading("DYNAMIC ISLAND · CAMERA FIT"));addSpace(body,9);
        cutoffLabel=text("Detecting the front camera cutout…",12,false,white(210));
        body.addView(cutoffLabel);addSpace(body,11);
        body.addView(sliderRow("Move left / right","x",-80,80));
        body.addView(sliderRow("Move up / down","y",-24,60));
        body.addView(sliderRow("Width correction","w",-40,110));
        body.addView(sliderRow("Height correction","h",-18,45));
        addSpace(body,10);
        body.addView(action("Reset camera alignment",v->{
            preferences.edit().putInt("x",0).putInt("y",0).putInt("w",0).putInt("h",0).apply();
            createUi();pager.goTo(3);toast("Camera alignment reset");
        }));
        addSpace(body,12);
        TextView hint=text("Android reports the display cutout when available. Use the sliders if the reported area is not exact.",12,false,white(185));
        hint.setLineSpacing(d(3),1);body.addView(hint);
        return scroll(body);
    }
    private TextView groupHeading(String title){
        TextView t=text(title,12,true,white(212));t.setLetterSpacing(.08f);
        t.setPadding(d(9),0,0,0);return t;
    }

    private View sliderRow(String label,String pref,int min,int max) {
        LinearLayout box=column();box.setPadding(0,d(8),0,d(6));
        LinearLayout header=row();
        header.addView(text(label,12,false,white(220)),new LinearLayout.LayoutParams(0,-2,1));
        TextView out=text(preferences.getInt(pref,0)+" dp",12,true,white(200)); header.addView(out);
        box.addView(header);
        SeekBar seek=new SeekBar(this); seek.setMax(max-min);seek.setProgress(preferences.getInt(pref,0)-min);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int p,boolean user){
                if (!user)return;
                int value=p+min;
                preferences.edit().putInt(pref,value).apply();out.setText(value+" dp");alignIsland();
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });box.addView(seek);return box;
    }

    private View wallpaperPicker() {
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout choices=row();choices.setPadding(d(2),d(3),d(4),d(9));
        horizontal.addView(choices,new HorizontalScrollView.LayoutParams(-2,-2));
        for(int i=0;i<THEMES.length+PHOTO_IDS.length;i++){
            final int index=i;
            LinearLayout item=column();item.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView thumb=new ImageView(this);thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if(i<THEMES.length) thumb.setImageDrawable(WallpaperArtwork.preview(this,themeColors(i),i));
            else thumb.setImageDrawable(WallpaperArtwork.preview(this,
                    new int[]{parse("#506A83"),parse("#10131A")},i));
            thumb.setBackground(shape(parse("#33435B"),16,white(80)));
            thumb.setClipToOutline(true);
            item.addView(thumb,ll(d(83),d(108)));
            TextView lbl=text(i<THEMES.length?THEMES[i]:"Car photo "+(i-THEMES.length+1),10,false,white(225));
            lbl.setMaxLines(2);lbl.setGravity(Gravity.CENTER);LinearLayout.LayoutParams l=ll(d(91),d(35));l.topMargin=d(6);item.addView(lbl,l);
            item.setOnClickListener(v->selectWallpaper(index));
            LinearLayout.LayoutParams itemP=ll(d(100),-2);itemP.rightMargin=d(8);choices.addView(item,itemP);
        }
        return horizontal;
    }

    private int[] themeColors(int t) {
        String[][] palette={
            {"#182330","#06070A"},{"#325D93","#080F22"},{"#555F69","#090B10"},
            {"#0E8B8E","#14104E"},{"#CE7B75","#2A1B3A"},{"#2C99B3","#0C2B58"},
            {"#E1A989","#5C3E42"},{"#A084C7","#21143D"},{"#37806B","#0A2923"},
            {"#8195AB","#202936"},{"#A36E44","#28191B"},{"#4D5662","#07080A"},
            {"#2282E5","#161C76"},{"#8545F0","#130C56"},{"#16B5E0","#18366C"},
            {"#45ADEF","#192F75"},{"#2355B6","#080C41"},{"#9A60E7","#30126C"},
            {"#3DBAE8","#134F9E"},{"#7CC9FF","#2C4FB2"},{"#C3E9FA","#5879C0"},
            {"#617DF0","#1A236C"},{"#40E0CF","#1446A1"},{"#8A53C9","#110D4E"}};
        int k=Math.max(0,Math.min(palette.length-1,t));
        return new int[]{parse(palette[k][0]),parse(palette[k][1])};
    }
    private void selectWallpaper(int index) {
        preferences.edit().putString("wallpaper",index<THEMES.length ? "theme:"+index : "photo:"+(index-THEMES.length)).apply();
        renderWallpaper();toast(index<THEMES.length ? "Wallpaper selected" : "Downloading selected photo wallpaper…");
    }
    private void renderWallpaper() {
        if(wallpaper == null)return;
        String selected=preferences.getString("wallpaper","theme:0");
        if(selected.startsWith("pack:")){
            try{
                int n=Integer.parseInt(selected.substring(5));
                Drawable packed=WallpaperPack.full(this,n);
                if(packed!=null){wallpaper.setImageDrawable(packed);return;}
            }catch(Exception ignored){}
        }
        if(selected.startsWith("photo:")){
            try {
                int index=Integer.parseInt(selected.substring(6));
                if(index>=0 && index<PHOTO_IDS.length){ loadPhoto(index);return; }
            }catch(Exception ignored){}
        }
        if(selected.startsWith("gallery:")){
            try {
                Uri uri=Uri.parse(selected.substring(8));
                wallpaper.setImageURI(uri);
                if(wallpaper.getDrawable()!=null) return;
            }catch(Exception ignored){}
        }
        int index=1;
        try{if(selected.startsWith("theme:"))index=Integer.parseInt(selected.substring(6));}catch(Exception ignored){}
        if(index==0){
            try{
                Drawable original=WallpaperManager.getInstance(this).getDrawable();
                if(original!=null){wallpaper.setImageDrawable(original);return;}
            }catch(Exception ignored){}
        }
        wallpaper.setImageDrawable(WallpaperArtwork.render(this,themeColors(index),index));
    }
    private void loadPhoto(int i) {
        File file = new File(getFilesDir(),"photo_"+i+".jpg");
        if(file.exists() && file.length()>4000) {
            Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath());
            if(bitmap!=null){wallpaper.setImageBitmap(bitmap);return;}
        }
        // Work offline while download is in progress. No fabricated photo placeholder.
        wallpaper.setImageDrawable(gradient(parse("#304E67"),parse("#070B11"),0));
        new Thread(()->{
            HttpURLConnection conn=null;
            try {
                URL url=new URL("https://images.unsplash.com/"+PHOTO_IDS[i]+"?auto=format&fit=crop&w=1400&q=85");
                conn=(HttpURLConnection)url.openConnection();
                conn.setConnectTimeout(9000);conn.setReadTimeout(14000);
                conn.setInstanceFollowRedirects(true);
                if(conn.getResponseCode()!=200)throw new Exception("HTTP "+conn.getResponseCode());
                try(InputStream in=conn.getInputStream(); FileOutputStream out=new FileOutputStream(file)){
                    byte[] buffer=new byte[8192];int count;
                    while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
                }
                Bitmap b=BitmapFactory.decodeFile(file.getAbsolutePath());
                if(b==null)throw new Exception("Invalid image");
                runOnUiThread(()->{
                    if(preferences.getString("wallpaper","").equals("photo:"+i)) wallpaper.setImageBitmap(b);
                });
            }catch(Exception e){
                file.delete();runOnUiThread(()->toast("Photo download unavailable; choose an offline theme or gallery photo"));
            }finally{if(conn!=null)conn.disconnect();}
        },"wallpaper-download").start();
    }
    private void openGallery() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,REQUEST_GALLERY);
    }

    private void buildDock() {
        LinearLayout dock = row(); dock.setGravity(Gravity.CENTER);
        dock.setPadding(d(10),d(10),d(10),d(10));
        dock.setBackground(shape(parse("#70C7DAED"),29,white(80)));
        FrameLayout.LayoutParams dockP=fl(-1,d(83),Gravity.BOTTOM);
        dockP.leftMargin=d(13);dockP.rightMargin=d(13);dockP.bottomMargin=d(18);
        root.addView(dock,dockP);
        List<ResolveInfo> chosen=new ArrayList<>();
        String[] terms={"phone","message","chrome","music"};
        for(String t:terms)for(ResolveInfo r:installed){
            String label=r.loadLabel(getPackageManager()).toString().toLowerCase(Locale.ROOT);
            if(label.contains(t) && !chosen.contains(r)){chosen.add(r);break;}
        }
        for(ResolveInfo r:installed){if(chosen.size()>=4)break;if(!chosen.contains(r))chosen.add(r);}
        for(ResolveInfo r:chosen){
            ImageView image=new ImageView(this);image.setImageDrawable(r.loadIcon(getPackageManager()));
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout shell = row();shell.setGravity(Gravity.CENTER);
            shell.addView(image,ll(d(58),d(58)));
            dock.addView(shell,new LinearLayout.LayoutParams(0,-1,1));shell.setOnClickListener(v->launchApp(r));
        }
    }
    private void buildStatusBar(){
        LinearLayout status=row();status.setPadding(d(26),0,d(22),0);
        FrameLayout.LayoutParams sp=fl(-1,d(36),Gravity.TOP);sp.topMargin=d(8);
        statusTime=text("--:--",14,true,Color.WHITE);
        status.addView(statusTime,new LinearLayout.LayoutParams(0,-1,1));
        statusBattery=text("● ●   --%",13,true,Color.WHITE);
        statusBattery.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);status.addView(statusBattery);
        root.addView(status,sp);
        status.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_UP){
                if(e.getX()>v.getWidth()*.53f)showControlCenter();else showNotifications();
                return true;
            }
            return true;
        });
        refreshStatus();
    }
    private void refreshStatus() {
        String time=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date());
        if(statusTime!=null)statusTime.setText(time);
        if(widgetTime!=null)widgetTime.setText(time);
        if(statusBattery!=null)statusBattery.setText("▮▮  "+batteryPercent+"%");
        if(widgetBattery!=null)widgetBattery.setText(batteryPercent+"%");
        handler.removeCallbacks(clockTick);
        handler.postDelayed(clockTick,30000);
    }
    private final Runnable clockTick=this::refreshStatus;

    private void buildIsland() {
        island = new FrameLayout(this);
        island.setBackground(shape(parse("#030305"),32,parse("#161821")));
        FrameLayout.LayoutParams p=fl(d(133),d(38),Gravity.TOP|Gravity.LEFT);p.topMargin=d(8);
        root.addView(island,p);
        islandLabel=text("",10,true,parse("#89EDBE"));islandLabel.setGravity(Gravity.CENTER);
        islandLabel.setLetterSpacing(.05f);island.addView(islandLabel,fl(-1,-1,Gravity.FILL));
        island.setOnClickListener(v->{
            islandExpanded=!islandExpanded;
            alignIsland();refreshIsland();
            if(islandExpanded)handler.postDelayed(()->{islandExpanded=false;alignIsland();refreshIsland();},3500);
        });
        island.setOnLongClickListener(v->{showNotifications();return true;});
        refreshIsland();root.post(this::alignIsland);
    }
    private void refreshIsland() {
        if(islandLabel==null)return;
        StatusBarNotification[] all=NotificationBridge.currentNotifications();
        int count=0;String headline=null;
        for(StatusBarNotification n:all){
            if(n.getPackageName().equals(getPackageName()))continue;
            if((n.getNotification().flags & Notification.FLAG_ONGOING_EVENT)!=0)continue;
            count++;
            if(headline==null){
                CharSequence title=n.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE);
                headline=title==null?null:title.toString();
            }
        }
        String result=islandExpanded?(torchEnabled?"Flashlight is on":headline!=null?headline:"No live activities"):
                torchEnabled?"● TORCH ON":count>0?"● "+count+" ALERTS":"● VELOCITY";
        islandLabel.setText(result);
        islandLabel.setContentDescription(headline==null?result:headline);
    }
    private void alignIsland() {
        if(island==null||root==null||root.getWidth()==0)return;
        int baseWidth=foundCutout?Math.max(d(114),physicalCutout.width()+d(24)):d(132);
        int baseHeight=foundCutout?Math.max(d(34),physicalCutout.height()+d(7)):d(36);
        int width=islandExpanded?Math.min(root.getWidth()-d(48),d(285)):Math.max(d(95),baseWidth+d(preferences.getInt("w",0)));
        int height=islandExpanded?d(77):Math.max(d(28),baseHeight+d(preferences.getInt("h",0)));
        int center=foundCutout?physicalCutout.centerX():root.getWidth()/2;
        int x=center-width/2+d(preferences.getInt("x",0));
        int y=(foundCutout?Math.max(0,physicalCutout.top-d(3)):d(8))+d(preferences.getInt("y",0));
        x=Math.max(0,Math.min(x,Math.max(0,root.getWidth()-width)));
        y=Math.max(0,Math.min(y,d(105)));
        FrameLayout.LayoutParams params=fl(width,height,Gravity.TOP|Gravity.LEFT);params.leftMargin=x;params.topMargin=y;
        island.setLayoutParams(params);islandLabel.setTextSize(islandExpanded?13:10);
        if(cutoffLabel!=null)cutoffLabel.setText(foundCutout?
            "Detected camera cutout: "+physicalCutout.width()+" × "+physicalCutout.height()+" physical px":"Camera cutout not reported by Android. Manual alignment is available.");
    }

    private void showControlCenter() {
        if(controlCenter==null)return;
        if(panel!=null)panel.setVisibility(View.GONE);
        controlCenter.open(wallpaper==null?null:wallpaper.getDrawable());
    }
    private void requestBrightnessPermission() {
        if(Settings.System.canWrite(this)){toast("Screen brightness permission already granted");return;}
        try{
            Intent i=new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:"+getPackageName()));
            startActivity(i);
        }catch(Exception ignored){toast("Please grant Modify system settings in Android Settings");}
    }
    private void openMusicApp() {
        for(String term:new String[]{"music","spotify","youtube music","soundcloud"}) {
            for(ResolveInfo info:installed){
                if(info.loadLabel(getPackageManager()).toString().toLowerCase(Locale.ROOT).contains(term)){
                    launchApp(info);return;
                }
            }
        }
        try{startActivity(new Intent(android.provider.MediaStore.INTENT_ACTION_MUSIC_PLAYER));}
        catch(Exception e){toast("Install a music app to use this shortcut");}
    }
    private void showNotifications() {
        if(panel==null)return;
        panel.removeAllViews();panel.setVisibility(View.VISIBLE);
        View scrim=new View(this);scrim.setBackgroundColor(parse("#E5070B12"));
        panel.addView(scrim,fl(-1,-1,Gravity.FILL));
        LinearLayout wrapper=column();wrapper.setPadding(d(18),d(66),d(18),d(12));
        panel.addView(wrapper,fl(-1,-1,Gravity.FILL));
        LinearLayout header=row();header.addView(heading("Notification Center"),new LinearLayout.LayoutParams(0,-2,1));
        TextView close=action("Close",v->panel.setVisibility(View.GONE));header.addView(close);wrapper.addView(header);
        addSpace(wrapper,15);
        TextView info=text("Notifications from your real installed apps",12,false,white(190));wrapper.addView(info);
        addSpace(wrapper,14);
        TextView permission=action("Manage notification access",v->requestNotificationAccess());wrapper.addView(permission);
        addSpace(wrapper,16);
        ScrollView list=new ScrollView(this);list.setVerticalScrollBarEnabled(true);list.setFillViewport(false);
        panelContent=column();panelContent.setPadding(0,0,0,d(30));
        list.addView(panelContent,new ScrollView.LayoutParams(-1,-2));wrapper.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        renderNotifications();
    }
    private void renderNotifications() {
        if(panelContent==null)return;
        panelContent.removeAllViews();
        StatusBarNotification[] notifications=NotificationBridge.currentNotifications();
        List<StatusBarNotification> visible=new ArrayList<>();
        for(StatusBarNotification n:notifications){
            if(n.getPackageName().equals(getPackageName()))continue;
            if((n.getNotification().flags&Notification.FLAG_ONGOING_EVENT)!=0)continue;
            visible.add(n);
        }
        visible.sort((a,b)->Long.compare(b.getPostTime(),a.getPostTime()));
        if(visible.isEmpty()) {
            addSpace(panelContent,12);
            TextView hint=text(hasNotificationAccess()?"No active notifications right now.":
                "Notification access is off. Use the button above to grant it, then new alerts will show here.",14,false,white(215));
            hint.setPadding(d(17),d(21),d(17),d(21));hint.setBackground(shape(parse("#5E364353"),22,white(35)));
            panelContent.addView(hint);
        }
        for(StatusBarNotification sbn:visible){
            Notification n=sbn.getNotification();
            CharSequence t=n.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence detail=n.extras.getCharSequence(Notification.EXTRA_TEXT);
            LinearLayout item=column();item.setPadding(d(17),d(14),d(17),d(13));
            item.setBackground(shape(parse("#AA364658"),22,white(35)));
            LinearLayout top=row();
            TextView appName=text(appName(sbn.getPackageName()),12,true,white(210));top.addView(appName,new LinearLayout.LayoutParams(0,-2,1));
            TextView time=text(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(sbn.getPostTime())),11,false,white(170));top.addView(time);
            item.addView(top);addSpace(item,6);
            item.addView(text(t==null?appName(sbn.getPackageName()):t.toString(),15,true,Color.WHITE));
            if(detail!=null && detail.length()>0){addSpace(item,5);TextView body=text(detail.toString(),13,false,white(220));body.setMaxLines(4);item.addView(body);}
            PendingIntent pending=n.contentIntent;
            if(pending!=null)item.setOnClickListener(v->{
                try{pending.send();panel.setVisibility(View.GONE);}catch(PendingIntent.CanceledException e){toast("Notification is no longer available");}
            });
            LinearLayout.LayoutParams lp=ll(-1,-2);lp.bottomMargin=d(10);panelContent.addView(item,lp);
        }
    }
    private String appName(String pkg) {
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString(); }
        catch(Exception e){return pkg;}
    }
    private boolean hasNotificationAccess() {
        String list=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
        return list!=null&&list.contains(getPackageName());
    }
    private void requestNotificationAccess() {
        try{startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}
        catch(Exception e){toast("Open Android Settings to enable notification access");}
    }
    private void openSystemSettings() {
        try{startActivity(new Intent(Settings.ACTION_SETTINGS));}
        catch(Exception e){toast("Android Settings unavailable");}
    }
    private void requestHomeRole() {
        try{
            if(Build.VERSION.SDK_INT>=29){
                RoleManager manager=(RoleManager)getSystemService(ROLE_SERVICE);
                if(manager!=null&&manager.isRoleAvailable(RoleManager.ROLE_HOME)){
                    if(manager.isRoleHeld(RoleManager.ROLE_HOME)){toast("Velocity is already the default launcher");return;}
                    startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_HOME),103);return;
                }
            }
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        }catch(Exception e){toast("Open Settings > Default apps > Home app to select Velocity");}
    }
    private void toggleTorch() {
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA},REQUEST_CAMERA);return;
        }
        CameraManager camera=(CameraManager)getSystemService(Context.CAMERA_SERVICE);
        if(camera==null){toast("Camera service unavailable");return;}
        try{
            for(String id:camera.getCameraIdList()){
                Boolean flash=camera.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer lens=camera.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if(Boolean.TRUE.equals(flash)&&lens!=null&&lens==CameraCharacteristics.LENS_FACING_BACK){
                    camera.setTorchMode(id,!torchEnabled);torchEnabled=!torchEnabled;
                    refreshIsland();toast(torchEnabled?"Torch on":"Torch off");return;
                }
            }
            toast("No supported rear flashlight was found");
        }catch(CameraAccessException|SecurityException e){toast("Cannot control torch on this device");}
    }
    @Override public void onRequestPermissionsResult(int code,String[] perms,int[] grantResults){
        super.onRequestPermissionsResult(code,perms,grantResults);
        if(code==REQUEST_CAMERA && grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED)toggleTorch();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQUEST_GALLERY && resultCode==RESULT_OK && data!=null && data.getData()!=null){
            Uri uri=data.getData();
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            preferences.edit().putString("wallpaper","gallery:"+uri.toString()).apply();renderWallpaper();
            toast("Your wallpaper has been selected");
        }
    }
    private void showFirstRun() {
        setupLayer.removeAllViews();setupLayer.setVisibility(View.VISIBLE);
        View curtain=new View(this);curtain.setBackgroundColor(parse("#FA090E17"));
        setupLayer.addView(curtain,fl(-1,-1,Gravity.FILL));
        ScrollView s=new ScrollView(this);s.setFillViewport(true);
        setupLayer.addView(s,fl(-1,-1,Gravity.FILL));
        LinearLayout body=column();body.setPadding(d(25),d(85),d(25),d(34));s.addView(body);
        TextView brand=text("VELOCITY",14,true,parse("#94D4FF"));brand.setLetterSpacing(.3f);body.addView(brand);
        addSpace(body,19);body.addView(text("Make this phone yours.",34,true,Color.WHITE));
        addSpace(body,11);TextView detail=text("A real Android home launcher, fitted to your display and built around your installed apps.",15,false,white(215));
        detail.setLineSpacing(d(5),1f);body.addView(detail);addSpace(body,22);
        android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
        body.addView(text("Screen: "+metrics.widthPixels+" × "+metrics.heightPixels+" px  ·  Density: "+metrics.densityDpi+" dpi",12,false,white(210)));
        addSpace(body,11);
        body.addView(settingsRow("1. Display & camera", "Camera-cutout auto-detection. Fine-tune in Settings if the phone reports only a rough rectangle.","Check",v->{
            toast(foundCutout?"Display cutout detected":"No cutout reported; manual alignment available");
        }));addSpace(body,13);
        body.addView(settingsRow("2. Notification access", "Enable to populate the custom notification panel with real alerts.","Grant",v->requestNotificationAccess()));
        addSpace(body,13);
        body.addView(settingsRow("3. Default Home", "Choose Velocity as your default Android launcher.","Select",v->requestHomeRole()));
        addSpace(body,13);
        body.addView(settingsRow("4. Control Centre", "Swipe down from the top-right for quick controls. Top-left opens alerts.","Open",v->showControlCenter()));
        addSpace(body,27);
        body.addView(text("Choose wallpaper after setup",13,true,white(210)));addSpace(body,10);
        body.addView(action("Browse 120 offline wallpapers", v->startActivity(new Intent(this, WallpaperGalleryActivity.class))));addSpace(body,12);
        body.addView(wallpaperPicker());addSpace(body,23);
        TextView done=action("Finish setup and open launcher",v->{
            preferences.edit().putBoolean("setupComplete",true).apply();setupLayer.setVisibility(View.GONE);pager.goTo(0);
        });done.setPadding(d(12),d(18),d(12),d(18));body.addView(done);
        addSpace(body,14);body.addView(text("You can change the default Home app later. No Device Owner setup is performed.",11,false,white(155)));
    }

    /** Swipe down from top to open local notifications; horizontal swipes are handled by pager. */
    private final class TouchRoot extends FrameLayout {
        float startX,startY; boolean started;
        TouchRoot(Context c){super(c);setFocusableInTouchMode(true);}
        @Override public boolean dispatchTouchEvent(MotionEvent e){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){startX=e.getX();startY=e.getY();started=true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP&&started){
                float dy=e.getY()-startY,dx=e.getX()-startX;
                started=false;
                if(startY<d(82)&&dy>d(64)&&Math.abs(dy)>Math.abs(dx)*1.25f){
                    if(setupLayer==null||setupLayer.getVisibility()!=View.VISIBLE){
                        if(startX>getWidth()*.52f)showControlCenter();else showNotifications();
                    }
                    return true;
                }
            }
            return super.dispatchTouchEvent(e);
        }
    }
    /** Native HorizontalScrollView pages that snap, with no visual phone frame around the content. */
    private final class SnapPager extends HorizontalScrollView {
        int target=0;
        SnapPager(Context c){super(c);setFillViewport(true);setHorizontalScrollBarEnabled(false);setOverScrollMode(View.OVER_SCROLL_NEVER);}
        void goTo(int index){target=Math.max(0,Math.min(3,index));smoothScrollTo(target*getResources().getDisplayMetrics().widthPixels,0);}
        @Override public void fling(int velocityX){
            int width=getResources().getDisplayMetrics().widthPixels;
            target=Math.max(0,Math.min(3,Math.round((float)getScrollX()/width)+(velocityX>250?1:velocityX< -250?-1:0)));
            goTo(target);
        }
        @Override public boolean onTouchEvent(MotionEvent ev){
            boolean r=super.onTouchEvent(ev);
            if(ev.getActionMasked()==MotionEvent.ACTION_UP || ev.getActionMasked()==MotionEvent.ACTION_CANCEL){
                postDelayed(()->{
                    int width=getResources().getDisplayMetrics().widthPixels;
                    goTo(Math.round((float)getScrollX()/width));
                },100);
            }
            return r;
        }
    }
}
