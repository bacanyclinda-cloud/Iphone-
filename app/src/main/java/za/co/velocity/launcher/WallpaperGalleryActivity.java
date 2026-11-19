package za.co.velocity.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A native, scrollable, recycled wallpaper browser. 120 bundled JPEGs available offline. */
public final class WallpaperGalleryActivity extends Activity {
    private final LruCache<Integer,Bitmap> thumbnails = new LruCache<Integer,Bitmap>(18*1024*1024) {
        @Override protected int sizeOf(Integer key, Bitmap bitmap) {return bitmap.getByteCount();}
    };
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private int d(float n){return Math.round(getResources().getDisplayMetrics().density*n);}
    private TextView label(String txt, int size,boolean bold){
        TextView v=new TextView(this); v.setText(txt);v.setTextColor(Color.WHITE);v.setTextSize(size);
        if(bold)v.setTypeface(null,1);return v;
    }
    private GradientDrawable background(int color,int radius){
        GradientDrawable result=new GradientDrawable();result.setColor(color);result.setCornerRadius(d(radius));return result;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setNavigationBarColor(Color.rgb(7,11,22));
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(d(12),d(22),d(12),d(5));shell.setBackgroundColor(Color.rgb(7,11,22));
        setContentView(shell);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=label("‹",32,true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());
        toolbar.addView(back,new LinearLayout.LayoutParams(d(50),d(48)));
        LinearLayout detail=new LinearLayout(this);detail.setOrientation(LinearLayout.VERTICAL);
        detail.addView(label("Wallpapers",24,true));
        TextView subtitle=label("120 premium designs • Available offline",12,false);
        subtitle.setTextColor(Color.rgb(174,189,214));detail.addView(subtitle);
        toolbar.addView(detail);shell.addView(toolbar);
        TextView hint=label("Tap a wallpaper to apply it to your launcher.",13,false);
        hint.setTextColor(Color.rgb(190,199,215));hint.setPadding(d(12),d(10),0,d(16));shell.addView(hint);
        GridView grid=new GridView(this);grid.setNumColumns(3);grid.setVerticalSpacing(d(12));grid.setHorizontalSpacing(d(10));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setClipToPadding(false);
        grid.setPadding(d(3),d(3),d(3),d(30));grid.setBackgroundColor(Color.TRANSPARENT);
        grid.setAdapter(new PackAdapter());
        shell.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        grid.setOnItemClickListener((parent,view,index,id)->{
            getSharedPreferences("velocity_prefs",MODE_PRIVATE).edit()
                    .putString("wallpaper","pack:"+index).apply();
            setResult(RESULT_OK);finish();
        });
    }
    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}
    private final class PackAdapter extends BaseAdapter {
        @Override public int getCount(){return WallpaperPack.COUNT;}
        @Override public Object getItem(int position){return position;}
        @Override public long getItemId(int position){return position;}
        @Override public View getView(int position,View convert,ViewGroup parent){
            LinearLayout item;
            ImageView image;
            TextView name;
            if(convert instanceof LinearLayout){
                item=(LinearLayout)convert;image=(ImageView)item.getChildAt(0);name=(TextView)item.getChildAt(1);
            }else{
                item=new LinearLayout(WallpaperGalleryActivity.this);item.setOrientation(LinearLayout.VERTICAL);
                image=new ImageView(WallpaperGalleryActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setBackground(background(Color.rgb(32,49,80),16));image.setClipToOutline(true);
                item.addView(image,new LinearLayout.LayoutParams(-1,d(185)));
                name=label("",11,false);name.setGravity(Gravity.CENTER);name.setPadding(0,d(8),0,d(2));
                item.addView(name,new LinearLayout.LayoutParams(-1,d(38)));
            }
            name.setText(WallpaperPack.title(position));
            final int key=position;image.setTag(key);
            Bitmap cached=thumbnails.get(key);
            if(cached!=null){image.setImageBitmap(cached);}
            else{
                image.setImageDrawable(null);
                final ImageView target=image;
                executor.execute(()->{
                    Bitmap result=WallpaperPack.bitmap(WallpaperGalleryActivity.this,key,180,400);
                    if(result==null)return;
                    thumbnails.put(key,result);
                    ui.post(()->{
                        if(key == (Integer)target.getTag() && !isFinishing())target.setImageBitmap(result);
                    });
                });
            }
            return item;
        }
    }
}
