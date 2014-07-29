package nl.tomsanders.lightswitch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

public class PowerConnectionReceiver extends BroadcastReceiver {

	private static final String TAG = "nl.tomsanders.lightswitch";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		int status = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED))
				.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

		Intent serviceIntent = new Intent(context, LightSwitchService.class);
		if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
				status == BatteryManager.BATTERY_STATUS_FULL) {
			// Device is connected
			Log.v(TAG, "Power connected; staring service");
			context.startService(serviceIntent);
		} else {
			// Device is (no longer) connected
			Log.v(TAG, "Power disconnected; stopping service");
			context.stopService(serviceIntent);
		}
	}

}
