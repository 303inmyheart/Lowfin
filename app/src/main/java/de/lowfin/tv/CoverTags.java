package de.lowfin.tv;

import android.graphics.*;

/** Native overlay in frame coordinates, deliberately independent of ImageView's crop matrix. */
final class CoverTags {
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),line=new Paint(Paint.ANTI_ALIAS_FLAG),text=new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF dvd=new RectF(),surround=new RectF(),hdr=new RectF();
    private int width,height,mask;
    private String[] labels=CoverTagLabels.EMPTY;
    final java.util.List<RectF> extraBoxes=new java.util.ArrayList<>();
    private final java.util.List<String> extraText=new java.util.ArrayList<>();
    private float radius,baseline;
    CoverTags() { fill.setColor(0xdd12161b);line.setColor(0xaacbd4dd);line.setStyle(Paint.Style.STROKE);text.setColor(0xffeef1f4);text.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD)); }
    void layout(int width,int height,int mask,float density) {layout(width,height,mask,density,CoverTagLabels.EMPTY);}
    void layout(int width,int height,int mask,float density,String[] labels) {
        if(this.width==width&&this.height==height&&this.mask==mask&&java.util.Arrays.equals(this.labels,labels)) return;
        this.labels=labels;extraBoxes.clear();extraText.clear();
        this.width=width;this.height=height;this.mask=mask;dvd.setEmpty();surround.setEmpty();hdr.setEmpty();
        if(width<=0||height<=0) return;
        if(labels.length>0) {layoutLabels(width,height,density,labels);return;}
        if(mask==0) return;
        int count=Integer.bitCount(mask&7);float required=10+31*count+3*Math.max(0,count-1);
        float scale=Math.min(density,Math.min(width/Math.max(100f,required),height/56f));
        float margin=5*scale,gap=3*scale,h=16*scale,w=31*scale,x=width-margin,y=height-margin-h;
        radius=3*scale;text.setTextSize(9*scale);line.setStrokeWidth(Math.max(.75f,scale*.65f));
        Paint.FontMetrics metrics=text.getFontMetrics();baseline=y+(h-metrics.ascent-metrics.descent)/2;
        if((mask&4)!=0) { hdr.set(x-w,y,x,y+h);x-=w+gap; }
        if((mask&2)!=0) { surround.set(x-w,y,x,y+h);x-=w+gap; }
        if((mask&1)!=0) dvd.set(x-w,y,x,y+h);
    }
    private void layoutLabels(int width,int height,float density,String[] values) {
        float scale=Math.min(density,Math.min(width/100f,height/56f)),margin=5*scale,gap=3*scale,h=16*scale;
        radius=3*scale;text.setTextSize(9*scale);line.setStrokeWidth(Math.max(.75f,scale*.65f));
        int maxRows=Math.max(1,Math.min(3,(int)(height*.45f/(h+gap)))),row=0;float x=width-margin,y=height-margin-h;
        for(int i=0;i<values.length;i++) {
            String label=CoverTagLabels.text(values[i]);float w=Math.max(31*scale,text.measureText(label)+12*scale);
            w=Math.min(w,width-2*margin);
            if(x-w<margin) {row++;x=width-margin;y-=h+gap;}
            if(row>=maxRows||i>=12) {if(!extraBoxes.isEmpty()) extraText.set(extraText.size()-1,"+"+(values.length-i+1));break;}
            extraBoxes.add(new RectF(x-w,y,x,y+h));extraText.add(label);x-=w+gap;
        }
    }
    void draw(Canvas canvas) {
        draw(canvas,dvd,"DVD");draw(canvas,surround,"5.1");draw(canvas,hdr,"HDR");
        Paint.FontMetrics metrics=text.getFontMetrics();
        for(int i=0;i<extraBoxes.size();i++) {RectF box=extraBoxes.get(i);baseline=box.centerY()-(metrics.ascent+metrics.descent)/2;draw(canvas,box,extraText.get(i));}
    }
    private void draw(Canvas canvas,RectF box,String label) {
        if(box.isEmpty()) return;canvas.drawRoundRect(box,radius,radius,fill);canvas.drawRoundRect(box,radius,radius,line);
        canvas.drawText(label,box.centerX()-text.measureText(label)/2,baseline,text);
    }
}
