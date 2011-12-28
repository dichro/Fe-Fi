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
import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.Vector;
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

import to.rcpt.fefi.DBAdapter.Card;
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
import android.content.Context;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Log;

public class EyefiServerConnection extends DefaultHttpServerConnection implements Runnable {
	public static final String TAG = "EyefiServerConnection";
	private ServerNonce serverNonce = new ServerNonce("deadbeefdeadbeefdeadbeefdeadbeef");
	EyefiReceiverService context;
	public static final String CONTENT_DISPOSITION_PREAMBLE = "form-data; name=\"";
	private static final String URN_GETPHOTOSTATUS = "\"urn:GetPhotoStatus\"";
	private static final String URN_STARTSESSION = "\"urn:StartSession\"";
	private static final String URN_LAST = "\"urn:MarkLastPhotoInRoll\"";
	private DBAdapter db;
	
	private static int id = 0;
	private int myId;
	
	public static EyefiServerConnection makeConnection(EyefiReceiverService c, Socket s) throws IOException {
		s.setReceiveBufferSize(256 * 1024);
		EyefiServerConnection me = new EyefiServerConnection(c, id++);
		BasicHttpParams params = new BasicHttpParams();
		params.setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 256 * 1024);
		me.bind(s, params);
		return me;
	}
	
	protected EyefiServerConnection(EyefiReceiverService c, int id) {
		myId = id;
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
		Log.d(TAG, myId + " getPhotoStatus");
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
					Log.d(TAG, myId + " registered " + mac + " with id " + id);
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
			Log.i(TAG, myId + " image " + filesignature + " exists");
//			offset = 0;
		} else {
			Log.i(TAG, myId + " new image " + filesignature);
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
				Log.d(TAG, myId + " waiting for request");
				HttpRequest request = receiveRequestHeader();
				String uri = request.getRequestLine().getUri();
				if (uri.equals("/api/soap/eyefilm/v1")) {
					Header[] soapActions = request.getHeaders("SOAPAction");
					if ((soapActions == null) || (soapActions.length == 0)) {
						Log.e(TAG, myId + " no SOAPAction");
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
							Log.e(TAG, myId + " unknown SOAPAction: " + action);
							close();
						}
					}
				} else if (uri.equals("/api/soap/eyefilm/v1/upload")) {
					uploadPhoto(request);
				} else {
					Log.e(TAG, myId + " unknown method " + uri);
					close();
				}
			}
		} catch (ConnectionClosedException e) {
			Log.i(TAG, myId + " client closed");
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
		Log.d(TAG, myId + " parsed startsession");
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

	private void copyToLocalFile(TarInputStream tarball, File destination) throws IOException {
		OutputStream out = new FileOutputStream(destination);
		Log.d(TAG, myId + " shuffling data to " + destination);
		long t1 = System.currentTimeMillis();
		long bytes = tarball.available();
		tarball.copyEntryContents(out);
		out.close();
		long t2 = System.currentTimeMillis();
		long delta = t2 - t1;
		if(delta == 0)
			delta = 1;
		Log.d(TAG, myId + " copied " + bytes + " in " + delta + "ms; " + (1000 * bytes / delta) + " K/s");
	}
	
	public void uploadPhoto(HttpRequest request) 
			throws HttpException, IOException {
		Log.d(TAG, myId + " upload " + request.toString());
		HttpEntityEnclosingRequest eyefiRequest = (HttpEntityEnclosingRequest) request;
		Header contentTypes[] = eyefiRequest.getHeaders("Content-type");
		if(( contentTypes == null) || contentTypes.length < 1) {
			Log.e(TAG, myId + " no content type in upload request");
			close();
		}
		HeaderElement elements[] = contentTypes[0].getElements();
		if((elements == null) || elements.length < 1) {
			Log.e(TAG, myId + " bad content type in upload request");
			close();
		}
		if(!elements[0].getName().equals("multipart/form-data")) {
			Log.e(TAG, myId + " content-type not multipart/form-data in upload");
			close();
		}
		// for some reason, we don't get an element with the boundary. find it manually.
		String b = "boundary=";
		String value = contentTypes[0].getValue();
		int pos = value.indexOf(b);
		if(pos < 0) {
			Log.e(TAG, myId + " no boundary in content-type");
			close();
		}
		String boundary = "--" + value.substring(pos + b.length());
		Log.d(TAG, myId + " identified boundary " + boundary);
		// okay, ready to start reading data
		receiveRequestEntity(eyefiRequest);
		HttpEntity entity = eyefiRequest.getEntity();
//		if(true) {
//			File f = openWritableFile(myId, "foo");
//			InputStream in = entity.getContent();
//			OutputStream out = new FileOutputStream(f);
//			byte[] b2 = new byte[60000];
//			int read;
//			while ((read = in.read(b2)) != -1) {
//				out.write(b2, 0, read);
//			}
//			out.close();
//			Log.d(TAG, "done copying " + myId);
//			return;
//		}
		MultipartInputStream in = new MultipartInputStream(
				new BufferedInputStream(entity.getContent(), 400000), boundary);
		// find first boundary; should be at start
		in.close();
		Map<String, String> headers = in.getHeaders();
		UploadPhoto uploadPhoto = null;
		String fileSignature = null;
		String imageName = null;
		File destinationPath = null;
		long id = -1;
		boolean success = false;
		String readDigest = null, calculatedDigest = null;
		Vector<File> written = new Vector<File>();
		String fileName = null;
		Card card = null;
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
			Log.d(TAG, myId + " have part " + partName);
			if(partName.equals("SOAPENVELOPE")) {
				UploadPhoto u = new UploadPhoto();
				u.parse(in);
				uploadPhoto = u;
				Log.d(TAG, myId + " parsed uploadPhoto");
				card = db.getCard(u.getMacAddress());
			} else if(partName.equals("FILENAME")) {
				EyefiIntegrityDigest checksum = new EyefiIntegrityDigest();
				CheckedInputStream c = new CheckedInputStream(in, checksum);
				TarInputStream tarball = new TarInputStream(c);
				TarEntry file = tarball.getNextEntry();
				while(file != null) {
					fileName = file.getName();
					Log.d(TAG, myId + " tarred file " + fileName + " " + file.getSize() + "b");
					// TODO(dichro): sanitize filename.
					if(fileName.endsWith(".log")) {
						File destination = openWritableFile(id, "log");
						copyToLocalFile(tarball, destination);
						written.add(destination);
					} else {
						if(uploadPhoto == null) {
							Log.d(TAG, myId + " ...but no uploadPhoto");
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
								Log.d(TAG, myId + " inexplicably unknown filesignature. X2 card? Faking one up as " + fileSignature);
								db.registerNewImage(fileSignature);
								id = db.imageUploadable(fileSignature);								
							}
							Log.d(TAG, myId + " image " + fileName + " has signature " + fileSignature + " id " + id);
							destinationPath = openWritableFile(id, "JPG");
							Log.d(TAG, myId + " want to write " + imageName + " to " + destinationPath);
							copyToLocalFile(tarball, destinationPath);
							written.add(destinationPath);
							success = true;
						} catch(IOException e) {
							Log.e(TAG, myId + " IO fail " + e);						
						} catch(DBAdapter.DuplicateUpload e) {
							Log.e(TAG, myId + " file exists, ignoring upload but faking success!");
							success = true;
						} catch(DBAdapter.UnknownUpload e) {
							Log.e(TAG, myId + " unknown upload! " + uploadPhoto);
						}
					}
					Log.d(TAG, myId + " skipping to next entry");
					file = tarball.getNextEntry();
				}
				long skipped = 0, s;
				while((s = c.skip(4096)) == 4096)
					skipped += 4096;
				Log.d(TAG, "Skipped " + (skipped + s) + " bytes");
				UploadKey uploadKey = getKeyForMac(uploadPhoto.getMacAddress());
				calculatedDigest = checksum.getValue(uploadKey).toString();
				Log.d(TAG, myId + " calculated digest " + calculatedDigest);
			} else if(partName.equals("INTEGRITYDIGEST")) {
				BufferedReader r = new BufferedReader(new InputStreamReader(in));
				readDigest = r.readLine();
				Log.d(TAG, myId + " read digest " + readDigest);
			}
			in.close();
			headers = in.getHeaders();
		}
		if(calculatedDigest == null) {
			Log.d(TAG, myId + " failed to calculate a digest, rejecting");
			success = false;
		}
		if(readDigest == null) {
			Log.d(TAG, myId + " failed to receive a digest, rejecting");
			success = false;
		}
		if(success && !readDigest.equals(calculatedDigest)) {
			Log.d(TAG, myId + " received digest does not match calculated digest, rejecting");
			success = false;
		}
		if(success && destinationPath != null) {
			card.registerUpload(id, imageName, destinationPath);
		}
		if(!success)
			for(File f : written)
				f.delete();
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
}
