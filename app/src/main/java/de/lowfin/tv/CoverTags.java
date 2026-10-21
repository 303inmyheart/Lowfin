package de.lowfin.tv;

import android.graphics.*;

/** Native overlay in frame coordinates, deliberately independent of ImageView's crop matrix. */
final class CoverTags {
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),line=new Paint(Paint.ANTI_ALIAS_FLAG),text=new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF dvd=new RectF(),surround=new RectF(),hdr=new RectF();
    private int width,height,mask;
    private float radius,baseline;
    CoverTags() { fill.setColor(0xdd12161b);line.setColor(0xaacbd4dd);line.setStyle(Paint.Style.STROKE);text.setColor(0xffeef1f4);text.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD)); }
    void layout(int width,int height,int mask,float density) {
        if(this.width==width&&this.height==height&&this.mask==mask) return;
        this.width=width;this.height=height;this.mask=mask;dvd.setEmpty();surround.setEmpty();hdr.setEmpty();
        if(width<=0||height<=0||mask==0) return;
        int count=Integer.bitCount(mask&7);float required=10+31*count+3*Math.max(0,count-1);
        float scale=Math.min(density,Math.min(width/Math.max(100f,required),height/56f));
        float margin=5*scale,gap=3*scale,h=16*scale,w=31*scale,x=width-margin,y=height-margin-h;
        radius=3*scale;text.setTextSize(9*scale);line.setStrokeWidth(Math.max(.75f,scale*.65f));
        Paint.FontMetrics metrics=text.getFontMetrics();baseline=y+(h-metrics.ascent-metrics.descent)/2;
        if((mask&4)!=0) { hdr.set(x-w,y,x,y+h);x-=w+gap; }
        if((mask&2)!=0) { surround.set(x-w,y,x,y+h);x-=w+gap; }
        if((mask&1)!=0) dvd.set(x-w,y,x,y+h);
    }
    void draw(Canvas canvas) { draw(canvas,dvd,"DVD");draw(canvas,surround,"5.1");draw(canvas,hdr,"HDR"); }
    private void draw(Canvas canvas,RectF box,String label) {
        if(box.isEmpty()) return;canvas.drawRoundRect(box,radius,radius,fill);canvas.drawRoundRect(box,radius,radius,line);
        canvas.drawText(label,box.centerX()-text.measureText(label)/2,baseline,text);
    }
}
