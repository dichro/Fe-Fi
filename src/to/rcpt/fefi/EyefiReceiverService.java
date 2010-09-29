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
		while (true) {
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
	}

	public void onCreate() {
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

	public void registerNewCard(MacAddress mac, UploadKey uk) {
		if(testKeyHelper == null)
			return;
		testKeyHelper.registerNewCard(mac, uk);		
	}

	public UploadKey registerUnknownMac(MacAddress mac) {
		if(testKeyHelper == null)
			return null;
		return testKeyHelper.registerUnknownMac(mac);
	}
}
