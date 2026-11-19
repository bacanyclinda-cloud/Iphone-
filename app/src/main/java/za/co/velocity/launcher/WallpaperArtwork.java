package za.co.velocity.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/** Original abstract wallpapers, rendered locally at the current screen resolution. */
final class WallpaperArtwork {
    private WallpaperArtwork() {}
    private static int alpha(int c,int a){return Color.argb(a,Color.red(c),Color.green(c),Color.blue(c));}
    static Drawable render(Context ctx,int[] palette,int variation) {
        int screenW=Math.max(400,ctx.getResources().getDisplayMetrics().widthPixels);
        int screenH=Math.max(760,ctx.getResources().getDisplayMetrics().heightPixels);
        float f=Math.min(1.4f,1800f/Math.max(screenW,screenH));
        return renderSized(ctx,palette,variation,Math.max(400,Math.round(screenW*f)),Math.max(760,Math.round(screenH*f)));
    }
    static Drawable preview(Context ctx,int[] palette,int variation) {
        return renderSized(ctx,palette,variation,120,180);
    }
    private static Drawable renderSized(Context ctx,int[] palette,int variation,int w,int h) {
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        int main=palette[0],back=palette[1];
        paint.setShader(new LinearGradient(0,0,w,h,new int[]{main,back,Color.rgb(3,8,22)},new float[]{0f,.53f,1f},Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
        float shift=variation%4*.13f;
        orb(canvas,paint,w*(.69f-shift),h*.18f,w*.85f,alpha(Color.CYAN,100));
        orb(canvas,paint,w*(.18f+shift),h*.63f,w*.88f,alpha(main,145));
        orb(canvas,paint,w*.81f,h*.83f,w*.92f,alpha(Color.rgb(132,50,236),108));
        // Wide, curved soft-ribbon forms evoke premium glass wallpaper without copying Apple artwork.
        Path ribbon=new Path();
        ribbon.moveTo(-w*.4f,h*.13f);
        ribbon.cubicTo(w*.34f,h*.08f,w*.6f,h*.55f,w*1.24f,h*.17f);
        ribbon.lineTo(w*1.24f,h*.48f);
        ribbon.cubicTo(w*.44f,h*.82f,w*.12f,h*.38f,-w*.4f,h*.69f);
        ribbon.close();
        paint.setShader(new LinearGradient(w*.08f,h*.17f,w*.9f,h*.6f,
                new int[]{alpha(Color.WHITE,66),alpha(main,36),alpha(Color.WHITE,8)},null,Shader.TileMode.CLAMP));
        canvas.drawPath(ribbon,paint);paint.setShader(null);
        Path second=new Path();second.moveTo(w*.12f,h*1.12f);
        second.cubicTo(w*.31f,h*.67f,w*.98f,h*.59f,w*1.17f,h*.31f);
        second.lineTo(w*1.3f,h*.68f);
        second.cubicTo(w*.78f,h*.94f,w*.6f,h*1.1f,w*.5f,h*1.21f);
        second.close();
        paint.setShader(new LinearGradient(w*.3f,h*.65f,w*.98f,h*.9f,
                new int[]{alpha(Color.WHITE,11),alpha(Color.rgb(89,210,255),45),alpha(back,48)},null,Shader.TileMode.CLAMP));
        canvas.drawPath(second,paint);paint.setShader(null);
        orb(canvas,paint,w*.67f,h*.51f,w*.26f,alpha(Color.WHITE,15));
        return new BitmapDrawable(ctx.getResources(),bitmap);
    }
    private static void orb(Canvas canvas,Paint paint,float x,float y,float radius,int center) {
        paint.setShader(new RadialGradient(x,y,radius,new int[]{center,alpha(center,Math.max(8,Color.alpha(center)/4)),Color.TRANSPARENT},
                new float[]{0,.4f,1f},Shader.TileMode.CLAMP));
        canvas.drawCircle(x,y,radius,paint);paint.setShader(null);
    }
}
