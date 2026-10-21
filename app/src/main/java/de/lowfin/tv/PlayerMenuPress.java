package de.lowfin.tv;

import android.view.KeyEvent;
import java.util.function.IntConsumer;

/** Consume the entire press that reveals controls, including held repeats and release. */
final class PlayerMenuPress {
    private int opening=KeyEvent.KEYCODE_UNKNOWN;
    boolean activate(KeyEvent event,Runnable action) {
        if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0) { opening=event.getKeyCode(); action.run(); }
        return true;
    }
    boolean handle(KeyEvent event,boolean visible,Runnable pause,IntConsumer reveal) {
        int key=event.getKeyCode();
        if(opening!=KeyEvent.KEYCODE_UNKNOWN&&key==opening) { if(event.getAction()==KeyEvent.ACTION_UP) opening=KeyEvent.KEYCODE_UNKNOWN; return true; }
        boolean confirm=key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean arrow=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN;
        if(!visible&&(confirm||arrow)) {
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0) { opening=key; if(confirm) pause.run(); reveal.accept(key); }
            return true;
        }
        return false;
    }
}
