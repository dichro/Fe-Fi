package to.rcpt.fefi;

import to.rcpt.fefi.eyefi.Types.MacAddress;
import to.rcpt.fefi.eyefi.Types.UploadKey;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ToggleButton;

public class FeFi extends Activity implements OnClickListener {
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
			unknownMacs.remove(mac);
			button.setChecked(false);
			textKey.setEnabled(true);
			textKey.getEditableText().clear();
		}
	}
	
	public void registerNewCard(MacAddress mac, UploadKey uk) {
		testKey = null;
		runOnUiThread(new NewMacUpdater(mac));
	}
}
