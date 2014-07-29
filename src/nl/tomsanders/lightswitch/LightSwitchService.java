package nl.tomsanders.lightswitch;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

public class LightSwitchService extends Service implements SensorEventListener {

	private WakeLock wakeLock;
	private HueEndpoint hue;
	
	private static final long MAX_GESTURE_DURATION = 1000;
	private boolean gestureInProgress = false;
	private long gestureInitTime;
	
	private final int notificationId = 1;
	
	@Override
	public IBinder onBind(Intent intent) {
		// Binding not supported
		return null;
	}

	@Override
	public void onCreate() {
		Log.v("nl.tomsanders.lightswitch", "Starting service");

        // Connect to Hue
        new ConnectTask().execute();
        
     // Start in foreground to keep service active
        Notification notification = new Notification.Builder(this.getApplicationContext())
			.setContentTitle("Light Switch")
	    	.setContentText("Connecting...")
	    	.setSmallIcon(R.drawable.ic_launcher)
	    	.setOngoing(true).build();
        this.startForeground(this.notificationId, notification);
	}
	
	public void onConnectionEstablished(HueEndpoint hue) {
		this.hue = hue;

        // Start monitoring proximity sensor
        SensorManager sensorManager = (SensorManager)this.getSystemService(Context.SENSOR_SERVICE);
        Sensor proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        
        if (proximitySensor == null) {
        	Log.e("nl.tomsanders.lightswitch", "Device does not have a proximity sensor; stopping");
        	this.stopSelf();
        	return;
        }
        
        Log.v("nl.tomsanders.lightswitch", "Registering sensor event listener");
        sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        
        // Acquire (partial) wake lock to monitor the proximity sensor even when
     	// the screen is turned off
     	PowerManager powerManager = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nl.tomsanders.lightswitch");
        wakeLock.acquire();
        
        NotificationManager notificationManager = (NotificationManager)
				getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = new Notification.Builder(this.getApplicationContext())
			.setContentTitle("Light Switch")
	    	.setContentText("Connected to " + hue)
	    	.setSmallIcon(R.drawable.ic_launcher)
	    	.setOngoing(true).build();
        notificationManager.notify(this.notificationId, notification);
	}
	
	public void onNotAuthorized() {
		// Show notification asking the user to authorize with the Hue bridge
		Intent resultIntent = new Intent(this, ConnectActivity.class);
		resultIntent.putExtra("username", "lightswitch");
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, 
				resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		
		Notification notification = new Notification.Builder(this)
			.setContentTitle("Light Switch")
			.setContentText("Tap to authorize with your Hue bridge")
			.setSmallIcon(R.drawable.ic_launcher)
			.setContentIntent(pendingIntent)
			.build();
			
		NotificationManager notificationManager = (NotificationManager)
				getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(this.notificationId, notification);
	}
	
	public void onConnectionFailure() {
		Notification notification = new Notification.Builder(this)
			.setContentTitle("Light Switch")
			.setContentText("Could not connect to Hue bridge")
			.setSmallIcon(R.drawable.ic_launcher)
			.build();
			
		NotificationManager notificationManager = (NotificationManager)
				getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(this.notificationId, notification);
		
		this.stopSelf();
	}

	@Override
	public void onDestroy() {
		Log.v("nl.tomsanders.lightswitch", "Stopping service");
		
		// Stop monitoring the proximity sensor
		SensorManager sensorManager = (SensorManager)this.getSystemService(Context.SENSOR_SERVICE);
		sensorManager.unregisterListener(this);
		
		// Release the wake lock
		if (wakeLock != null && wakeLock.isHeld()) {
			wakeLock.release();
		}
	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// Care.
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		float proximity = event.values[0];

		if (proximity < event.sensor.getMaximumRange()) {
			// Hand above proximity sensor
			if (!gestureInProgress) {
				// Start gesture
				gestureInProgress = true;
				gestureInitTime = System.currentTimeMillis();
			}
		} else {
			// Hand not above proximity sensor
			if (gestureInProgress) {
				// Finish gesture if hand was previously over sensor
				gestureInProgress = false;
				if (System.currentTimeMillis() <= gestureInitTime + MAX_GESTURE_DURATION) {
					performGestureAction();
				}
			}
		}
	}

	private void performGestureAction() {
		Log.v("nl.tomsanders.lightswitch", "Activated!");
		new ToggleLightsTask().execute();
	}
	
	private static enum ConnectTaskResult {
		CONNECTED,
		BRIDGE_NOT_FOUND,
		NOT_AUTHORIZED
	}
	
	private class ConnectTask extends AsyncTask<Void, Void, ConnectTaskResult> {
		private HueEndpoint hue;
		
		@Override
		protected ConnectTaskResult doInBackground(Void... params) {
			
	        hue = HueEndpoint.getDefaultEndpoint();
	        if (hue == null) {
	        	return ConnectTaskResult.BRIDGE_NOT_FOUND;
	        }
	        	
        	hue.setUser("lightswitch");
        	if (!hue.isAuthorized()) {
        		return ConnectTaskResult.NOT_AUTHORIZED;
        	}
	        return ConnectTaskResult.CONNECTED;
		}
		
		@Override
		protected void onPostExecute(ConnectTaskResult result) {
			if (result == ConnectTaskResult.BRIDGE_NOT_FOUND) {
				LightSwitchService.this.onConnectionFailure();
			} else if (result == ConnectTaskResult.NOT_AUTHORIZED) {
				LightSwitchService.this.onNotAuthorized();
			} else {
				LightSwitchService.this.onConnectionEstablished(hue);
			}
		}
	}
	
	private class ToggleLightsTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			
			// Turn off lights
			if (hue != null) {
				for (HueEndpoint.HueLight light : hue.getLights()) {
					Log.v("nl.tomsanders.lightswitch", "Turning off light " + light);
		    		light.setOn(false);
		    	}
			}
			
			return null;
		}
	}

}
