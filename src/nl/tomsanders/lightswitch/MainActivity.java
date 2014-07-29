package nl.tomsanders.lightswitch;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;

public class MainActivity extends Activity implements SensorEventListener {
	private View sensorView;
	private WakeLock wakeLock;
	
	private static final long MAX_GESTURE_DURATION = 2000;
	private boolean gestureInProgress = false;
	private long gestureInitTime;
	
	private HueEndpoint hue;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
	    sensorView = new RelativeLayout(this);
        sensorView.setBackgroundColor(Color.BLACK);
        
        setContentView(sensorView);
        
        PowerManager powerManager = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nl.tomsanders.lightswitch");
        wakeLock.acquire();
        
        SensorManager sensorManager = (SensorManager)this.getSystemService(Context.SENSOR_SERVICE);
        Sensor proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        
        if (proximitySensor != null) {
        	sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        
        new ConnectTask().execute();
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// Care.
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		float proximity = event.values[0];

		if (proximity < event.sensor.getMaximumRange()) {
			if (!gestureInProgress) {
				gestureInProgress = true;
				gestureInitTime = System.currentTimeMillis();
			}
			
			sensorView.setBackgroundColor(Color.WHITE);
		} else {
			if (gestureInProgress) {
				gestureInProgress = false;
				if (System.currentTimeMillis() <= gestureInitTime + MAX_GESTURE_DURATION) {
					performGestureAction();
				}
			}
			sensorView.setBackgroundColor(Color.BLACK);
		}
		sensorView.invalidate();
	}

	private void performGestureAction() {
		Log.v("nl.tomsanders.lightswitch", "Activated!");
		if (hue != null) {
			new ToggleLightsTask().execute();
		}
	}
	
	private class ConnectTask extends AsyncTask<Void, Void, HueEndpoint> {
		private ProgressDialog dialog;
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			
			dialog = new ProgressDialog(MainActivity.this);
			dialog.setMessage("Connecting to bridge...");
			dialog.setIndeterminate(false);
			dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			dialog.setCancelable(false);
			dialog.show();
		}
		
		@Override
		protected HueEndpoint doInBackground(Void... params) {
			HueEndpoint hue = HueEndpoint.getDefaultEndpoint();
	        if (hue != null) {
	        	hue.setUser("lightswitch");
	        	Log.v("nl.tomsanders.lightswitch", "Connecting to Hue bridge: " + hue);
	        	if (!hue.isAuthorized()) {
	        		hue.authorize();
	        	}
	        }
	        
	        return hue;
		}
		
		@Override
		protected void onPostExecute(HueEndpoint result) {
			MainActivity.this.hue = result;
			dialog.dismiss();
			
			Log.v("nl.tomsanders.lightswitch", "Connected");
		}
	}
	
	private class ToggleLightsTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			// Turn all the lights off
			for (HueEndpoint.HueLight light : hue.getLights()) {
				Log.v("nl.tomsanders.lightswitch", "Turning off light " + light);
	    		light.setOn(false);
	    	}
			return null;
		}
	}
}
