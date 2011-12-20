package to.rcpt.fefi;

import to.rcpt.fefi.DBAdapter.Card;
import to.rcpt.fefi.DBAdapter.Progress;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class EyefiCardEditActivity extends FefiLicensedActivity {
	private TextView name, offset, stored;
	private DBAdapter db;
	private long id;
	private static final String TAG = "EyefiCardEditActivity";
	private Card card;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
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
	
	class Foo extends Handler implements Runnable, Progress {
		ProgressDialog pd;
		long lastUpdate = 0, interval = 100;
		int total = 0, done = 0, ok = 0;

		public void updateProgress(int total, int done, int ok) {
			this.total = total;
			this.done = done;
			this.ok = ok;
			long now = System.currentTimeMillis();
			if((done < total) && (now - interval < lastUpdate))
				return;
			lastUpdate = now;
			Message m = obtainMessage();
			m.arg1 = done;
			m.arg2 = total;
			sendMessage(m);
		}
		
		public void handleMessage(Message m) {
			pd.setProgress(m.arg1);
			if(m.arg1 == m.arg2) {
				dismissDialog(0);
				showDialog(1);
			}
		}
		
		public void run() {
			try {
				card.recalculateMetadata(this);
			} finally {
				runOnUiThread(new Runnable() {
					public void run() {
						dismissDialog(0);
					}
				});
			}
		}
		
		Dialog createProgressDialog(String msg) {
			pd = new ProgressDialog(EyefiCardEditActivity.this);
			pd.setMessage(msg);
			pd.setCancelable(false);
			pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			return pd;
		}
		
		void prepareProgressDialog() {
			pd.setProgress(0);
			pd.setMax(card.getStoredCount());
			new Thread(this).start();
		}
		
		Dialog createSummaryDialog(final int id) {
			AlertDialog.Builder b = new AlertDialog.Builder(EyefiCardEditActivity.this);
			b.setMessage(ok + " of " + done + " succeeded");
			b.setNegativeButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					removeDialog(id);
				}
			});
			return b.create();
		}
	}
	
	private Foo foo;
	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch(id) {
		case 0:
			return foo.createProgressDialog("Recalculating metadata");
		case 1:
			return foo.createSummaryDialog(1);
		default:
			return null;
		}
	}

	@Override
	protected void onPrepareDialog(int id, Dialog d) {
		switch(id) {
		case 0:
			foo.prepareProgressDialog();
		}
	}
	
	
	public void recalculateOffsets(View v) {
		if(checkBetaLicense(0, "recalculateGeotags"))
			recalculateOffsets();
	}
	
	private void recalculateOffsets() {
		foo = new Foo();
		showDialog(0);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		db.close();
	}
	
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode != 1)
			// license acquired, but feature disabled
			return;
		switch(requestCode) {
		case 0:
			recalculateOffsets();
			break;
		}
	}
}
