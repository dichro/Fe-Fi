package to.rcpt.fefi;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import to.rcpt.fefi.eyefi.Types.MacAddress;
import to.rcpt.fefi.eyefi.Types.UploadKey;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.Media;
import android.util.Log;

public class DBAdapter extends SQLiteOpenHelper {
	private static final String UPLOADS = "uploads";
	private static final String CARDS = "cameras";
	private static final String TAG = "DBAdapter";
	private static final String LOCATION = "location";
	protected SQLiteDatabase dbh;
	private Context context;
	
	private static final String[] idColumns = { "_id" };
	
	public static DBAdapter make(Context c) {
		DBAdapter adapter = new DBAdapter(c);
		adapter.dbh = adapter.getWritableDatabase();
		return adapter;
	}
	
	protected DBAdapter(Context c) {
		super(c, "FeFi", null, sqlSetup.length);
		context = c;
	}
	
	final static String sqlSetup[] = {
		null,
		null,
		null,
		"CREATE TABLE " + LOCATION + " ("
		+ "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
		+ "latitude REAL NOT NULL,"
		+ "longitude REAL NOT NULL,"
		+ "altitude REAL,"
		+ "accuracy REAL,"
		+ "fixtime INT NOT NULL"
		+ ");",
		"ALTER TABLE " + CARDS + " ADD COLUMN offset INT DEFAULT 0;",
		"ALTER TABLE " + UPLOADS + " ADD COLUMN card INT;",
		null,
		"UPDATE " + UPLOADS + " SET card=(SELECT _id FROM " + CARDS + " LIMIT 1) WHERE card IS NULL;",
	};
	
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
		for(int i = 0; i < sqlSetup.length; i++) {
			if(sqlSetup[i] == null)
				continue;
			Log.d(TAG, "running " + sqlSetup[i]);
			db.execSQL(sqlSetup[i]);
		}
		Log.e(TAG, "DB creation done");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int currentVersion, int requestedVersion) {
		Log.e(TAG, "db upgrade requested from " + currentVersion + " to " + requestedVersion);
		if ((currentVersion <= 1) && (requestedVersion >= 2)) {
			Log.d(TAG, "migrating log data to sdcard");
			db.beginTransaction();
			Cursor c = db.query(UPLOADS, new String[] { "path", "log" },
					"log IS NOT NULL", null, null, null, null);
			int pathindex = c.getColumnIndex("path");
			int logindex = c.getColumnIndex("log");
			try {
				if (c.moveToFirst())
					do {
						String path = c.getString(pathindex);
						String log = c.getString(logindex);
						Log.d(TAG, "Image " + path + " has " + log.length()
								+ " byte log");
						if (!path.endsWith("JPG"))
							throw new RuntimeException(
									"don't know what to do with path " + path);
						String logpath = path.replace("JPG", "log");
						File f = new File(logpath);
						if (f.exists()) {
							if (f.length() > log.length())
								throw new RuntimeException("Image " + path
										+ " has a log file " + f.length()
										+ " which is longer than desired log "
										+ log.length());
							if (f.length() == log.length()) {
								Log.d(TAG, "Log already exists, continuing");
								continue;
							}
						}
						Log.d(TAG, "writing log to " + logpath);
						FileWriter out = new FileWriter(logpath, false);
						out.write(log);
						out.close();
					} while (c.moveToNext());
				c.close();
				Log
						.d(TAG,
								"log writeouts complete, nulling column in database");
				ContentValues cv = new ContentValues();
				cv.putNull("log");
				int updated = db.update(UPLOADS, cv, "log IS NOT NULL", null);
				Log.d(TAG, "updated " + updated + " rows");
				db.setTransactionSuccessful();
			} catch (IOException e) {
				Log.d(TAG, "exception " + e);
				throw new RuntimeException(e);
			} finally {
				if (!c.isClosed())
					c.close();
				db.endTransaction();
				Log.d(TAG, "transaction committed");
			}
		}
		if ((currentVersion <= 2) && (requestedVersion >= 3)) {
			Log.d(TAG, "Triggering initial backup");
			scheduleBackup();
		}
		for(int i = currentVersion; i < requestedVersion; i++) {
			if(sqlSetup[i] == null)
				continue;
			Log.d(TAG, "running " + sqlSetup[i]);
			db.execSQL(sqlSetup[i]);
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
	
	public void addLocation(Location l) {
		ContentValues cv = new ContentValues();
		cv.put("latitude", l.getLatitude());
		cv.put("longitude", l.getLongitude());
		if(l.hasAltitude())
			cv.put("altitude", l.getAltitude());
		if(l.hasAccuracy())
			cv.put("accuracy", l.getAccuracy());
		cv.put("fixtime", l.getTime());
		dbh.insertOrThrow(LOCATION, null, cv);
	}
	
	public long countLocations() {
		Cursor c = dbh.rawQuery("SELECT COUNT(*) FROM " + LOCATION, null);
		if(!c.moveToFirst())
			return 0;
		long ret = c.getLong(0);
		c.close();
		return ret;
	}
	
	public Cursor getLocations() {
		Cursor c = dbh.query(LOCATION, new String[] { "_id", "latitude", "longitude", "altitude", "accuracy", "fixtime" }, 
				null, null, null, null, null);
		return c;
	}
	
	public Cursor findNearestLocation(long time, int maxTolerance) {
		Cursor c = dbh.query(LOCATION, new String[] { "latitude", "longitude", "altitude", "accuracy", "fixtime" },
				"fixtime >= ? AND fixtime <= ?", new String[] { "" + (time - maxTolerance), "" + (time + maxTolerance) },
				null, null, "fixtime");
		if(!c.moveToFirst()) {
//			Log.d(TAG, "No points found for " + time + "/" + maxTolerance);
			c.close();
			return null;
		}
		long lastDelta = maxTolerance;
		int index = c.getColumnIndex("fixtime");
		do {
			long thisDelta = c.getLong(index) - time;
			if(thisDelta < 0)
				thisDelta = -thisDelta;
			if(thisDelta > lastDelta) {
				c.moveToPrevious();
				break;
			}
			lastDelta = thisDelta;
			if(!c.moveToNext()) {
				c.moveToLast();
				break;
			}
		} while(true);
//		Log.d(TAG, "Point found for " + time + "/" + maxTolerance + "@" + lastDelta);
		return c;
	}
	
	public long addNewKeyWithMac(MacAddress mac, UploadKey key) {
		ContentValues cv = new ContentValues();
		cv.put("uploadKey", key.toString());
		cv.put("macAddress", mac.toString());
		scheduleBackup();
		long ret = dbh.insertOrThrow(CARDS, null, cv);
		scheduleBackup();
		return ret;
	}
	
	public UploadKey getUploadKeyForMac(MacAddress mac) {
		Cursor c = dbh.query(CARDS, new String[] { "uploadKey" }, "macAddress = ?",
				new String[] { mac.toString() }, null, null, null);
		try {
			if(!c.moveToFirst()) {
				// TODO: throw something
				return null;
			}
			String uploadKey = c.getString(c.getColumnIndex("uploadKey"));
			return new UploadKey(uploadKey);
		} finally {
			c.close();
		}
	}

	public int getOffsetForMac(MacAddress mac) {
		Cursor c = dbh.query(CARDS, new String[] { "offset" }, "macAddress = ?",
				new String[] { mac.toString() }, null, null, null);
		try {
			if(!c.moveToFirst()) {
				// TODO: throw something
				return 0;
			}
			int uploadKey = c.getInt(c.getColumnIndex("offset"));
			return uploadKey;
		} finally {
			c.close();
		}
	}

	public Cursor getUploads() {
		Cursor c = dbh.query(UPLOADS, new String[] { "_id", "name", "updated", "status", "imageUri" }, 
				"status > 0", null, null, null, "updated DESC");
		return c;
	}
	
	public Cursor getCards() {
		Cursor c = dbh.query(CARDS, new String[] { "_id", "name", "macAddress" }, 
				null, null, null, null, null);
		return c;
	}
	
	public Cursor getCard(long id) {
		Cursor c = dbh.query(CARDS, new String[] { "_id", "name", "macAddress", "offset" }, 
				"_id = ?", new String[] { id + "" }, null, null, null);
		return c;
	}
	
	public void deleteCard(int id) {
		dbh.delete(CARDS, "_id = ?", new String[] { id + "" });
	}
	
	public void updateCard(long id, String name, int offset) {
		ContentValues cv = new ContentValues();
		cv.put("name", name);
		cv.put("offset", offset);
		dbh.update(CARDS, cv, "_id = ?", new String[] { id + "" });
		scheduleBackup();
	}
	
	public void scheduleBackup() {
		Log.d(TAG, "Scheduling backup");
		try {
			Class<?> managerClass = Class.forName("android.app.backup.BackupManager");
			Constructor<?> managerConstructor = managerClass.getConstructor(Context.class);
			Object manager = managerConstructor.newInstance(context);
			Method m = managerClass.getMethod("dataChanged");
			m.invoke(manager);
			Log.d(TAG, "Backup requested");
		} catch(ClassNotFoundException e) {
			Log.d(TAG, "No backup manager found");
		} catch(Throwable t) {
			Log.d(TAG, "Scheduling backup failed " + t);
			t.printStackTrace();
		}
	}
	
	class Card {
		private long id, dateOffset;
		private long myId = 0;
		private String name, macAddress;

		Card(String condition, String ...args) {
			init(condition, args);
		}
		
		protected void init(String condition, String ...args) {
			Cursor c = dbh.query(CARDS, new String[] { "_id", "name", "macAddress", "offset" },
					condition, args, null, null, null);
			try {
				if(!c.moveToFirst()) {
					throw new RuntimeException("no card found");
				}
				id = myId = c.getLong(c.getColumnIndex("_id"));
				dateOffset = c.getLong(c.getColumnIndex("offset"));
				macAddress = c.getString(c.getColumnIndex("macAddress"));
				name = c.getString(c.getColumnIndex("name"));
			} finally {
				c.close();
			}
		}

		public int getStoredCount() {
			Cursor c = dbh.rawQuery("SELECT COUNT(*) FROM " + UPLOADS + " WHERE card = ? AND status = 2",
					new String[] { "" + id });
			if(!c.moveToFirst())
				return 0;
			int ret = c.getInt(0);
			c.close();
			return ret;
		}

		public void registerUpload(long pid, String name, File path) {
			Uri uri = importPhoto(path, name, pid);
			ContentValues cv = new ContentValues();
			cv.put("name", name);
			cv.put("path", path.toString());
			cv.put("updated", System.currentTimeMillis());
			cv.put("status", 2);
			cv.put("imageUri", uri.toString());
			cv.put("card", id);
			Log.d(TAG, "done with image " + pid + "@" + uri.toString());
			dbh.update(UPLOADS, cv, "_id = ?", new String[] { pid + "" });
			if(observer != null)
				observer.notifyImage();
		}

		private void augmentMetadata(ContentValues values, File file) {
			String path = file.getAbsolutePath();
			values.put(Media.MIME_TYPE, "image/jpeg");
			values.put(Media.DATA, path);
			values.put(MediaStore.MediaColumns.SIZE, file.length());
			values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg"); // ...right?
			// TODO(dichro): load image offset from card list
			try {
				ExifInterface exif = new ExifInterface(path);
				importDate(exif, values, dateOffset);
				int orientation = exif.getAttributeInt(
						ExifInterface.TAG_ORIENTATION, -1);
				if (orientation != -1) {
					int degree;
					switch(orientation) {
					case ExifInterface.ORIENTATION_ROTATE_90:
						degree = 90;
						break;
					case ExifInterface.ORIENTATION_ROTATE_180:
						degree = 180;
						break;
					case ExifInterface.ORIENTATION_ROTATE_270:
						degree = 270;
						break;
					default:
						degree = 0;
						break;
					}
					values.put(Images.Media.ORIENTATION, degree);
				}
			} catch (IOException e) {
				Log.d(TAG, myId + " exif error " + e);
				e.printStackTrace();
			}

			File folder = file.getParentFile();
			values.put(Media.BUCKET_ID,
					folder.toString().toLowerCase().hashCode());
			values.put(Media.BUCKET_DISPLAY_NAME,
					folder.getName().toLowerCase());
		}

		private void augmentLocation(ContentValues values, int window) {
			Long date = values.getAsLong(Images.Media.DATE_TAKEN);
			if(date != null) {
				Cursor c = findNearestLocation(date.longValue(), window);
				if(c != null) {
					values.put(Images.Media.LATITUDE, c.getFloat(c.getColumnIndex("latitude")));
					values.put(Images.Media.LONGITUDE, c.getFloat(c.getColumnIndex("longitude")));
					c.close();
				}
			}
		}

		public void recalculateMetadata(Progress p) {
			Cursor c = dbh.query(UPLOADS, new String[] { "imageUri", "path" }, "card = ? AND status = 2", new String[] { "" + id }, null, null, null);
			if(!c.moveToFirst())
				return;
			int total, done = 0, ok = 0;
			total = c.getCount();
			try {
				ContentResolver cr = context.getContentResolver();
				int pathIndex = c.getColumnIndex("path");
				int uriIndex = c.getColumnIndex("imageUri");
				do {
					done++;
					String path = c.getString(pathIndex);
					if(path == null) {
						Log.d(TAG, "While recalculating, path null");
						continue;
					}
					File f = new File(path);
					if(!f.exists()) {
						Log.d(TAG, "While recalculating, " + f.toString() + " file DNE");
						continue;
					}
					ContentValues values = new ContentValues();
					augmentMetadata(values, f);
					String string = c.getString(uriIndex);
					if(string == null) {
						Log.d(TAG, "While recalculating, uriIndex null");
						continue;
					}
					Uri uri = Uri.parse(string);
					if(cr.update(uri, values, null, null) > 0)
						ok++;
					p.updateProgress(total, done, ok);
				} while(c.moveToNext());
			} finally {
				c.close();
			}
		}

		private Uri importPhoto(File file, String fileName, long id) {
			ContentValues values = new ContentValues();
			values.put(Media.TITLE, fileName);
			augmentMetadata(values, file);
			augmentLocation(values, 900000);
			ContentResolver cr = context.getContentResolver();
			Uri uri = cr.insert(Media.EXTERNAL_CONTENT_URI, values);
			context.sendBroadcast(new Intent("com.android.camera.NEW_PICTURE", uri));
			Log.d(TAG, myId + " inserted values to uri " + uri);
			Vibrator v = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
			v.vibrate(300);
			return uri;
		}

		private long importDate(ExifInterface exif, ContentValues values,
				long dateOffset) {
			String dateTime;
			dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME);
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
			try {
				Date date = sdf.parse(dateTime);
				long revisedDate = date.getTime() + dateOffset * 1000;
				values.put(Images.Media.DATE_TAKEN, revisedDate);
				return revisedDate;
			} catch (ParseException e) {
				Log.d(TAG, myId + " failed to parse " + dateTime);
			}
			return -1;
		}
	}

	public Card getCardO(long id) {
		return new Card("_id = ?", "" + id);
	}

	public Card getCardO(MacAddress mac) {
		return new Card("macAddress = ?", mac.toString());
	}
	
	public interface Progress {
		public void updateProgress(int total, int done, int ok);
	}

}
