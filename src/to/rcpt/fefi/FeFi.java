package to.rcpt.fefi;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpServerConnection;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.message.BufferedHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.util.CharArrayBuffer;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;

import to.rcpt.fefi.eyefi.EyefiMessage;
import to.rcpt.fefi.eyefi.GetPhotoStatus;
import to.rcpt.fefi.eyefi.GetPhotoStatusResponse;
import to.rcpt.fefi.eyefi.StartSession;
import to.rcpt.fefi.eyefi.StartSessionResponse;
import to.rcpt.fefi.eyefi.UploadPhoto;
import to.rcpt.fefi.eyefi.UploadPhotoResponse;
import to.rcpt.fefi.util.MultipartInputStream;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Images.Media;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class FeFi extends Activity implements Runnable {
	private static final String CONTENT_DISPOSITION_PREAMBLE = "form-data; name=\"";
	private static final String serverNonce = "deadbeef";
	private static final String URN_STARTSESSION = "\"urn:StartSession\"";
	private static final String URN_GETPHOTOSTATUS = "\"urn:GetPhotoStatus\"";
	private ServerSocket eyefiSocket;
	private String uploadKey;
	public static final String TAG = "FeFi";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		Log.e(TAG, "bar2");
		try {
			eyefiSocket = new ServerSocket(59278);
			Log.e(TAG, "bar");
		} catch (IOException e) {
			e.printStackTrace();
		}
		new Thread(this).start();
	}

	public void startSession(HttpServerConnection conn, HttpRequest request)
			throws HttpException, IOException {
		if (!(request instanceof HttpEntityEnclosingRequest))
			throw new RuntimeException(); // TODO: something useful
		HttpEntityEnclosingRequest eyefiRequest = (HttpEntityEnclosingRequest) request;
		conn.receiveRequestEntity(eyefiRequest);
		StartSession ss = StartSession.parse(eyefiRequest.getEntity()
				.getContent());
		StartSessionResponse ssr = new StartSessionResponse(ss, this.uploadKey,
				serverNonce);
		conn.sendResponseHeader(ssr);
		conn.sendResponseEntity(ssr);
	}

	public void getPhotoStatus(HttpServerConnection conn, HttpRequest request)
			throws HttpException, IOException {
		Log.d(TAG, "getPhotoStatus");
		if (!(request instanceof HttpEntityEnclosingRequest))
			throw new RuntimeException(); // TODO: something useful
		HttpEntityEnclosingRequest eyefiRequest = (HttpEntityEnclosingRequest) request;
		conn.receiveRequestEntity(eyefiRequest);
		GetPhotoStatus photoStatus = new GetPhotoStatus();
		photoStatus.parse(eyefiRequest.getEntity().getContent());
		photoStatus.authenticate(uploadKey, serverNonce);
		Cursor results = getCursor(photoStatus.getParameter("filesignature"));
		int offset = 0;
		boolean imageExists = results.moveToFirst();
		if(imageExists)
			offset = results.getInt(results.getColumnIndex(MediaColumns.SIZE));
		results.close();
		Log.d(TAG, "existence: " + imageExists + "; offset " + offset);
		// TODO: how to reject an image? offset presumably resumes upload;
		// what's the point of fileid? oh - returned by client in upload
		GetPhotoStatusResponse gpsr = new GetPhotoStatusResponse(photoStatus,
				1, 0);
		conn.sendResponseHeader(gpsr);
		conn.sendResponseEntity(gpsr);
	}

	private Cursor getCursor(String signature) {
		String imageName = "eyefi/" + signature;
		Log.d(TAG, "searching for " + imageName);
		ContentResolver cr = getContentResolver();
		String[] columns = { BaseColumns._ID, MediaColumns.SIZE };
		String filter = Images.Media.DISPLAY_NAME + " = ? ";
		String placeholders[] = { imageName };
		Cursor results = cr.query(Images.Media.EXTERNAL_CONTENT_URI, columns,
				filter, placeholders, null);
		return results;
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
		in.setBoundary(boundary);
		return headers;
	}
	
	public void uploadPhoto(HttpServerConnection conn, HttpRequest request) 
			throws HttpException, IOException {
		Log.d(TAG, "upload " + request.toString());
		HttpEntityEnclosingRequest eyefiRequest = (HttpEntityEnclosingRequest) request;
		Header contentTypes[] = eyefiRequest.getHeaders("Content-type");
		if((contentTypes == null) || contentTypes.length < 1) {
			Log.e(TAG, "no content type in upload request");
			conn.close();
		}
		HeaderElement elements[] = contentTypes[0].getElements();
		if((elements == null) || elements.length < 1) {
			Log.e(TAG, "bad content type in upload request");
			conn.close();
		}
		if(!elements[0].getName().equals("multipart/form-data")) {
			Log.e(TAG, "content-type not multipart/form-data in upload");
			conn.close();
		}
		// for some reason, we don't get an element with the boundary. find it manually.
		String b = "boundary=";
		String value = contentTypes[0].getValue();
		int pos = value.indexOf(b);
		if(pos < 0) {
			Log.e(TAG, "no boundary in content-type");
			conn.close();
		}
		String boundary = "--" + value.substring(pos + b.length()) + "\r\n";
		Log.d(TAG, "identified boundary " + boundary);
		// okay, ready to start reading data
		conn.receiveRequestEntity(eyefiRequest);
		HttpEntity entity = eyefiRequest.getEntity();
		Log.d(TAG, "entity " + entity + " len " + entity.getContentLength());
		MultipartInputStream in = new MultipartInputStream(
				new BufferedInputStream(entity.getContent()), boundary);
		Log.d(TAG, "made in " + in);
		// find first boundary; should be at start
		in.close();
		Map<String, String> headers = getHeaders(in, boundary);
		UploadPhoto uploadPhoto = null;
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
			if(partName.equals("SOAPENVELOPE")) {
				UploadPhoto u = new UploadPhoto();
				u.parse(in);
				uploadPhoto = u;
			} else if(partName.equals("FILENAME")) {
				TarInputStream tarball = new TarInputStream(in);
				TarEntry file = tarball.getNextEntry();
				while(file != null) {
					String fileName = file.getName();
					if(fileName.endsWith(".log")) {
						Log.d(TAG, "Found logfile " + fileName + ", ignoring... for now!");
					} else {
						Log.d(TAG, "Processing image file " + fileName);
						if(uploadPhoto == null)
							break;
						Cursor results = getCursor(uploadPhoto.getParameter(EyefiMessage.FILESIGNATURE));
						boolean fileExists = results.moveToFirst();
						results.close();
						if(!fileExists) {
							ContentValues values = new ContentValues();
							values.put(Media.DISPLAY_NAME, "eyefi/" + uploadPhoto.getParameter(EyefiMessage.FILESIGNATURE));
							values.put(Media.BUCKET_DISPLAY_NAME, "bucket display?");
							values.put(Media.DESCRIPTION, "description");
							values.put(Media.TITLE, "title");
							values.put(Media.MIME_TYPE, "image/jpeg");
							values.put(MediaColumns.SIZE, (int)file.getSize());
							ContentResolver cr = getContentResolver();
							Uri uri = cr.insert(Media.EXTERNAL_CONTENT_URI, values);
							try {
								OutputStream out = cr.openOutputStream(uri);
								Log.d(TAG, "shuffling image to " + uri);
								tarball.copyEntryContents(out);
								out.close();
								Log.d(TAG, "done with " + uri);
							} catch(IOException e) {
								Log.e(TAG, "IO fail " + e);
							}
						} else
							Log.e(TAG, "file exists!");
					}
					file = tarball.getNextEntry();
				}
			}
			in.close();
			headers = getHeaders(in, boundary);
		}
		UploadPhotoResponse response = new UploadPhotoResponse(false);
		conn.sendResponseHeader(response);
		conn.sendResponseEntity(response);
	}
	
	public void run() {
		Log.e(TAG, "run()");
		uploadKey = "a8378747b56aa0c49d608bec38b159e8";
		while (true) {
			try {
				Socket eyefiClient = eyefiSocket.accept();
				Log.e(TAG, "socket!");
				DefaultHttpServerConnection conn = new DefaultHttpServerConnection();
				conn.bind(eyefiClient, new BasicHttpParams());
				while (conn.isOpen()) {
					Log.d(TAG, "waiting for request");
					HttpRequest request = conn.receiveRequestHeader();
					String uri = request.getRequestLine().getUri();
					if (uri.equals(
							"/api/soap/eyefilm/v1")) {
						Header[] soapActions = request.getHeaders("SOAPAction");
						if ((soapActions == null) || (soapActions.length == 0)) {
							Log.e(TAG, "no SOAPAction");
							conn.close();
						} else {
							String action = soapActions[0].getValue();
							if (action.equals(URN_STARTSESSION))
								startSession(conn, request);
							else if (action.equals(URN_GETPHOTOSTATUS))
								getPhotoStatus(conn, request);
							else {
								Log.e(TAG, "unknown SOAPAction: " + action);
								conn.close();
							}
						}
					} else if(uri.equals("/api/soap/eyefilm/v1/upload")) {
						uploadPhoto(conn, request);
					} else {
						Log.e(TAG, "unknown method " + uri);
					}
				}
			} catch (IOException e) {
				// TODO: handle exception
				e.printStackTrace();
			} catch (HttpException e) {
				// TODO: handle exception
				e.printStackTrace();
			}
		}
	}
}
