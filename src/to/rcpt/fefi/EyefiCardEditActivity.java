package to.rcpt.fefi;

import to.rcpt.fefi.DBAdapter.Card;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class EyefiCardEditActivity extends Activity {
	private TextView name, offset, stored;
	private DBAdapter db;
	private long id;
	private static final String TAG = "EyefiCardEditActivity";
	private Card card;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "started edit ");
		setContentView(R.layout.card_edit);
		Intent i = getIntent();
		id = i.getLongExtra("id", -1);
		db = DBAdapter.make(this);
		card = db.getCardO(id);
		Cursor c = db.getCard(id);
		startManagingCursor(c);
		if(!c.moveToFirst()) {
			Log.d(TAG, "nothing to edit found on id " + id + ", abandoning");
			finish();
			return;
		}
		name = (TextView)findViewById(R.id.card_name);
		name.setText(c.getString(c.getColumnIndex("name")));
		offset = (TextView)findViewById(R.id.cardedit_offset);
		offset.setText("" + c.getInt(c.getColumnIndex("offset")));
		stored = (TextView)findViewById(R.id.cardedit_stored);
		stored.setText("" + card.getStoredCount());
		TextView mac = (TextView)findViewById(R.id.card_macaddress);
		mac.setText(c.getString(c.getColumnIndex("macAddress")));
		Button save = (Button)findViewById(R.id.save);
		save.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				String nameValue = name.getEditableText().toString();
				int offsetValue = 0;
				try {
					offsetValue = Integer.parseInt(offset.getText().toString());
				} catch (NumberFormatException e) {
				}
				db.updateCard(id, nameValue, offsetValue);
				EyefiCardEditActivity.this.finish();
			}
		});
	}
	
	public void recalculateOffset(View v) {
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		db.close();
	}
}
