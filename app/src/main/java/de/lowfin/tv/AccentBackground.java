package de.lowfin.tv;

import android.graphics.Color;

/** Eight softly illuminated, static compositions for every curated accent hue. */
final class AccentBackground {
    static final int VARIANTS=8;
    private static final float[][] TINTS={
        {.16f,.04f,.095f},{.12f,.03f,.18f},{.18f,.07f,.03f},{.04f,.09f,.16f},
        {.145f,.03f,.075f},{.07f,.04f,.17f},{.12f,.06f,.03f},{.03f,.075f,.145f}
    };
    static int[] colors(int accent,int variant) {
        float[] weights=TINTS[Math.floorMod(variant,VARIANTS)]; int[] colors=new int[weights.length];
        for(int i=0;i<colors.length;i++) {
            float weight=weights[i];
            colors[i]=Color.rgb(Math.round(Color.red(Ui.BG)+(Color.red(accent)-Color.red(Ui.BG))*weight),
                Math.round(Color.green(Ui.BG)+(Color.green(accent)-Color.green(Ui.BG))*weight),
                Math.round(Color.blue(Ui.BG)+(Color.blue(accent)-Color.blue(Ui.BG))*weight));
        }
        return colors;
    }
    static SmoothGradient drawable(int accent,int variant) {
        return SmoothGradient.ambient(accent,variant);
    }
}
