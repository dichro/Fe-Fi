package to.rcpt.fefi;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DBAdapter extends SQLiteOpenHelper {
	private static final String TAG = "DBAdapter";
	protected SQLiteDatabase dbh;
	
	private static final String[] idColumns = { "_id" };
	
	public static DBAdapter make(Context c) {
		DBAdapter adapter = new DBAdapter(c);
		adapter.dbh = adapter.getWritableDatabase();
		return adapter;
	}
	
	protected DBAdapter(Context c) {
		super(c, "FeFi", null, 1);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.e(TAG, "creating DB");
		db.execSQL("CREATE TABLE cameras (" +
				"_id INTEGER PRIMARY KEY AUTOINCREMENT," +
				"name TEXT NOT NULL," +
				"uploadKey TEXT NOT NULL," +
				"macAddress TEXT NOT NULL);");
		db.execSQL("CREATE UNIQUE INDEX macAddress " +
				"ON cameras(macAddress);");
		db.execSQL("CREATE TABLE uploads (" +
				"_id INTEGER PRIMARY KEY AUTOINCREMENT," +
				"fileSignature TEXT NOT NULL," +
				"imageUri TEXT," +
				"log TEXT);");
		db.execSQL("CREATE UNIQUE INDEX fileSignature " +
				"ON uploads(fileSignature);");
		Log.e(TAG, "DB creation done");
	}

	@Override
	public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
		Log.e(TAG, "upgrade requested from " + arg1 + " to " + arg2);
	}
	
	public boolean imageExists(String fileSignature) {
		Cursor c = dbh.query("uploads", idColumns, "fileSignature = ?", 
				new String[] { fileSignature }, null, null, null);
		boolean ret = c.moveToFirst();
		Log.d(TAG, "imageExists " + fileSignature + " = " + ret);
		return ret;
	}
	
	public void addImage(String fileSignature, String imageUri, String log) {
		ContentValues cv = new ContentValues();
		cv.put("fileSignature", fileSignature);
		cv.put("imageUri", imageUri);
		cv.put("log", log);
		Log.d(TAG, "adding image " + fileSignature + " with URI " + imageUri);
		dbh.insertOrThrow("uploads", null, cv);
	}
}