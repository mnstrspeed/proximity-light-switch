package nl.tomsanders.lightswitch;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;

public class ConnectActivity extends Activity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		String username = this.getIntent().getStringExtra("username");
		if (username == null) {
			username = "lightswitch";
		}
		new ConnectTask().execute(username);
	}
	
	protected void onConnectionEstablished() {
		// Launch LightSwitchService if the device is connected to power
		int powerStatus = this.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED))
				.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
		if (powerStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
				powerStatus == BatteryManager.BATTERY_STATUS_FULL) {
			this.startService(new Intent(ConnectActivity.this, LightSwitchService.class));
		}
		
		// Close
		this.finish();
	}
	
	public void onConnectionFailed() {
		// Tell the user they suck
		AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this)
			.setMessage("Failed to connect to Hue bridge")
			.setTitle("Failed");
		dialogBuilder.create().show();
	}

	public static enum ConnectTaskResult {
		CONNECTED,
		NO_BRIDGE_FOUND,
		WAITING_FOR_LINK
	}
	
	private class ConnectTask extends AsyncTask<String, ConnectTaskResult, ConnectTaskResult> {
		private ProgressDialog dialog;
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			
			dialog = new ProgressDialog(ConnectActivity.this);
			dialog.setMessage("Connecting to bridge...");
			dialog.setIndeterminate(false);
			dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			dialog.setCancelable(false);
			dialog.show();
		}
		
		@Override
		protected ConnectTaskResult doInBackground(String... params) {
			String username = params[0]; // only one
			
			// Connect to bridge
			HueEndpoint hue = HueEndpoint.getDefaultEndpoint();
	        if (hue == null) {
	        	return ConnectTaskResult.NO_BRIDGE_FOUND;
	        }
	        
        	hue.setUser(username);
        	if (!hue.isAuthorized()) {
        		publishProgress(ConnectTaskResult.WAITING_FOR_LINK);
        		hue.authorize();
        	}
        	return ConnectTaskResult.CONNECTED;
		}
		
		@Override
		protected void onProgressUpdate(ConnectTaskResult... values) {
			super.onProgressUpdate(values);
			
			if (values[0] == ConnectTaskResult.WAITING_FOR_LINK) {
				dialog.setMessage("Press the link button on your bridge");
			}
		}
		
		@Override
		protected void onPostExecute(ConnectTaskResult result) {
			Log.v("nl.tomsanders.lightswitch", "Connected");
			
			dialog.dismiss();
			if (result == ConnectTaskResult.CONNECTED) {
				ConnectActivity.this.onConnectionEstablished();
			} else {
				ConnectActivity.this.onConnectionFailed();
			}
		}
	}
}
