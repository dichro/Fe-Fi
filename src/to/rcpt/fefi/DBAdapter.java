package to.rcpt.fefi;

import java.io.IOException;

import to.rcpt.fefi.eyefi.Types.MacAddress;
import to.rcpt.fefi.eyefi.Types.UploadKey;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

public class DBAdapter extends SQLiteOpenHelper {
	private static final String UPLOADS = "uploads";
	private static final String CARDS = "cameras";
	private static final String TAG = "DBAdapter";
	protected SQLiteDatabase dbh;
	
	private static final String[] idColumns = { "_id" };
	
	public static DBAdapter make(Context c) {
		DBAdapter adapter = new DBAdapter(c);
		adapter.dbh = adapter.getWritableDatabase();
		try {
			String cmd = "/system/bin/chmod 0664 " + adapter.dbh.getPath();
			Log.d(TAG, "trying " + cmd);
			Process p = Runtime.getRuntime().exec(cmd);
			Log.d(TAG, "waiting");
			p.waitFor();
			Log.d(TAG, "done");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return adapter;
	}
	
	protected DBAdapter(Context c) {
		super(c, "FeFi", null, 1);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.e(TAG, "creating DB");
		db.execSQL("CREATE TABLE " + CARDS + " (" +
				"_id INTEGER PRIMARY KEY AUTOINCREMENT," +
				"name TEXT," +
				"uploadKey TEXT NOT NULL," +
				"macAddress TEXT NOT NULL);");
		db.execSQL("CREATE UNIQUE INDEX macAddress " +
				"ON " + CARDS + "(macAddress);");
		db.execSQL("CREATE TABLE " + UPLOADS + " (" +
				"_id INTEGER PRIMARY KEY AUTOINCREMENT," +
				"fileSignature TEXT NOT NULL," +
				"updated INT," +
				"name TEXT," +
				"imageUri TEXT," +
				"log TEXT," +
				"status INT," +
				"path TEXT);");
		db.execSQL("CREATE UNIQUE INDEX fileSignature " +
				"ON " + UPLOADS + "(fileSignature);");
		Log.e(TAG, "DB creation done");
	}

	@Override
	public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
		Log.e(TAG, "upgrade requested from " + arg1 + " to " + arg2);
	}
	
	public long imageExists(String fileSignature) {
		Cursor c = dbh.query(UPLOADS, idColumns, "fileSignature = ?", 
				new String[] { fileSignature }, null, null, null);
		int ret = -1;
		if(c.moveToFirst()) {
			ret = c.getInt(c.getColumnIndex("_id"));			
		}
		Log.d(TAG, "imageExists " + fileSignature + " = " + ret);
		c.close();
		return ret;
	}
	
	public long imageUploadable(String fileSignature) {
		Cursor c = dbh.query(UPLOADS, new String[] { "_id", "status" }, "fileSignature = ?", 
				new String[] { fileSignature }, null, null, null);
		long ret = -1;
		if(c.moveToFirst() && (0 == c.getInt(c.getColumnIndex("status")))) {
			ret = c.getInt(c.getColumnIndex("_id"));
		}
		Log.d(TAG, "imageUploadable " + fileSignature + " = " + ret);
		c.close();
		return ret;
	}

	private static IncomingImagesActivity observer = null;
	
	public void setImageObserver(IncomingImagesActivity observer) {
		DBAdapter.observer = observer;
	}
	
	public long registerNewImage(String fileSignature) {
		ContentValues cv = new ContentValues();
		cv.put("fileSignature", fileSignature);
		cv.put("updated", System.currentTimeMillis());
		cv.put("status", 0);
		Log.d(TAG, "registering new image " + fileSignature);
		long ret = dbh.insertOrThrow(UPLOADS, null, cv);
		if(observer != null)
			observer.notifyImage();
		return ret;	
	}
	
	public void receiveImage(long id, String name, String path) {
		ContentValues cv = new ContentValues();
		cv.put("name", name);
		cv.put("path", path);
		cv.put("status", 1);
		cv.put("updated", System.currentTimeMillis());
		Log.d(TAG, "updating image " + id + ": " + name + " path " + path);
		dbh.update(UPLOADS, cv, "_id = ?", new String[] { id + "" });
		if(observer != null)
			observer.notifyImage();
	}
	
	public void finishImage(long id, Uri uri) {
		ContentValues cv = new ContentValues();
		cv.put("status", 2);
		cv.put("imageUri", uri.toString());
		Log.d(TAG, "done with image " + id + "@" + uri.toString());
		dbh.update(UPLOADS, cv, "_id = ?", new String[] { id + "" });
		if(observer != null)
			observer.notifyImage();		
	}

	public long addNewKeyWithMac(MacAddress mac, UploadKey key) {
		ContentValues cv = new ContentValues();
		cv.put("uploadKey", key.toString());
		cv.put("macAddress", mac.toString());
		return dbh.insertOrThrow(CARDS, null, cv);
	}
	
	public UploadKey getUploadKeyForMac(MacAddress mac) {
		Cursor c = dbh.query(CARDS, new String[] { "uploadKey" }, "macAddress = ?", 
				new String[] { mac.toString() }, null, null, null);
		try {
			if(!c.moveToFirst()) {
				c.close();
				// TODO: throw something
				return null;
			}
			String uploadKey = c.getString(c.getColumnIndex("uploadKey"));
			return new UploadKey(uploadKey);
		} finally {
			c.close();
		}
	}
	
	public Cursor getUploads() {
		Cursor c = dbh.query(UPLOADS, new String[] { "_id", "name", "updated", "status", "imageUri" }, 
				null, null, null, null, "updated DESC");
		return c;
	}
	
	public Cursor getCards() {
		Cursor c = dbh.query(CARDS, new String[] { "_id", "name", "macAddress" }, 
				null, null, null, null, null);
		return c;
	}
	
	public Cursor getCard(long id) {
		Cursor c = dbh.query(CARDS, new String[] { "_id", "name", "macAddress" }, 
				"_id = ?", new String[] { id + "" }, null, null, null);
		return c;
	}
	
	public void deleteCard(int id) {
		dbh.delete(CARDS, "_id = ?", new String[] { id + "" });
	}
	
	public void updateCardName(long id, String name) {
		ContentValues cv = new ContentValues();
		cv.put("name", name);
		dbh.update(CARDS, cv, "_id = ?", new String[] { id + "" });
	}
}