package de.lowfin.tv;

import android.app.job.*;

public final class HomeJob extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        LowfinApp app=(LowfinApp)getApplication();
        if(app.account==null||app.playing||!app.settings.getBoolean("homeChannels",true)) return false;
        app.channels.refresh(()->jobFinished(params,false)); return true;
    }
    @Override public boolean onStopJob(JobParameters params) { return true; }
}
