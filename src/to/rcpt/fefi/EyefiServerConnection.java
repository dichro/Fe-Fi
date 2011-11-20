package to.rcpt.fefi;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.zip.CheckedInputStream;

import org.apache.http.ConnectionClosedException;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;

import to.rcpt.fefi.eyefi.EyefiIntegrityDigest;
import to.rcpt.fefi.eyefi.EyefiMessage;
import to.rcpt.fefi.eyefi.GetPhotoStatus;
import to.rcpt.fefi.eyefi.GetPhotoStatusResponse;
import to.rcpt.fefi.eyefi.MarkLastPhotoInRollResponse;
import to.rcpt.fefi.eyefi.StartSession;
import to.rcpt.fefi.eyefi.StartSessionResponse;
import to.rcpt.fefi.eyefi.UploadPhoto;
import to.rcpt.fefi.eyefi.UploadPhotoResponse;
import to.rcpt.fefi.eyefi.Types.MacAddress;
import to.rcpt.fefi.eyefi.Types.ServerNonce;
import to.rcpt.fefi.eyefi.Types.UploadKey;
import to.rcpt.fefi.util.MultipartInputStream;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.Media;
import android.util.Log;

public class EyefiServerConnection extends DefaultHttpServerConnection implements Runnable {
	public static final String TAG = "EyefiServerConnection";
	private ServerNonce serverNonce = new ServerNonce("deadbeefdeadbeefdeadbeefdeadbeef");
	EyefiReceiverService context;
	private static final String CONTENT_DISPOSITION_PREAMBLE = "form-data; name=\"";
	private static final String URN_GETPHOTOSTATUS = "\"urn:GetPhotoStatus\"";
	private static final String URN_STARTSESSION = "\"urn:StartSession\"";
	private static final String URN_LAST = "\"urn:MarkLastPhotoInRoll\"";
	private DBAdapter db;
	
	public static EyefiServerConnection makeConnection(EyefiReceiverService c, Socket s) throws IOException {
		s.setReceiveBufferSize(256 * 1024);
		EyefiServerConnection me = new EyefiServerConnection(c);
		BasicHttpParams params = new BasicHttpParams();
		params.setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 256 * 1024);
		me.bind(s, params);
		return me;
	}
	
	protected EyefiServerConnection(EyefiReceiverService c) {
		context = c;
		db = context.getDatabase();
		byte buf[] = new byte[16];
		Random r = new Random();
		r.nextBytes(buf);
		long now = System.currentTimeMillis();
		buf[0] = (byte)now;
		buf[1] = (byte)(now >> 8);
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] nonce = md.digest(buf);
			serverNonce = new ServerNonce(nonce);
		} catch (NoSuchAlgorithmException e) {
			// TODO: handle exception
		}
	}
	
	public void getPhotoStatus(HttpRequest request)
			throws HttpException, IOException {
		Log.d(TAG, "getPhotoStatus");
		if (!(request instanceof HttpEntityEnclosingRequest))
			throw new RuntimeException(); // TODO: something useful
		HttpEntityEnclosingRequest eyefiRequest = (HttpEntityEnclosingRequest) request;
		receiveRequestEntity(eyefiRequest);
		GetPhotoStatus photoStatus = new GetPhotoStatus();
		photoStatus.parse(eyefiRequest.getEntity().getContent());
		MacAddress mac = photoStatus.getMacAddress();
		UploadKey uploadKey = getKeyForMac(mac);
		if(uploadKey != null)
			photoStatus.authenticate(uploadKey, serverNonce);
		else {
			uploadKey = context.registerUnknownMac(mac);
			if(uploadKey != null) {
				photoStatus.authenticate(uploadKey, serverNonce);
					long id = db.addNewKeyWithMac(mac, uploadKey);
					Log.d(TAG, "registered " + mac + " with id " + id);
					context.registerNewCard(mac, uploadKey, id);
			} else {
				close();
				return;
			}
		}
		int offset = 0;
		String filesignature = photoStatus.getParameter("filesignature");
		long id = db.imageExists(filesignature);
		if(id != -1) {
			Log.i(TAG, "image " + filesignature + " exists");
			offset = 0;
		} else {
			Log.i(TAG, "new image " + filesignature);
			db.registerNewImage(filesignature);
		}
		// TODO: how to reject an image? offset presumably resumes upload;
		// what's the point of fileid? oh - returned by client in upload
		GetPhotoStatusResponse gpsr = new GetPhotoStatusResponse(photoStatus,
				1, offset);
		sendResponseHeader(gpsr);
		sendResponseEntity(gpsr);
	}

	public void run() {
		PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Fe-Fi");
		try {
			wakeLock.acquire();

			while (isOpen()) {
				Log.d(TAG, "waiting for request");
				HttpRequest request = receiveRequestHeader();
				String uri = request.getRequestLine().getUri();
				if (uri.equals("/api/soap/eyefilm/v1")) {
					Header[] soapActions = request.getHeaders("SOAPAction");
					if ((soapActions == null) || (soapActions.length == 0)) {
						Log.e(TAG, "no SOAPAction");
						close();
					} else {
						String action = soapActions[0].getValue();
						if (action.equals(URN_STARTSESSION))
							startSession(request);
						else if (action.equals(URN_GETPHOTOSTATUS))
							getPhotoStatus(request);
						else if (action.equals(URN_LAST)) {
							// should probably validate, but screw it. We're done here.
							MarkLastPhotoInRollResponse response = new MarkLastPhotoInRollResponse();
							sendResponseHeader(response);
							sendResponseEntity(response);
							Vibrator v = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
							v.vibrate(1000);
							close();
						}
						else {
							Log.e(TAG, "unknown SOAPAction: " + action);
							close();
						}
					}
				} else if (uri.equals("/api/soap/eyefilm/v1/upload")) {
					uploadPhoto(request);
				} else {
					Log.e(TAG, "unknown method " + uri);
					close();
				}
			}
		} catch (ConnectionClosedException e) {
			Log.i(TAG, "client closed");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			wakeLock.release();
			wakeLock.acquire(60000); // for luck... and reconnection?
		}		
	}

	public UploadKey getKeyForMac(MacAddress mac) {
		UploadKey uploadKey;
		uploadKey = db.getUploadKeyForMac(mac);
		return uploadKey;
	}
	
	public void startSession(HttpRequest request)
			throws HttpException, IOException {
		if (!(request instanceof HttpEntityEnclosingRequest))
			throw new RuntimeException(); // TODO: something useful
		HttpEntityEnclosingRequest eyefiRequest = (HttpEntityEnclosingRequest) request;
		receiveRequestEntity(eyefiRequest);
		StartSession ss = new StartSession();
		ss.parse(eyefiRequest.getEntity().getContent());
		Log.d(TAG, "parsed startsession");
		MacAddress mac = ss.getMacaddress();
		UploadKey uploadKey = getKeyForMac(mac);
		if(uploadKey == null)
			uploadKey = context.registerUnknownMac(mac);
		if(uploadKey == null) {
			close();
			return;
		}
		StartSessionResponse ssr = new StartSessionResponse(ss, uploadKey,
				serverNonce);
		sendResponseHeader(ssr);
		sendResponseEntity(ssr);
	}

	private void importPhoto(File file, String fileName, long id) {
		Log.d(TAG, "importing " + fileName + " from " + file);
		ContentValues values = new ContentValues();
		Date now = new Date();
		DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(context.getApplicationContext());
		values.put(Media.DESCRIPTION, "Received by Fe-Fi on " + dateFormat.format(now));
		values.put(Media.TITLE, fileName);
		values.put(Media.MIME_TYPE, "image/jpeg");
		String path = file.getAbsolutePath();
		values.put(Media.DATA, path);
		values.put(MediaStore.MediaColumns.SIZE, file.length());
		values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg"); // ...right?
		// TODO(dichro): load image offset from card list
		long dateOffset = 3290;
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
			Log.d(TAG, "exif error " + e);
			e.printStackTrace();
		}
		
		File folder = file.getParentFile();
		values.put(Media.BUCKET_ID,
				folder.toString().toLowerCase().hashCode());
		values.put(Media.BUCKET_DISPLAY_NAME,
				folder.getName().toLowerCase());
		
		ContentResolver cr = context.getContentResolver();
		Uri uri = cr.insert(Media.EXTERNAL_CONTENT_URI, values);
		Log.d(TAG, "inserted values to uri " + uri);
		Vibrator v = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
		v.vibrate(300);
	}

	private void importDate(ExifInterface exif, ContentValues values,
			long dateOffset) {
		String dateTime;
		dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME);
		Log.d(TAG, "exif says " + dateTime);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
		try {
			Date date = sdf.parse(dateTime);
			Log.d(TAG, "exif timestamp is " + date + " " + date.getTime());
			long revisedDate = date.getTime() + dateOffset * 1000;
			Log.d(TAG, "revisedDate " + revisedDate + " " + new Date(revisedDate));
			values.put(Images.Media.DATE_TAKEN, revisedDate);
		} catch (ParseException e) {
			Log.d(TAG, "failed to parse " + dateTime);
		}
	}
	
	private void copyToLocalFile(TarInputStream tarball, File destination) throws IOException {
		OutputStream out = new FileOutputStream(destination);
		Log.d(TAG, "shuffling data to " + destination);
		long t1 = System.currentTimeMillis();
		long bytes = tarball.available();
		tarball.copyEntryContents(out);
		out.close();
		long t2 = System.currentTimeMillis();
		long delta = t2 - t1;
		if(delta == 0)
			delta = 1;
		Log.d(TAG, "copied " + bytes + " in " + delta + "ms; " + (1000 * bytes / delta) + " K/s");
	}
	
	public void uploadPhoto(HttpRequest request) 
			throws HttpException, IOException {
		Log.d(TAG, "upload " + request.toString());
		HttpEntityEnclosingRequest eyefiRequest = (HttpEntityEnclosingRequest) request;
		Header contentTypes[] = eyefiRequest.getHeaders("Content-type");
		if(( contentTypes == null) || contentTypes.length < 1) {
			Log.e(TAG, "no content type in upload request");
			close();
		}
		HeaderElement elements[] = contentTypes[0].getElements();
		if((elements == null) || elements.length < 1) {
			Log.e(TAG, "bad content type in upload request");
			close();
		}
		if(!elements[0].getName().equals("multipart/form-data")) {
			Log.e(TAG, "content-type not multipart/form-data in upload");
			close();
		}
		// for some reason, we don't get an element with the boundary. find it manually.
		String b = "boundary=";
		String value = contentTypes[0].getValue();
		int pos = value.indexOf(b);
		if(pos < 0) {
			Log.e(TAG, "no boundary in content-type");
			close();
		}
		String boundary = "--" + value.substring(pos + b.length()) + "\r\n";
		Log.d(TAG, "identified boundary " + boundary);
		// okay, ready to start reading data
		receiveRequestEntity(eyefiRequest);
		HttpEntity entity = eyefiRequest.getEntity();
		MultipartInputStream in = new MultipartInputStream(
				new BufferedInputStream(entity.getContent(), 400000), boundary);
		// find first boundary; should be at start
		in.close();
		Map<String, String> headers = getHeaders(in, boundary);
		UploadPhoto uploadPhoto = null;
		String fileSignature = null;
		String imageName = null;
		File destinationPath = null;
		long id = -1;
		boolean success = false;
		String readDigest = null, calculatedDigest = null;
		while(!headers.isEmpty()) {
			String contentDisposition = headers.get("Content-Disposition");
			if(contentDisposition == null)
				break;
			if(!contentDisposition.startsWith(CONTENT_DISPOSITION_PREAMBLE))
				break;
			int endPos = contentDisposition.indexOf('"', CONTENT_DISPOSITION_PREAMBLE.length());
			if(endPos < 0)
				break;
			String partName = contentDisposition.substring(CONTENT_DISPOSITION_PREAMBLE.length(), endPos);
			Log.d(TAG, "have part " + partName);
			if(partName.equals("SOAPENVELOPE")) {
				UploadPhoto u = new UploadPhoto();
				u.parse(in);
				uploadPhoto = u;
				Log.d(TAG, "parsed uploadPhoto");
			} else if(partName.equals("FILENAME")) {
				EyefiIntegrityDigest checksum = new EyefiIntegrityDigest();
				TarInputStream tarball = new TarInputStream(new CheckedInputStream(in, checksum));
				TarEntry file = tarball.getNextEntry();
				while(file != null) {
					String fileName = file.getName();
					if(fileName.endsWith(".log")) {
						Log.d(TAG, "Found logfile " + fileName);
						File destination = openWritableFile(id, "log");
						copyToLocalFile(tarball, destination);
					} else {
						Log.d(TAG, "Processing image file " + fileName);
						if(uploadPhoto == null) {
							Log.d(TAG, "...but no uploadPhoto");
							break;
						}
						fileSignature = uploadPhoto.getParameter(EyefiMessage.FILESIGNATURE);
						imageName = fileName;
						try {
							try {
								id = db.imageUploadable(fileSignature);
							} catch(DBAdapter.UnknownUpload e) {
								// some (?) X2 cards have oddness and duplicity in UploadPhoto:filesignature. Fake one up.
								fileSignature = fileSignature + ":" + fileName;
								Log.d(TAG, "inexplicably unknown filesignature. X2 card? Faking one up as " + fileSignature);
								db.registerNewImage(fileSignature);
								id = db.imageUploadable(fileSignature);								
							}
							Log.d(TAG, "image " + fileName + " has signature " + fileSignature + " id " + id);
							destinationPath = openWritableFile(id, "JPG");
							Log.d(TAG, "want to write " + imageName + " to " + destinationPath);
							copyToLocalFile(tarball, destinationPath);
							importPhoto(destinationPath, fileName, id);
							success = true;
						} catch(IOException e) {
							Log.e(TAG, "IO fail " + e);						
						} catch(DBAdapter.DuplicateUpload e) {
							Log.e(TAG, "file exists, ignoring upload but faking success!");
							success = true;
						} catch(DBAdapter.UnknownUpload e) {
							Log.e(TAG, "unknown upload! " + uploadPhoto);
						}
					}
					Log.d(TAG, "skipping to next entry");
					file = tarball.getNextEntry();
				}
				UploadKey uploadKey = getKeyForMac(uploadPhoto.getMacAddress());
				calculatedDigest = checksum.getValue(uploadKey).toString();
				Log.d(TAG, "calculated digest " + calculatedDigest);
				// TODO: check integrity?
			} else if(partName.equals("INTEGRITYDIGEST")) {
				BufferedReader r = new BufferedReader(new InputStreamReader(in));
				readDigest = r.readLine();
				Log.d(TAG, "read digest " + readDigest);
			}
			in.close();
			headers = getHeaders(in, boundary);
		}
		if(calculatedDigest == null) {
			Log.d(TAG, "failed to calculate a digest, rejecting");
			return;
		}
		if(readDigest == null) {
			Log.d(TAG, "failed to receive a digest, rejecting");
			return;
		}
		if(!readDigest.equals(calculatedDigest)) {
			Log.d(TAG, "received digest does not match calculated digest, rejecting");
			return;
		}
		if(destinationPath != null)
			db.receiveImage(id, imageName, destinationPath.toString());
		UploadPhotoResponse response = new UploadPhotoResponse(success);
		sendResponseHeader(response);
		sendResponseEntity(response);
	}

	private File openWritableFile(long id, String suffix) {
		File destinationPath;
		SettingsActivity.FefiPreferences prefs = new SettingsActivity.FefiPreferences(context);
		destinationPath = new File(Environment.getExternalStorageDirectory(),
				"eyefi/" + prefs.getFolderName(new Date()) + "/" + id + "." + suffix);
		destinationPath.getParentFile().mkdirs();
		return destinationPath;
	}

	public Map<String, String> getHeaders(MultipartInputStream in, String boundary) 
			throws IOException {
		Map<String, String> headers = new HashMap<String, String>();
		in.setBoundary("\r\n\r\n");
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		String line = reader.readLine();
		while(line != null) {
			int pos = line.indexOf(": ");
			if(pos > 0)
				headers.put(line.substring(0, pos), line.substring(pos + 2));
			line = reader.readLine();
		}
		in.setBoundary("\r\n" + boundary);
		return headers;
	}

}
