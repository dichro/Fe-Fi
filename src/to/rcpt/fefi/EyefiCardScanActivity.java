package to.rcpt.fefi;

import java.util.Vector;

import to.rcpt.fefi.EyefiReceiverService.RelayBinder;
import to.rcpt.fefi.eyefi.Types.MacAddress;
import to.rcpt.fefi.eyefi.Types.UploadKey;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputFilter;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ToggleButton;

public class EyefiCardScanActivity extends Activity implements OnClickListener {
	private ToggleButton button;
	public static final String TAG = "EyefiCardScanActivity";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.card_scan);
		ListView lv = (ListView)findViewById(R.id.unknown_mac_list);
		unknownMacs = new ArrayAdapter<String>(this, R.layout.unknown_mac_item, new Vector<String>(5));
		lv.setAdapter(unknownMacs);
		lv.setTextFilterEnabled(true);
		button = (ToggleButton) findViewById(R.id.togglebutton);
		button.setOnClickListener(this);
		textKey = (EditText)findViewById(R.id.edittext);
		DigitsKeyListener dkl = DigitsKeyListener.getInstance("0123456789abcdefABCDEF");
		textKey.getEditableText().setFilters(new InputFilter[] { dkl });
		bindService(new Intent(this, EyefiReceiverService.class), networkService, BIND_AUTO_CREATE);
    }

    protected void onDestroy() {
    	super.onDestroy();
    	unbindService(networkService);
    }
    
    RelayBinder binder = null;
    
    private ServiceConnection networkService = new ServiceConnection() {
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.d(TAG, "service bound");
			binder = (RelayBinder) service;
			binder.setKeyHelper(EyefiCardScanActivity.this);
		}

		public void onServiceDisconnected(ComponentName name) {
			Log.d(TAG, "service unbound");
			binder = null;
		}
    };
    
	public void onClick(View arg0) {
		if (button.isChecked()) {
			testKey = new UploadKey(textKey.getEditableText().toString());
			textKey.setEnabled(false);		
		} else {
			testKey = null;
			textKey.setEnabled(true);
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
	
	class NewMacUpdater implements Runnable {
		String mac;
		
		public NewMacUpdater(MacAddress mac) {
			this.mac = mac.toString();
		}
		
		public void run() {
			Intent i = new Intent();
			i.putExtra("mac", mac.toString());
			setResult(RESULT_OK, i);
			finish();
		}
	}
	
	public void registerNewCard(MacAddress mac, UploadKey uk) {
		testKey = null;
		runOnUiThread(new NewMacUpdater(mac));
	}

}
