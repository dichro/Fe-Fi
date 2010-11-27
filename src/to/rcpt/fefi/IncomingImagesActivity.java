package to.rcpt.fefi;

import java.util.Date;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class IncomingImagesActivity extends ListActivity {
	private static final String IMAGES_UPDATED = "updated";
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
        		new String[] { "name", IMAGES_UPDATED }, new int[] { R.id.name, R.id.received });
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {		
			public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
				TextView tv = (TextView) view;
				int index = cursor.getColumnIndex(IMAGES_UPDATED);
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
    
    @Override
    public void onListItemClick(ListView l, View v, int pos, long id) {
    	super.onListItemClick(l, v, pos, id);
    	Cursor cur = (Cursor)l.getItemAtPosition(pos);
    	int oldpos = cur.getPosition();
    	cur.moveToPosition(pos);
    	String uri = cur.getString(cur.getColumnIndex("imageUri"));
    	if(uri == null)
    		return;
    	Uri u = Uri.parse(uri);
    	if(u == null)
    		return;
    	cur.moveToPosition(oldpos); // do I need to do this?
    	Intent i = new Intent(Intent.ACTION_VIEW, u);
    	startActivity(i);
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
