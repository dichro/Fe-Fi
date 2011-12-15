package to.rcpt.fefi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.Media;
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
	
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode) {
		case 0:
			if(resultCode == 1)
				enableGeotagging();
		}
	}
	
	boolean checkBetaLicense(int requestCode, String feature) {
		Log.d(TAG, "checkBetaLicense " + requestCode + " " + feature);
		Intent i = new Intent();
		i.setComponent(new ComponentName("to.rcpt.license.fefi", "to.rcpt.license.fefi.LicensingActivity"));
		i.setAction("to.rcpt.license.CHECK");
		i.putExtra("to.rcpt.license.feature", feature);
		i.putExtra("to.rcpt.license.version", 1);
		try {
			Log.d(TAG, "sending license intent");
			startActivityForResult(i, requestCode);
			Log.d(TAG, "sent intent");
		} catch(ActivityNotFoundException e) {
			Log.d(TAG, "activity not found");
			AlertDialog.Builder b = new AlertDialog.Builder(this);
			b.setMessage("You need to buy a beta license from the market to use this feature.")
			 .setPositiveButton("Tell Me More?", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=to.rcpt.license.fefi")));
				}
			 })
			 .setNegativeButton("Never mind!", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			}).show();
		}
		Log.d(TAG, "returning false");
		return false;
	}
	
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

		CheckBox b = (CheckBox)findViewById(R.id.enable_geotag);
		b.setChecked(preferences.getBoolean("enable_geotag", false));

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
		if(b.isChecked()) {
			if(!checkBetaLicense(0, "enableGeotagging"))
				b.setChecked(false);
		} else
			preferences.edit().putBoolean("enable_geotag", false).commit();
	}
	
	private void enableGeotagging() {
		CheckBox b = (CheckBox)findViewById(R.id.enable_geotag);
		b.setChecked(true);
		preferences.edit().putBoolean("enable_geotag", true).commit();
	}
	
	public void clearSavedLocations(View v) {
		Log.d(TAG, "clearSaved");
	}
	
	private static final int REDO = 0;
	private static final int COUNT = 1;
	private static final int UPDATE = 2;
	private static final int RESULT = 3;
	
	public void redoGeotags(View v) {
		showDialog(REDO);
	}
	
	final Handler countHandler = new Handler() {
		public void handleMessage(Message msg) {
			progressDialog.setProgress(msg.arg1);
		}
	};
	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch(id) {
		case REDO:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("Apply geotags to saved photos?")
			.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					showDialog(COUNT);
				}
			})
			.setNegativeButton("No", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			});
			return builder.create();
		case COUNT:
			ProgressDialog cd = new ProgressDialog(this);
			cd.setMessage("Scanning photos...");
			cd.setCancelable(false);
			return cd;
		case UPDATE:
			progressDialog = new ProgressDialog(this);
			progressDialog.setMessage("Updating geotags...");
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			return progressDialog;
		case RESULT:
			builder = new AlertDialog.Builder(this);
			builder.setMessage("Found " + next.ok + " locations in " + next.done + " images")
			.setNegativeButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					removeDialog(RESULT);
				}
			});
			return builder.create();
		default:
			return null;
		}
	}
	
	private ProgressDialog progressDialog = null;

	@Override
	protected void onPrepareDialog(int id, Dialog d) {
		switch(id) {
		case COUNT:
			new ImageCounter().start();
			break;
		case UPDATE:
			progressDialog.setProgress(0);
			progressDialog.setMax(next.getCount());
			next.start();
			break;
		}
	}

	private GeotagUpdater next;
	
	private class ImageCounter extends Thread {
		@Override
		public void run() {
			final Cursor c = managedQuery(Media.EXTERNAL_CONTENT_URI, new String[] { "_id", Images.Media.DATE_TAKEN }, 
					Images.Media.DATE_TAKEN + " IS NOT NULL AND " + Images.Media.LATITUDE + " IS NULL", null, null);
			if(!c.moveToFirst()) {
				runOnUiThread(new Runnable() {
					public void run() {
						dismissDialog(COUNT);
						// TODO(dichro): report the good news?
					}				
				});
				return;
			}
			runOnUiThread(new Runnable() {
				public void run() {
					dismissDialog(COUNT);
					next = new GeotagUpdater(c);
					showDialog(UPDATE);
				}				
			});
		}
	}
	
	private class GeotagUpdater extends Thread {
		private Cursor c;
		private int ok = 0, done = 0;
		
		GeotagUpdater(Cursor c) {
			this.c = c;
		}
		
		@Override
		public void run() {
			int index = c.getColumnIndex(Images.Media.DATE_TAKEN);
			long lastUpdate = System.currentTimeMillis();
			do {
				long ctm = System.currentTimeMillis();
				if(ctm - 100 > lastUpdate) {
					lastUpdate = ctm;
					Message m = countHandler.obtainMessage();
					m.arg1 = done;
					countHandler.sendMessage(m);
				}
				long timestamp = c.getLong(index);
				Cursor pos = db.findNearestLocation(timestamp, window * 1000);
				done++;
				if(pos == null)
					continue;
				ok++;
				pos.close();
			} while(c.moveToNext());
			runOnUiThread(new Runnable() {
				public void run() {
					dismissDialog(UPDATE);
					showDialog(RESULT);
				}
			});
		}
		
		int getCount() {
			return c.getCount();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		db.close();
	}
}
