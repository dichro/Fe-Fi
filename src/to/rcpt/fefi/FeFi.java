package to.rcpt.fefi;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class FeFi extends Activity implements Runnable {
	private ServerSocket eyefiSocket;
	public static final String TAG = "FeFi";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		Log.e(TAG, "bar2");
		try {
			eyefiSocket = new ServerSocket(59278);
			Log.e(TAG, "bar");
		} catch (IOException e) {
			e.printStackTrace();
		}
		new Thread(this).start();
	}

	public void run() {
		Log.e(TAG, "run()");
		
		while (true) {
			try {
				Socket eyefiClient = eyefiSocket.accept();
				Log.e(TAG, "socket!");
				EyefiServerConnection esc = EyefiServerConnection.makeConnection(this, eyefiClient);
				new Thread(esc).start();
			} catch (IOException e) {
				// TODO: handle exception
				e.printStackTrace();
			}
		}
	}
}
