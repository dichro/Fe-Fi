package to.rcpt.fefi;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

public class GeotagActivity extends Activity {
	private static final String TAG = "Geotag";
	private DBAdapter db;
	private SharedPreferences preferences;
	private int window;
	private TextView offset;
	private ImageButton save;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        db = DBAdapter.make(this);
		setContentView(R.layout.tab_geotag);
		preferences = getPreferences(MODE_PRIVATE);
		window = preferences.getInt("geotag_window", 900);
		
		TextView count = (TextView)findViewById(R.id.numLocations);
		count.setText(db.countLocations() + "");
		
		save = (ImageButton)findViewById(R.id.save);
		save.setEnabled(false);

		offset = (TextView)findViewById(R.id.geotag_offset);
		offset.setText("" + window);
		offset.addTextChangedListener(new TextWatcher() {			
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}
			
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}
			
			public void afterTextChanged(Editable s) {
				try {
					int i = Integer.parseInt(s.toString());
					save.setEnabled(i != window);
				} catch (NumberFormatException e) {
					if(s.length() != 0)
						offset.setText("" + window);
					save.setEnabled(false);
				}
			}
		});
	}
	
	public void savePreferences(View v) {
		try {
			window = Integer.parseInt(offset.getText().toString());
			save.setEnabled(false);
			preferences.edit().putInt("geotag_window", window).commit();
		} catch (NumberFormatException e) {
			offset.setText("" + window);
			save.setEnabled(false);
		}
	}
	
	public void enableGeotagging(View v) {
		CheckBox b = (CheckBox)v;
		Log.d(TAG, "enableGeo " + b.isChecked());
	}
	
	public void clearSavedLocations(View v) {
		Log.d(TAG, "clearSaved");
	}
	
	public void redoGeotags(View v) {
		Log.d(TAG, "redoGeo");
	}
}
