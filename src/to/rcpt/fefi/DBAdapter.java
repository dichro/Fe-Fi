package to.rcpt.fefi;

import java.io.File;
import java.io.FileWriter;
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
//		try {
////			String cmd = "/system/bin/chmod 0666 " + adapter.dbh.getPath();
////			String cmd = "/system/bin/chmod 0771 /data/data/to.rcpt.fefi/databases";
//			String cmd = "/system/bin/chmod 0660 /data/data/to.rcpt.fefi/databases/FeFi";
//			Log.d(TAG, "trying " + cmd);
//			Process p = Runtime.getRuntime().exec(cmd);
//			Log.d(TAG, "waiting");
//			p.waitFor();
//			Log.d(TAG, "done");
//		} catch (IOException e) {
//			e.printStackTrace();
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		DBAdapter adapter = new DBAdapter(c);
		adapter.dbh = adapter.getWritableDatabase();
		return adapter;
	}
	
	protected DBAdapter(Context c) {
		super(c, "FeFi", null, 2);
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
	public void onUpgrade(SQLiteDatabase db, int arg1, int arg2) {
		Log.e(TAG, "db upgrade requested from " + arg1 + " to " + arg2);
		db.beginTransaction();
		Cursor c = db.query(UPLOADS, new String[] { "path", "log" }, "log IS NOT NULL", null, null, null, null);
		int pathindex = c.getColumnIndex("path");
		int logindex = c.getColumnIndex("log");
		try {
			if (!c.moveToFirst())
				return;
			do {
				String path = c.getString(pathindex);
				String log = c.getString(logindex);
				Log.d(TAG, "Image " + path + " has " + log.length() + " byte log");
				if(!path.endsWith("JPG"))
					throw new RuntimeException("don't know what to do with path " + path);
				String logpath = path.replace("JPG", "log");
				File f = new File(logpath);
				if(f.exists()) {
					if(f.length() > log.length())
						throw new RuntimeException("Image " + path + " has a log file " + f.length() + 
								" which is longer than desired log " + log.length());
					if(f.length() == log.length()) {
						Log.d(TAG, "Log already exists, continuing");
						continue;
					}
				}
				Log.d(TAG, "writing log to " + logpath);
				FileWriter out = new FileWriter(logpath, false);
				out.write(log);
				out.close();
			} while(c.moveToNext());
			c.close();
			Log.d(TAG, "log writeouts complete, nulling column in database");
			ContentValues cv = new ContentValues();
			cv.putNull("log");
			int updated = db.update(UPLOADS, cv, "log IS NOT NULL", null);
			Log.d(TAG, "updated " + updated + " rows");
			db.setTransactionSuccessful();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if(!c.isClosed())
				c.close();
			db.endTransaction();
			Log.d(TAG, "transaction committed");
		}
		Log.d(TAG, "upgrade complete");
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
	
	public class DuplicateUpload extends Exception {
		private static final long serialVersionUID = 1398625861214657407L;
		
	}
	
	public class UnknownUpload extends Exception {
		private static final long serialVersionUID = -5382003423698828649L;		
	}
	
	public long imageUploadable(String fileSignature) throws DuplicateUpload, UnknownUpload {
		Cursor c = dbh.query(UPLOADS, new String[] { "_id", "status" }, "fileSignature = ?", 
				new String[] { fileSignature }, null, null, null);
		try {
			if(!c.moveToFirst())
				throw new UnknownUpload();

			if(0 != c.getInt(c.getColumnIndex("status")))
				throw new DuplicateUpload();
			
			return c.getInt(c.getColumnIndex("_id"));
		} finally {
			c.close();
		}
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