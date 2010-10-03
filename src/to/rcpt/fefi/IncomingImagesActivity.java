package to.rcpt.fefi;

import java.util.Date;

import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class IncomingImagesActivity extends ListActivity {
	private DBAdapter db;
	private static final String TAG = "IncomingImagesActivity";
	private Cursor c;
	private SimpleCursorAdapter adapter;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DBAdapter.make(this);
        c = db.getUploads();
        startManagingCursor(c);
        adapter = new SimpleCursorAdapter(this, R.layout.incoming_list_item, c,
        		new String[] { "name", "received" }, new int[] { R.id.name, R.id.received });
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
			
			public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
				TextView tv = (TextView) view;
				int index = cursor.getColumnIndex("received");
				if(columnIndex == index) {
					long timestamp = cursor.getLong(index);
					Date d = new Date(timestamp);
					tv.setText(DateFormat.format("yyyy-MM-dd kk:mm:ss", d));
					return true;
				}
				return false;
			}
		});
		setListAdapter(adapter);
    }
    
    public void notifyImage() {
    	runOnUiThread(new Runnable() {
			public void run() {
		    	c.requery();
		    	adapter.notifyDataSetChanged();
			}
		});
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	c.requery();
		db.setImageObserver(this);    	
    }

    @Override
    public void onPause() {
    	super.onPause();
		db.setImageObserver(null);    	
    }

    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	db.close();
    }
}
