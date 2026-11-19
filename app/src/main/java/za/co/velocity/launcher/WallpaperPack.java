package za.co.velocity.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.IOException;
import java.io.InputStream;

/** 120 licensed-as-original locally generated offline photographs/abstract wallpapers.
 * Android OS and app icons are never bundled or redistributed. */
public final class WallpaperPack {
    public static final int COUNT = 120;
    public static final String[] FAMILIES = {
        "Atlantic", "Amethyst", "Aurora", "Midnight", "Neon Drive",
        "Pearl", "Copper", "Lagoon", "Iris", "Glacier"
    };
    private WallpaperPack(){}
    public static String title(int i) {
        return FAMILIES[i/12] + " " + String.format(java.util.Locale.US,"%02d",i%12+1);
    }
    public static Drawable full(Context context,int index) {
        Bitmap bitmap = bitmap(context,index, 1000,2000);
        return bitmap == null ? null : new BitmapDrawable(context.getResources(),bitmap);
    }
    /** Downsamples via inSampleSize before decoding: a 1200x2600 JPEG does not
     * require a 12 MB full-resolution allocation just to show a small thumbnail. */
    public static Bitmap bitmap(Context context,int index,int width,int height) {
        if(index<0 || index>=COUNT) return null;
        String path=String.format(java.util.Locale.US,"wallpapers/wall_%03d.jpg",index);
        try (InputStream in=context.getAssets().open(path)) {
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inJustDecodeBounds=true;
            BitmapFactory.decodeStream(in,null,options);
            int sample=1;
            while(options.outWidth/sample>width*1.5f && options.outHeight/sample>height*1.5f) sample*=2;
            options.inSampleSize=sample;
            options.inJustDecodeBounds=false;
            try(InputStream again=context.getAssets().open(path)){
                return BitmapFactory.decodeStream(again,null,options);
            }
        } catch (IOException ex) {return null;}
    }
}
