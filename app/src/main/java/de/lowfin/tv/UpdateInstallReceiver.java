package de.lowfin.tv;

import android.content.*;

public final class UpdateInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {((LowfinApp)context.getApplicationContext()).updater.installationResult(intent);}
}
