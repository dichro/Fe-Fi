package to.rcpt.fefi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

import to.rcpt.fefi.eyefi.Types.MacAddress;
import to.rcpt.fefi.eyefi.Types.UploadKey;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.database.Cursor;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class FefiBackupAgent extends BackupAgent {
	private static final String TAG = "BackupAgent";

	@Override
	public void onBackup(ParcelFileDescriptor arg0, BackupDataOutput data,
			ParcelFileDescriptor arg2) throws IOException {
		Log.d(TAG, "Starting backup");
		DBAdapter db = DBAdapter.make(getApplicationContext());
		Cursor c = db.getCards();
		try {
			if (!c.moveToFirst())
				return;
			ByteArrayOutputStream bufStream = new ByteArrayOutputStream();
			DataOutputStream outWriter = new DataOutputStream(bufStream);
			do {
				String name = c.getString(c.getColumnIndex("name"));
				Log.d(TAG, "Backing up card " + name);
				outWriter.writeUTF(name);
				String mac = c.getString(c.getColumnIndex("macAddress"));
				outWriter.writeUTF(mac);
				MacAddress m = new MacAddress(mac);
				outWriter.writeUTF(db.getUploadKeyForMac(m).toString());
				outWriter.writeInt(c.getInt(c.getColumnIndex("offset")));
			} while (c.moveToNext());
			byte[] buffer = bufStream.toByteArray();
			int len = buffer.length;
			data.writeEntityHeader("cards-v2", len);
			data.writeEntityData(buffer, len);
			Log.d(TAG, "Backup done");
		} finally {
			c.close();
			db.close();
		}
		
	}

	@Override
	public void onRestore(BackupDataInput data, int arg1,
			ParcelFileDescriptor arg2) throws IOException {
		Log.d(TAG, "Starting restore");
		DBAdapter db = DBAdapter.make(getApplicationContext());
		try {
			while (data.readNextHeader()) {
				String key = data.getKey();
				int dataSize = data.getDataSize();

				Log.d(TAG, "Restoring " + key);
				if (key.equals("cards")) {
					byte[] buffer = new byte[dataSize];
					data.readEntityData(buffer, 0, dataSize);
					DataInputStream in = new DataInputStream(
							new ByteArrayInputStream(buffer));
					try {
						while (true) {
							String name = in.readUTF();
							MacAddress mac = new MacAddress(in.readUTF());
							UploadKey uk = new UploadKey(in.readUTF());
							if (db.getUploadKeyForMac(mac) == null) {
								Log.d(TAG, "adding card " + name + "@" + mac);
								long id = db.addNewKeyWithMac(mac, uk);
								db.updateCard(id, name, 0);
							} else
								Log.d(TAG, "skipping existing card " + name);
						}
					} catch (EOFException e) {
						// m'kay
					}
				} else if(key.equals("cards-v2")) {
					byte[] buffer = new byte[dataSize];
					data.readEntityData(buffer, 0, dataSize);
					DataInputStream in = new DataInputStream(
							new ByteArrayInputStream(buffer));
					try {
						while (true) {
							String name = in.readUTF();
							MacAddress mac = new MacAddress(in.readUTF());
							UploadKey uk = new UploadKey(in.readUTF());
							int offset = in.readInt();
							if (db.getUploadKeyForMac(mac) == null) {
								Log.d(TAG, "adding card " + name + "@" + mac);
								long id = db.addNewKeyWithMac(mac, uk);
								db.updateCard(id, name, offset);
							} else
								Log.d(TAG, "skipping existing card " + name);
						}
					} catch (EOFException e) {
						// m'kay
					}
				} else {
					Log.e(TAG, "Unknown restore type " + key);
				}
			}
			Log.d(TAG, "Restore done");
		} finally {
			db.close();
		}
	}
}
