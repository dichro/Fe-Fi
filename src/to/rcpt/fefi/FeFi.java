package to.rcpt.fefi;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;

import to.rcpt.fefi.eyefi.Types.MacAddress;
import to.rcpt.fefi.eyefi.Types.UploadKey;


import android.app.Activity;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ToggleButton;

public class FeFi extends Activity implements Runnable, OnClickListener {
	private ServerSocket eyefiSocket;
	public static final String TAG = "FeFi";
	private ToggleButton button;

	public void onClick(View arg0) {
		if (button.isChecked()) {
			testKey = new UploadKey(textKey.getEditableText().toString());
			textKey.setEnabled(false);
		} else {
			testKey = null;
			textKey.setEnabled(true);
		}
	}

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
		button = (ToggleButton) findViewById(R.id.togglebutton);
		button.setOnClickListener(this);
		textKey = (EditText)findViewById(R.id.edittext);
		DigitsKeyListener dkl = DigitsKeyListener.getInstance("0123456789abcdefABCDEF");
		textKey.getEditableText().setFilters(new InputFilter[] { dkl });
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
		
		public UnknownMacUpdater(MacAddress mac) {
			this.mac = mac.toString();
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
	private UploadKey testKey = null;
	private EditText textKey;
	
	public UploadKey registerUnknownMac(MacAddress mac) {
		runOnUiThread(new UnknownMacUpdater(mac));
		return testKey;
	}
}
