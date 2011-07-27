package to.rcpt.fefi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Log;

public class WifiWatcher extends BroadcastReceiver {
	public static final String TAG = "WifiWatcher";
	
	@Override
	public void onReceive(Context context, Intent arg1) {
		WifiManager wifi = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
		WifiInfo info = wifi.getConnectionInfo();
		if(info != null) { 
			String ssid = info.getSSID();
			if((ssid != null) && ssid.contains("Eye-Fi")) {
				Log.i(TAG, "Broadcast received for essid " + ssid + "; waiting for card");
				startup(context);
				return;
			}
		}
		Log.i(TAG, "Broadcast received; ignoring");
	}

	private void startup(Context context) {
		PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, 
				"Fe-Fi WifiWatcher");
		wakeLock.acquire(120000);
		context.startService(new Intent(context, EyefiReceiverService.class));
		Vibrator v = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
		v.vibrate(300);
	}
}