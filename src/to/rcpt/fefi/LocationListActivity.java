package to.rcpt.fefi;

import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.SimpleCursorAdapter;

public class LocationListActivity extends ListActivity {
	DBAdapter db;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.locations_list);
		db = DBAdapter.make(this);
		Cursor c = db.getLocations();
		startManagingCursor(c);
		SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, R.layout.location_list_item, c,
				new String[] { "latitude", "longitude", "altitude", "accuracy", "fixtime" },
				new int[] { R.id.latitude, R.id.longitude, R.id.altitude, R.id.accuracy, R.id.fixtime });
		setListAdapter(adapter);
	}
	
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	db.close();
    }
}
