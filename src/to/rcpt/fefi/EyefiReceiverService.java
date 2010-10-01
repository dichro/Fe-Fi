package to.rcpt.fefi;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import to.rcpt.fefi.eyefi.Types.MacAddress;
import to.rcpt.fefi.eyefi.Types.UploadKey;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class EyefiReceiverService extends Service implements Runnable {
	private ServerSocket eyefiSocket;
	public static final String TAG = "EyefiReceiverService";

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
		try {
			eyefiSocket = new ServerSocket(59278);
		} catch (IOException e) {
			e.printStackTrace();
		}
		new Thread(this).start();
	}
	
	private EyefiCardScanActivity testKeyHelper = null;
	
	public class RelayBinder extends Binder {
		void setKeyHelper(EyefiCardScanActivity helper) {
			testKeyHelper = helper;
		}
	}
	
	private RelayBinder binder = new RelayBinder();
	
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
		try {
			eyefiSocket.close();
		} catch (Exception e) {
			// TODO: care? no? Log something?
			Log.d(TAG, "onDestroy exception " + e);
		}
	}
}
