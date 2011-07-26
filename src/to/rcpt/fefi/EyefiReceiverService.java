package to.rcpt.fefi;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

import to.rcpt.fefi.eyefi.Types.MacAddress;
import to.rcpt.fefi.eyefi.Types.UploadKey;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public class EyefiReceiverService extends Service implements Runnable {
	private ServerSocket eyefiSocket;
	public static final String TAG = "EyefiReceiverService";

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
		Log.d(TAG, "Listening on incoming port");
		while (!eyefiSocket.isClosed()) { // .isBound() remains true after closing?
			try {
				Socket eyefiClient = eyefiSocket.accept();
				Log.i(TAG, "connection from " + eyefiClient.getRemoteSocketAddress());
				EyefiServerConnection esc = EyefiServerConnection.makeConnection(this, eyefiClient);
				new Thread(esc).start();
			} catch (IOException e) {
				// TODO: handle exception
				e.printStackTrace();
			}
		}
		stopSelf();
	}

	public class Unlock extends TimerTask {
		PowerManager.WakeLock wakeLock;
		
		public Unlock(PowerManager.WakeLock wakeLock) {
			this.wakeLock = wakeLock;
		}

		@Override
		public void run() {
			Log.i(TAG, "unlocking");
			wakeLock.release();
			wakeLock = null;
		}		
	}
	
	public class WifiWatcher extends BroadcastReceiver {
		Timer t;
		
		public WifiWatcher() {
			t = new Timer("wakelock unlocker", true);
		}
		
		@Override
		public void onReceive(Context context, Intent arg1) {
			Log.i(TAG, "received network broadcast");
			NetworkInfo networkInfo = (NetworkInfo) arg1.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
			if(networkInfo.getType() != ConnectivityManager.TYPE_WIFI)
				return;
			Log.i(TAG, "locking");
			PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
			PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Fe-Fi");
			t.schedule(new Unlock(wakeLock), 120000);
		}
		
		public void shutdown() {
			t.cancel();
		}
	}
	
	private WifiWatcher receiver;
	
	public void onCreate() {
		super.onCreate();
		Log.d(TAG, "onCreate");
		new Thread(this).start();
		receiver = new WifiWatcher();
		registerReceiver(receiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
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
		receiver.shutdown();
		unregisterReceiver(receiver);
		receiver = null;
		db.close();
		try {
			eyefiSocket.close();
		} catch (Exception e) {
			// TODO: care? no? Log something?
			Log.d(TAG, "onDestroy exception " + e);
		}
	}
}
