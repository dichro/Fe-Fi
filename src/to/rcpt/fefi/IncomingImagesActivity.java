package to.rcpt.fefi;

import java.util.Date;

import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Images;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

/*
 * TODO(dichro):
 *   only show completed images
 *   view per-image log data
 *   show timestamp offset to closest stored location
 *   indicate whether gps location is stored in content provider
 *   allow push of closest gps location into content provider
 *     allow push of all closest gps locations (matching some restriction criteria?) into location provider
 *   
 */
public class IncomingImagesActivity extends ListActivity {
	private class IncomingImageViewBinder implements
			SimpleCursorAdapter.ViewBinder {
		private ContentResolver cr;
		String projection[] = { Images.ImageColumns.DATE_TAKEN };

		IncomingImageViewBinder() {
			cr = getContentResolver();		
		}
		
		public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
			int index = cursor.getColumnIndex(IMAGES_UPDATED);
			if(columnIndex == index) {
				TextView tv = (TextView) view;
				long timestamp = cursor.getLong(index);
				Date d = new Date(timestamp);
				tv.setText(DateFormat.format("yyyy-MM-dd kk:mm:ss", d));
				return true;
			}
			if(columnIndex == cursor.getColumnIndex("imageUri")) {
				TextView tv = (TextView) view;
				String uriString = cursor.getString(columnIndex);
				if(uriString == null) {
					tv.setText("null URI?");
					return true;
				}
				Uri uri = Uri.parse(uriString);
				// error for the above?
				Cursor c = cr.query(uri, projection, null, null, null);
				if(c.moveToFirst()) {
					tv.setText(DateFormat.format("yyyy-MM-dd kk:mm:ss", c.getLong(c.getColumnIndex(Images.ImageColumns.DATE_TAKEN))));
				} else {
					tv.setText("No date found.");
				}
				c.close();
				return true;
			}
			return false;
		}
	}

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
        		new String[] { "name", IMAGES_UPDATED, "imageUri" },
        		new int[] { R.id.name, R.id.received, R.id.timestamp });
        adapter.setViewBinder(new IncomingImageViewBinder());
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
