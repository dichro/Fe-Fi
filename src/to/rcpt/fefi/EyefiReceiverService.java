package to.rcpt.fefi;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import to.rcpt.fefi.eyefi.Types.MacAddress;
import to.rcpt.fefi.eyefi.Types.UploadKey;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

public class EyefiReceiverService extends Service implements Runnable {
	private ServerSocket eyefiSocket;
	public static final String TAG = "EyefiReceiverService";

	private class GetAFix implements LocationListener, Runnable {
		private PowerManager.WakeLock wakeLock;
		private LocationManager locationManager;
		private Criteria criteria;
		private String provider;
		private DBAdapter db;
		
		GetAFix() {
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Fe-Fi");
			locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
			criteria = new Criteria();
			criteria.setAccuracy(Criteria.ACCURACY_FINE);
			criteria.setPowerRequirement(Criteria.POWER_HIGH);
			List<String> providers = locationManager.getProviders(criteria, true);
			if(providers.size() > 0) {
				provider = providers.get(0);
			} else {
				Log.e(TAG, "Couldn't find a GPS provider, guessing");
				provider = "GPS";
			}
			db = getDatabase();
		}
		
		private boolean updating = false;
		
		public synchronized void poke() {
			Log.d(TAG, "Requesting location updates");
			if(updating)
				return;
			wakeLock.acquire(180000);
			updating = true;
			new Thread(this).start();
		}

		public synchronized void onLocationChanged(Location location) {
			Log.d(TAG, "Got location " + location);
			db.addLocation(location);
			if(!updating)
				return;
			updating = false;
			locationManager.removeUpdates(this);
			if(wakeLock.isHeld())
				wakeLock.release();
			Looper.myLooper().quit(); // this is retarded
		}

		public void onProviderDisabled(String provider) {
			// TODO Auto-generated method stub
		}

		public void onProviderEnabled(String provider) {
			// TODO Auto-generated method stub
		}

		public void onStatusChanged(String provider, int status, Bundle extras) {
			// TODO Auto-generated method stub
		}

		public void run() {
			Looper.prepare();
			locationManager.requestLocationUpdates(provider, 5000, 10, this);
			Looper.loop();
		}
	}
	
	public void run() {
		Log.d(TAG, "Connecting to database");
		db = DBAdapter.make(this);
		Log.d(TAG, "Opening incoming port");
		while (eyefiSocket == null) {
			try {
				eyefiSocket = new ServerSocket(59278);
				eyefiSocket.setReuseAddress(true);
			} catch (IOException e) {
				Log.d(TAG, "Failed to open port, sleeping before retry");
				e.printStackTrace();
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
		}
		GetAFix fix = new GetAFix();
		Log.d(TAG, "Listening on incoming port");
		while (!eyefiSocket.isClosed()) { // .isBound() remains true after closing?
			try {
				Socket eyefiClient = eyefiSocket.accept();
				Log.i(TAG, "connection from " + eyefiClient.getRemoteSocketAddress());
				EyefiServerConnection esc = EyefiServerConnection.makeConnection(this, eyefiClient);
				new Thread(esc).start();
				fix.poke();
			} catch (IOException e) {
				// TODO: handle exception
				e.printStackTrace();
			}
		}
		stopSelf();
	}

	public void onCreate() {
		super.onCreate();
		Log.d(TAG, "onCreate");
		new Thread(this).start();
	}
	
	private EyefiCardScanActivity testKeyHelper = null;
	
	public class RelayBinder extends Binder {
		void setKeyHelper(EyefiCardScanActivity helper) {
			testKeyHelper = helper;
		}
	}
	
	private RelayBinder binder = new RelayBinder();
	private DBAdapter db;
	
	public DBAdapter getDatabase() { return db; }
	
	@Override
	public IBinder onBind(Intent arg0) {
		return binder;
	}

	public void registerNewCard(MacAddress mac, UploadKey uk, long id) {
		if(testKeyHelper == null)
			return;
		testKeyHelper.registerNewCard(mac, uk, id);		
	}

	public UploadKey registerUnknownMac(MacAddress mac) {
		if(testKeyHelper == null)
			return null;
		return testKeyHelper.registerUnknownMac(mac);
	}
	
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");
		db.close();
		try {
			eyefiSocket.close();
		} catch (Exception e) {
			// TODO: care? no? Log something?
			Log.d(TAG, "onDestroy exception " + e);
		}
	}
}
