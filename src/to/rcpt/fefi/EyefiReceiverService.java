package to.rcpt.fefi;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import to.rcpt.fefi.eyefi.Types.MacAddress;
import to.rcpt.fefi.eyefi.Types.UploadKey;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

public class EyefiReceiverService extends Service implements Runnable {
	private ServerSocket eyefiSocket;
	public static final String TAG = "EyefiReceiverService";
	private PowerManager.WakeLock wakeLock;

	public void run() {
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

	public void onCreate() {
		super.onCreate();
		Log.d(TAG, "onCreate");
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Fe-Fi");
		wakeLock.acquire();
		try {
			// TODO: retry below to avoid NPE
			// in fact, just move all the below (with retry for network, but including DB.make
			// into start of run()
			eyefiSocket = new ServerSocket(59278);
			eyefiSocket.setReuseAddress(true);
		} catch (IOException e) {
			e.printStackTrace();
		}
		new Thread(this).start();
		db = DBAdapter.make(this);
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
		wakeLock.release();
		db.close();
		try {
			eyefiSocket.close();
		} catch (Exception e) {
			// TODO: care? no? Log something?
			Log.d(TAG, "onDestroy exception " + e);
		}
	}
}
