package to.rcpt.fefi;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.SimpleCursorAdapter;
import android.widget.ToggleButton;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class EyefiCardListActivity extends ListActivity implements OnClickListener {
    private static final String ENABLE_SERVICE = "enableService";
	private DBAdapter db;
    private static final String TAG = "EyefiCardActivity";
	private Cursor c;
	private SimpleCursorAdapter adapter;
	private ToggleButton button;
	private Intent serviceIntent;
	private PowerManager.WakeLock wakeLock;
	private SharedPreferences preferences;
	

	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.card_control);
        Button b = (Button)findViewById(R.id.scan_button);
        b.setOnClickListener(new OnClickListener() {		
			public void onClick(View v) {
				Intent i = new Intent().setClass(EyefiCardListActivity.this, EyefiCardScanActivity.class);
				startActivityForResult(i, 0);
			}
		});
        b = (Button)findViewById(R.id.buy_button);
        b.setOnClickListener(new OnClickListener() {		
			public void onClick(View v) {
				Intent i = new Intent().setAction(Intent.ACTION_VIEW);
				i.setData(Uri.parse("http://eyefi.tellapal.com/a/clk/8rl5j"));
				startActivity(i);
			}
		});
        db = DBAdapter.make(this);
        c = db.getCards();
        startManagingCursor(c);
        adapter = new SimpleCursorAdapter(this, R.layout.card_control_item, c,
        		new String[] { "name", "macAddress" }, new int[] { R.id.name, R.id.macaddress });
		setListAdapter(adapter);
        registerForContextMenu(getListView());
		button = (ToggleButton) findViewById(R.id.enable_service);
		button.setOnClickListener(this);
		serviceIntent = new Intent(this, EyefiReceiverService.class);
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Fe-Fi");
		preferences = getPreferences(MODE_PRIVATE);
		if(preferences.getBoolean(ENABLE_SERVICE, false)) {
			button.setChecked(true);
			startReceiver();
		}
    }
    
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo info) {
		super.onCreateContextMenu(menu, v, info);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.layout.card_control_item_menu, menu);
	}
		
	public class CardDeleter implements DialogInterface.OnClickListener {
		private int cardId;
		
		public CardDeleter(int id) {
			cardId = id;
		}
		
		public void onClick(DialogInterface dialog, int which) {
			db.deleteCard(cardId);
			c.requery();
		}
	}
	
	
	protected Dialog onCreateDialog(int id) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Are you sure you want to delete this card?")
		       .setPositiveButton("Yes", new CardDeleter(id))
		       .setNegativeButton("No", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                dialog.cancel();
		           }
		       });
		AlertDialog alert = builder.create();
		return alert;
	}
	
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId()) {
		case R.id.edit:
			launchEditActivity(info.id);
			return true;
		case R.id.delete:
			showDialog((int)info.id);
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		Log.d(TAG, "creating menu");
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.layout.card_control_options, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	    case R.id.settings:
	        Intent i = new Intent().setClass(this, SettingsActivity.class);
			startActivity(i);
	        return true;
	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}

	private void launchEditActivity(long id) {
		Intent i = new Intent().setClass(this, EyefiCardEditActivity.class);
		i.putExtra("id", id);
		startActivity(i);
	}
	
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if(data != null) {
    		Log.d("foo", "got req " + requestCode + " res " + resultCode + " inte " + data + " mac " + data.getStringExtra("mac"));
    		launchEditActivity(data.getLongExtra("id", -1));
    	}
    }
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	db.close();
    }

	public void onClick(View arg0) {
		if (button.isChecked()) {
			startReceiver();
		} else {
			stopReceiver();
		}
	}

	private void stopReceiver() {
		preferences.edit().putBoolean(ENABLE_SERVICE, false).commit();
		wakeLock.release();
		stopService(serviceIntent);
	}

	private void startReceiver() {
		preferences.edit().putBoolean(ENABLE_SERVICE, true).commit();
		startService(serviceIntent);
		wakeLock.acquire();
	}
}
