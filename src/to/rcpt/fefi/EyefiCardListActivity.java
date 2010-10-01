package to.rcpt.fefi;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class EyefiCardListActivity extends ListActivity {
    private DBAdapter db;
    private static final String TAG = "EyefiCardActivity";
	private Cursor c;
	private SimpleCursorAdapter adapter;

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
        db = DBAdapter.make(this);
        c = db.getCards();
        startManagingCursor(c);
        adapter = new SimpleCursorAdapter(this, R.layout.card_control_item, c,
        		new String[] { "name", "macAddress" }, new int[] { R.id.name, R.id.macaddress });
		setListAdapter(adapter);
        registerForContextMenu(getListView());
    }
    
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
}
