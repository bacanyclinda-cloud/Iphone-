package za.co.velocity.launcher;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.RenderEffect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Native iPhone-inspired Control Centre. All working switches use real Android APIs;
 * restricted mobile data/Bluetooth/airplane actions deliberately open Android settings.
 * Shown only over the launcher; it does not replace Android's secure SystemUI.
 */
final class ControlCenterOverlay extends FrameLayout {
    interface Callbacks {
        void onTorch();
        boolean isTorchOn();
        void onNotifications();
        void onOpenMusic();
        void onAndroidSettings();
    }
    private final Activity activity;
    private final Callbacks callbacks;
    private final AudioManager audio;
    private ImageView blurredWall;
    private TextView torchText;
    private VerticalLevel brightnessLevel, volumeLevel;

    ControlCenterOverlay(Activity activity, Callbacks callbacks) {
        super(activity);
        this.activity = activity;
        this.callbacks = callbacks;
        this.audio = (AudioManager)activity.getSystemService(Context.AUDIO_SERVICE);
        setVisibility(GONE);
        setClickable(true);
    }
    private int d(float dp) { return (int)(getResources().getDisplayMetrics().density * dp + .5f); }
    private int c(String value) { return Color.parseColor(value); }
    private int white(int alpha) { return Color.argb(alpha,255,255,255); }
    private LinearLayout vertical() { LinearLayout v=new LinearLayout(activity);v.setOrientation(LinearLayout.VERTICAL);return v; }
    private LinearLayout horizontal() { LinearLayout v=new LinearLayout(activity);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v; }
    private GradientDrawable glass(int tint,int radius) {
        GradientDrawable drawable=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{tint,0x97313F5A});
        drawable.setCornerRadius(d(radius));drawable.setStroke(d(1),white(60));return drawable;
    }
    private TextView label(String value,int size,boolean bold,int color) {
        TextView v=new TextView(activity);v.setText(value);v.setTextColor(color);v.setTextSize(size);
        v.setGravity(Gravity.CENTER_VERTICAL);v.setIncludeFontPadding(false);
        if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        return v;
    }
    private void spacing(LinearLayout parent,int dp) { parent.addView(new View(activity),new LinearLayout.LayoutParams(1,d(dp))); }
    private LinearLayout.LayoutParams weight(int heightDp) { return new LinearLayout.LayoutParams(0,heightDp<0?heightDp:d(heightDp),1f); }

    void open(Drawable wallpaper) {
        removeAllViews();setVisibility(VISIBLE);
        blurredWall=new ImageView(activity);
        blurredWall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if(wallpaper!=null) blurredWall.setImageDrawable(wallpaper);
        if(Build.VERSION.SDK_INT>=31)blurredWall.setRenderEffect(RenderEffect.createBlurEffect(d(42),d(42),Shader.TileMode.CLAMP));
        addView(blurredWall,new FrameLayout.LayoutParams(-1,-1));
        View tint=new View(activity);tint.setBackgroundColor(c("#D81B2741"));addView(tint,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(activity);
        scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-1);
        sp.topMargin=d(37);sp.bottomMargin=d(9);addView(scroll,sp);
        LinearLayout body=vertical();body.setPadding(d(19),d(27),d(19),d(30));
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        LinearLayout header=horizontal();
        TextView title=label("Control Centre",27,true,Color.WHITE);
        header.addView(title,new LinearLayout.LayoutParams(0,d(48),1));
        TextView close=label("Done",14,true,white(240));close.setGravity(Gravity.CENTER);
        close.setBackground(glass(0x84536A86,16));close.setPadding(d(16),d(9),d(16),d(9));
        close.setOnClickListener(v->close());header.addView(close);
        body.addView(header);spacing(body,15);

        LinearLayout top=horizontal();top.setGravity(Gravity.TOP);
        LinearLayout connections=vertical();connections.setPadding(d(12),d(12),d(12),d(10));
        connections.setBackground(glass(0xA14B5B78,25));
        LinearLayout first=horizontal();first.addView(shortcut("Wi-Fi","⌁",()->openIntent(new Intent(Build.VERSION.SDK_INT>=29?Settings.Panel.ACTION_WIFI:Settings.ACTION_WIFI_SETTINGS))),weight(68));
        first.addView(shortcut("Bluetooth","ᛒ",()->openIntent(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))),weight(68));
        connections.addView(first);spacing(connections,5);
        LinearLayout second=horizontal();second.addView(shortcut("Mobile","▥",()->openIntent(new Intent(Settings.ACTION_WIRELESS_SETTINGS))),weight(68));
        second.addView(shortcut("Airplane","✈",()->openIntent(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))),weight(68));
        connections.addView(second);
        LinearLayout.LayoutParams cp=weight(161);cp.rightMargin=d(10);top.addView(connections,cp);

        LinearLayout music=vertical();music.setPadding(d(13),d(14),d(13),d(11));music.setBackground(glass(0xA84A5872,25));
        music.addView(label("MEDIA",10,true,white(195)));
        spacing(music,9);music.addView(label("Not Playing",16,true,Color.WHITE));
        spacing(music,6);TextView hint=label("Open music app",12,false,white(210));hint.setMaxLines(2);music.addView(hint);
        spacing(music,15);TextView play=label("◀    ▶    ▶▶",21,true,white(245));play.setGravity(Gravity.CENTER);
        play.setBackground(glass(0x7A425D7C,16));play.setOnClickListener(v->callbacks.onOpenMusic());
        music.addView(play,new LinearLayout.LayoutParams(-1,d(42)));
        top.addView(music,weight(161));body.addView(top);
        spacing(body,11);

        LinearLayout mid=horizontal();mid.setGravity(Gravity.TOP);
        brightnessLevel=new VerticalLevel(activity,"☀",.57f,level->setBrightness(level));
        volumeLevel=new VerticalLevel(activity,"♪",getVolume(),level->setVolume(level));
        LinearLayout brightnessCard=levelCard("Brightness",brightnessLevel);
        LinearLayout volumeCard=levelCard("Volume",volumeLevel);
        LinearLayout.LayoutParams lp=weight(206);lp.rightMargin=d(11);mid.addView(brightnessCard,lp);
        mid.addView(volumeCard,weight(206));body.addView(mid);spacing(body,11);

        LinearLayout actions=horizontal();
        LinearLayout torch=actionTile("Torch","♜",()->{callbacks.onTorch(); if(torchText!=null)torchText.setText(callbacks.isTorchOn()?"Torch On":"Torch");});
        torchText=(TextView)torch.getChildAt(1);torchText.setText(callbacks.isTorchOn()?"Torch On":"Torch");
        LinearLayout dnd=actionTile("Focus","☾",this::toggleFocus);
        LinearLayout battery=actionTile("Battery","▰",()->openIntent(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)));
        LinearLayout settings=actionTile("Settings","⚙",callbacks::onAndroidSettings);
        actions.addView(torch,weight(93));actions.addView(dnd,weight(93));actions.addView(battery,weight(93));actions.addView(settings,weight(93));body.addView(actions);
        spacing(body,11);

        LinearLayout bottom=horizontal();
        TextView notifications=wideTile("Notifications", "Real alerts from your apps", "▣",()->{close();callbacks.onNotifications();});
        LinearLayout.LayoutParams np=weight(75);np.rightMargin=d(10);bottom.addView(notifications,np);
        TextView device=wideTile("Screen control", "Brightness permission", "☼",this::requestGlobalBrightness);
        bottom.addView(device,weight(75));body.addView(bottom);
        spacing(body,13);
        TextView fineprint=label("Wi-Fi, Bluetooth, mobile data and flight mode open Android's own settings, because third-party launchers cannot silently change every protected system setting.",11,false,white(180));
        fineprint.setLineSpacing(d(3),1f);body.addView(fineprint);
    }
    void close() {setVisibility(GONE);removeAllViews();}
    boolean opened(){return getVisibility()==VISIBLE;}

    private LinearLayout shortcut(String caption,String symbol,Runnable click) {
        LinearLayout b=vertical();b.setGravity(Gravity.CENTER);b.setPadding(d(4),d(4),d(4),d(3));
        TextView ico=label(symbol,23,true,Color.WHITE);ico.setGravity(Gravity.CENTER);
        ico.setBackground(glass(0xAA5C86BF,15));b.addView(ico,new LinearLayout.LayoutParams(d(39),d(39)));
        spacing(b,5);TextView label=label(caption,10,true,white(248));label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.addView(label,new LinearLayout.LayoutParams(-1,d(19)));b.setOnClickListener(v->click.run());return b;
    }
    private LinearLayout levelCard(String caption,VerticalLevel level) {
        LinearLayout box=vertical();box.setGravity(Gravity.CENTER);box.setPadding(d(15),d(12),d(15),d(10));
        box.setBackground(glass(0x865E7899,24));
        box.addView(level,new LinearLayout.LayoutParams(d(62),d(150)));
        spacing(box,10);TextView text=label(caption,13,true,white(245));text.setGravity(Gravity.CENTER);
        box.addView(text,new LinearLayout.LayoutParams(-1,d(20)));
        return box;
    }
    private LinearLayout actionTile(String caption,String symbol,Runnable click) {
        LinearLayout box=vertical();box.setGravity(Gravity.CENTER);box.setPadding(d(4),d(7),d(4),d(5));
        box.setBackground(glass(0x9D4A5C78,18));
        TextView ico=label(symbol,25,true,Color.WHITE);ico.setGravity(Gravity.CENTER);box.addView(ico,new LinearLayout.LayoutParams(-1,d(47)));
        TextView lbl=label(caption,10,true,white(245));lbl.setGravity(Gravity.CENTER);lbl.setSingleLine(true);
        lbl.setEllipsize(android.text.TextUtils.TruncateAt.END);box.addView(lbl,new LinearLayout.LayoutParams(-1,d(23)));
        box.setOnClickListener(v->click.run());return box;
    }
    private TextView wideTile(String title,String subtitle,String icon,Runnable click) {
        TextView b=label(icon+"  "+title+"\n     "+subtitle,12,true,Color.WHITE);
        b.setLineSpacing(d(4),1f);b.setPadding(d(13),d(8),d(9),d(8));
        b.setBackground(glass(0xA1435C7C,18));b.setOnClickListener(v->click.run());return b;
    }
    private float getVolume() {
        if(audio==null)return .4f;
        return (float)audio.getStreamVolume(AudioManager.STREAM_MUSIC)/Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
    }
    private void setVolume(float fraction) {
        if(audio==null)return;
        int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.round(fraction*max),0);
    }
    private void setBrightness(float fraction) {
        android.view.WindowManager.LayoutParams params=activity.getWindow().getAttributes();
        params.screenBrightness=Math.max(.01f,fraction);
        activity.getWindow().setAttributes(params);
        if(Settings.System.canWrite(activity)){
            Settings.System.putInt(activity.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,Math.max(1,Math.round(fraction*255)));
        }
    }
    private void requestGlobalBrightness() {
        if(Settings.System.canWrite(activity)){
            Toast.makeText(activity,"Full brightness control already granted",Toast.LENGTH_SHORT).show();return;
        }
        try {
            Intent i=new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:"+activity.getPackageName()));
            activity.startActivity(i);
        }catch(Exception ex){Toast.makeText(activity,"Open Android settings to allow changing brightness",Toast.LENGTH_LONG).show();}
    }
    private void toggleFocus() {
        NotificationManager nm=(NotificationManager)activity.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        if(!nm.isNotificationPolicyAccessGranted()){
            openIntent(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            Toast.makeText(activity,"Allow Do Not Disturb access to use Focus",Toast.LENGTH_LONG).show();return;
        }
        int next=nm.getCurrentInterruptionFilter()==NotificationManager.INTERRUPTION_FILTER_ALL?
                NotificationManager.INTERRUPTION_FILTER_PRIORITY:NotificationManager.INTERRUPTION_FILTER_ALL;
        nm.setInterruptionFilter(next);
        Toast.makeText(activity,next==NotificationManager.INTERRUPTION_FILTER_ALL?"Focus off":"Focus on",Toast.LENGTH_SHORT).show();
    }
    private void openIntent(Intent intent){
        try{activity.startActivity(intent);}catch(Exception ex){Toast.makeText(activity,"This setting is not available on this phone",Toast.LENGTH_SHORT).show();}
    }
    private interface OnLevelChanged {void onChanged(float level);}
    /** A real touch-driven vertical SeekBar, with a clean liquid style. */
    private static final class VerticalLevel extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String glyph;private final OnLevelChanged listener;private float value;
        VerticalLevel(Context ctx,String glyph,float value,OnLevelChanged listener){
            super(ctx);this.glyph=glyph;this.value=value;this.listener=listener;
            setContentDescription(glyph.equals("☀")?"Brightness slider":"Media volume slider");
        }
        private int d(float dp){return (int)(dp*getResources().getDisplayMetrics().density+.5f);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float w=getWidth(),h=getHeight(),radius=d(22),pad=d(1);
            RectF rail=new RectF(pad,pad,w-pad,h-pad);p.setColor(0xA6D8E3F3);
            canvas.drawRoundRect(rail,radius,radius,p);
            Path mask=new Path();mask.addRoundRect(rail,radius,radius,Path.Direction.CW);
            canvas.save();canvas.clipPath(mask);
            float top=h-(h-2*pad)*value;
            p.setColor(Color.WHITE);canvas.drawRect(pad,top,w-pad,h-pad,p);
            canvas.restore();p.setTextAlign(Paint.Align.CENTER);
            p.setColor(value>.15f?0xFF283650:Color.WHITE);p.setTextSize(d(25));
            canvas.drawText(glyph,w/2,h-d(17),p);
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    value=Math.max(0,Math.min(1,1-event.getY()/Math.max(1,getHeight())));
                    invalidate();listener.onChanged(value);return true;
                case MotionEvent.ACTION_UP:performClick();return true;
                default:return super.onTouchEvent(event);
            }
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
