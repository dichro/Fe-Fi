package to.rcpt.fefi;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;


import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class FeFi extends Activity implements Runnable {
	private ServerSocket eyefiSocket;
	public static final String TAG = "FeFi";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		ListView lv = (ListView)findViewById(R.id.unknown_mac_list);
		unknownMacs = new ArrayAdapter<String>(this, R.layout.unknown_mac_item, new Vector<String>(5));
		lv.setAdapter(unknownMacs);
		lv.setTextFilterEnabled(true);
		try {
			eyefiSocket = new ServerSocket(59278);
		} catch (IOException e) {
			e.printStackTrace();
		}
		new Thread(this).start();
	}

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
	
	class UnknownMacUpdater implements Runnable {
		String mac;
		
		public UnknownMacUpdater(String mac) {
			this.mac = mac;
		}
		
		public void run() {
			int pos = unknownMacs.getPosition(mac);
			if(pos >= 0) {
				return;
			}
			if(unknownMacs.getCount() > 4)
				unknownMacs.remove(unknownMacs.getItem(0));
			unknownMacs.add(mac);
			unknownMacs.notifyDataSetChanged();
		}	
	}
	
	ArrayAdapter<String> unknownMacs;
	public void addUnknownMac(String mac) {
		runOnUiThread(new UnknownMacUpdater(mac));
	}
}
