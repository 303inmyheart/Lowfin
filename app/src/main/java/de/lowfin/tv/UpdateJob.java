package de.lowfin.tv;

import android.app.job.*;

public final class UpdateJob extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        ((LowfinApp)getApplication()).updater.check(false,()->jobFinished(params,false));return true;
    }
    @Override public boolean onStopJob(JobParameters params) {return true;}
}
