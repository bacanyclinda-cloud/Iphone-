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
import android.graphics.Rect;
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
        View tint=new View(activity);tint.setBackgroundColor(c("#B91A273A"));addView(tint,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(activity);
        scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-1);
        sp.topMargin=d(37);sp.bottomMargin=d(9);addView(scroll,sp);
        LinearLayout body=vertical();body.setPadding(d(19),d(27),d(19),d(30));
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        LinearLayout header=horizontal();
        TextView title=label("Control Centre",26,true,Color.WHITE);
        header.addView(title,new LinearLayout.LayoutParams(0,d(48),1));
        TextView close=label("Done",14,true,white(240));close.setGravity(Gravity.CENTER);
        close.setBackground(glass(0x84536A86,16));close.setPadding(d(16),d(9),d(16),d(9));
        close.setOnClickListener(v->close());header.addView(close);
        body.addView(header);spacing(body,15);

        LinearLayout top=horizontal();top.setGravity(Gravity.TOP);
        LinearLayout connections=vertical();connections.setPadding(d(12),d(12),d(12),d(10));
        connections.setBackground(glass(0x8C556783,25));
        LinearLayout first=horizontal();first.addView(shortcut("Wi-Fi","wifi",()->openIntent(new Intent(Build.VERSION.SDK_INT>=29?Settings.Panel.ACTION_WIFI:Settings.ACTION_WIFI_SETTINGS))),weight(68));
        first.addView(shortcut("Bluetooth","bluetooth",()->openIntent(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))),weight(68));
        connections.addView(first);spacing(connections,5);
        LinearLayout second=horizontal();second.addView(shortcut("Mobile","cellular",()->openIntent(new Intent(Settings.ACTION_WIRELESS_SETTINGS))),weight(68));
        second.addView(shortcut("Airplane","airplane",()->openIntent(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))),weight(68));
        connections.addView(second);
        LinearLayout.LayoutParams cp=weight(161);cp.rightMargin=d(10);top.addView(connections,cp);

        LinearLayout music=vertical();music.setPadding(d(13),d(14),d(13),d(11));music.setBackground(glass(0x8C556783,25));
        music.addView(label("MEDIA",10,true,white(195)));
        spacing(music,9);music.addView(label("Not Playing",16,true,Color.WHITE));
        spacing(music,6);TextView hint=label("Open music app",12,false,white(210));hint.setMaxLines(2);music.addView(hint);
        spacing(music,15);TextView play=label("❮      ▶      ❯",21,true,white(245));play.setGravity(Gravity.CENTER);
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
        LinearLayout torch=actionTile("Torch","torch",()->{callbacks.onTorch(); if(torchText!=null)torchText.setText(callbacks.isTorchOn()?"Torch On":"Torch");});
        torchText=(TextView)torch.getChildAt(1);torchText.setText(callbacks.isTorchOn()?"Torch On":"Torch");
        LinearLayout dnd=actionTile("Focus","moon",this::toggleFocus);
        LinearLayout battery=actionTile("Battery","battery",()->openIntent(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)));
        LinearLayout settings=actionTile("Settings","settings",callbacks::onAndroidSettings);
        actions.addView(torch,weight(93));actions.addView(dnd,weight(93));actions.addView(battery,weight(93));actions.addView(settings,weight(93));body.addView(actions);
        spacing(body,11);

        LinearLayout bottom=horizontal();
        View notifications=wideTile("Notifications", "Your app alerts", "bell",()->{close();callbacks.onNotifications();});
        LinearLayout.LayoutParams np=weight(75);np.rightMargin=d(10);bottom.addView(notifications,np);
        View device=wideTile("Display", "Brightness access", "brightness",this::requestGlobalBrightness);
        bottom.addView(device,weight(75));body.addView(bottom);
        spacing(body,13);
        TextView fineprint=label("Wi-Fi, Bluetooth, mobile data and flight mode open Android's own settings, because third-party launchers cannot silently change every protected system setting.",11,false,white(180));
        fineprint.setLineSpacing(d(3),1f);body.addView(fineprint);
    }
    void close() {setVisibility(GONE);removeAllViews();}
    boolean opened(){return getVisibility()==VISIBLE;}

    private LinearLayout shortcut(String caption,String symbol,Runnable click) {
        LinearLayout b=vertical();b.setGravity(Gravity.CENTER);b.setPadding(d(3),d(3),d(3),d(4));
        FrameLayout tile=new FrameLayout(activity);
        tile.setBackground(glass(0xCF337BD3,18));
        tile.addView(new GlyphIcon(activity,symbol),new FrameLayout.LayoutParams(-1,-1));
        b.addView(tile,new LinearLayout.LayoutParams(d(46),d(46)));
        spacing(b,5);
        TextView label=label(caption,11,true,white(246));label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.addView(label,new LinearLayout.LayoutParams(-1,d(19)));
        b.setOnClickListener(v->click.run());return b;
    }
    private LinearLayout levelCard(String caption,VerticalLevel level) {
        LinearLayout box=vertical();box.setGravity(Gravity.CENTER);box.setPadding(d(15),d(12),d(15),d(10));
        box.setBackground(glass(0x88647488,24));
        box.addView(level,new LinearLayout.LayoutParams(d(62),d(150)));
        spacing(box,10);TextView text=label(caption,13,true,white(245));text.setGravity(Gravity.CENTER);
        box.addView(text,new LinearLayout.LayoutParams(-1,d(20)));
        return box;
    }
    private LinearLayout actionTile(String caption,String symbol,Runnable click) {
        LinearLayout box=vertical();box.setGravity(Gravity.CENTER);box.setPadding(d(5),d(6),d(5),d(5));
        box.setBackground(glass(0x94546477,18));
        box.addView(new GlyphIcon(activity,symbol),new LinearLayout.LayoutParams(d(37),d(37)));
        TextView lbl=label(caption,10,true,white(245));lbl.setGravity(Gravity.CENTER);
        lbl.setSingleLine(true);lbl.setEllipsize(android.text.TextUtils.TruncateAt.END);
        box.addView(lbl,new LinearLayout.LayoutParams(-1,d(23)));
        box.setOnClickListener(v->click.run());return box;
    }
    private View wideTile(String title,String subtitle,String icon,Runnable click) {
        LinearLayout row=horizontal();row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(d(12),d(10),d(12),d(10));
        row.setBackground(glass(0x94546477,18));
        row.addView(new GlyphIcon(activity,icon),new LinearLayout.LayoutParams(d(33),d(33)));
        LinearLayout captions=vertical();captions.setPadding(d(7),0,0,0);
        captions.addView(label(title,12,true,Color.WHITE));
        captions.addView(label(subtitle,10,false,white(200)));
        row.addView(captions);
        row.setOnClickListener(v->click.run());return row;
    }
    /** Canvas-drawn vectors keep the quick controls consistent on all Android devices. */
    private static final class GlyphIcon extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final String symbol;
        GlyphIcon(Context ctx,String symbol){super(ctx);this.symbol=symbol;setContentDescription(symbol);}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();canvas.translate(getWidth()/2f,getHeight()/2f);
            float k=Math.min(getWidth(),getHeight())/48f;canvas.scale(k,k);
            p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3.2f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            switch(symbol){
                case "wifi":
                    canvas.drawArc(-17,-11,17,16,222,96,false,p);
                    canvas.drawArc(-11,-5,11,11,222,96,false,p);
                    p.setStyle(Paint.Style.FILL);canvas.drawCircle(0,12,3,p);break;
                case "bluetooth":
                    Path b=new Path();b.moveTo(0,-19);b.lineTo(12,-8);b.lineTo(-11,10);
                    b.moveTo(-11,-10);b.lineTo(12,9);b.lineTo(0,20);b.lineTo(0,-19);
                    canvas.drawPath(b,p);break;
                case "airplane":
                    Path a=new Path();a.moveTo(0,-20);a.lineTo(4,-4);a.lineTo(18,4);
                    a.lineTo(18,9);a.lineTo(4,7);a.lineTo(4,16);a.lineTo(9,20);
                    a.lineTo(-9,20);a.lineTo(-4,16);a.lineTo(-4,7);a.lineTo(-18,9);
                    a.lineTo(-18,4);a.lineTo(-4,-4);a.close();p.setStyle(Paint.Style.FILL);canvas.drawPath(a,p);break;
                case "cellular":
                    p.setStyle(Paint.Style.FILL);
                    for(int i=0;i<4;i++)canvas.drawRoundRect(-17+i*9,12-i*7,-11+i*9,18,2,2,p);break;
                case "torch":
                    Path t=new Path();t.moveTo(-9,-19);t.lineTo(9,-19);t.lineTo(9,-6);
                    t.lineTo(5,2);t.lineTo(5,18);t.lineTo(-5,18);t.lineTo(-5,2);t.lineTo(-9,-6);t.close();
                    canvas.drawPath(t,p);canvas.drawLine(-9,-9,9,-9,p);break;
                case "moon":
                    p.setStyle(Paint.Style.FILL);canvas.drawCircle(0,0,15,p);
                    p.setColor(0xFF586578);canvas.drawCircle(8,-6,15,p);break;
                case "battery":
                    canvas.drawRoundRect(-17,-10,14,10,4,4,p);
                    p.setStyle(Paint.Style.FILL);canvas.drawRect(-12,-5,7,5,p);canvas.drawRect(16,-4,19,4,p);break;
                case "settings":
                    canvas.drawCircle(0,0,9,p);canvas.drawCircle(0,0,3,p);
                    for(int i=0;i<8;i++){double r=i*Math.PI/4;canvas.drawLine((float)(Math.cos(r)*13),(float)(Math.sin(r)*13),(float)(Math.cos(r)*19),(float)(Math.sin(r)*19),p);}break;
                case "bell":
                    Path bell=new Path();bell.moveTo(-12,8);bell.lineTo(-8,2);bell.lineTo(-8,-6);
                    bell.cubicTo(-8,-20,8,-20,8,-6);bell.lineTo(8,2);bell.lineTo(12,8);bell.close();
                    canvas.drawPath(bell,p);canvas.drawArc(-4,7,4,15,0,180,false,p);break;
                case "brightness":
                    canvas.drawCircle(0,0,8,p);
                    for(int i=0;i<8;i++){double r=i*Math.PI/4;canvas.drawLine((float)(Math.cos(r)*13),(float)(Math.sin(r)*13),(float)(Math.cos(r)*19),(float)(Math.sin(r)*19),p);}break;
                default:canvas.drawCircle(0,0,12,p);
            }
            canvas.restore();
        }
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
