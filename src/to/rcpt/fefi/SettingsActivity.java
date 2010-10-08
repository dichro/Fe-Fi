package to.rcpt.fefi;

import java.util.Date;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;

public class SettingsActivity extends Activity {
	private FefiPreferences prefs;
	
	public static class FefiPreferences {
		private SharedPreferences preferences;
		private Context context;
		
		public FefiPreferences(Context c) {
			context = c;
			preferences = c.getSharedPreferences("default", MODE_PRIVATE);
		}
		
		public void setOnBoot(boolean onBoot) {
			preferences.edit().putBoolean("onBoot", onBoot).commit();
		}
		
		public boolean getOnBoot() {
			return preferences.getBoolean("onBoot", false);
		}
		
		public void setFolderCode(int code) {
			preferences.edit().putInt("folder", code).commit();
		}
		
		public int getFolderCode() {
			return preferences.getInt("folder", 0);
		}
		
		String[] folderNames = { "", "yyyy-MM", "yyyy-MM-dd" };
		
		public String getFolderName(Date d) {
			String base = "Eye-Fi";
			int mode = getFolderCode();
			if(mode == 0)
				return base;
			return base + " " + DateFormat.format(folderNames[mode], d);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings);
	
		prefs = new FefiPreferences(this);
		
		CheckBox onBoot = (CheckBox) findViewById(R.id.on_boot);
		onBoot.setSelected(prefs.getOnBoot());
		onBoot.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				prefs.setOnBoot(v.isSelected());
			}
		});
		
		Spinner folder = (Spinner) findViewById(R.id.save_folder);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, 
				R.array.folder_items, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		folder.setAdapter(adapter);
		folder.setSelection(prefs.getFolderCode());
		folder.setOnItemSelectedListener(new OnItemSelectedListener() {

			public void onItemSelected(AdapterView<?> arg0, View arg1,
					int pos, long id) {
				prefs.setFolderCode(pos);
			}

			public void onNothingSelected(AdapterView<?> arg0) {
				// TODO Auto-generated method stub
				
			}
		});
	}
	
}
