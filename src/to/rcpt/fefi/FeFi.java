package to.rcpt.fefi;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpServerConnection;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.message.BufferedHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.util.CharArrayBuffer;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;

import to.rcpt.fefi.eyefi.GetPhotoStatus;
import to.rcpt.fefi.eyefi.GetPhotoStatusResponse;
import to.rcpt.fefi.eyefi.StartSession;
import to.rcpt.fefi.eyefi.StartSessionResponse;
import to.rcpt.fefi.eyefi.UploadPhoto;
import to.rcpt.fefi.util.MultipartInputStream;

import android.app.Activity;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.MediaStore.Images;
import android.os.Bundle;
import android.util.Log;

public class FeFi extends Activity implements Runnable {
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
		String imageName = "eyefi/" + photoStatus.getParameter("filesignature");
		Log.d(TAG, "searching for " + imageName);
		ContentResolver cr = getContentResolver();
		String[] columns = { BaseColumns._ID };
		String filter = Images.Media.DISPLAY_NAME + " = ? ";
		String placeholders[] = { imageName };
		Cursor results = cr.query(Images.Media.EXTERNAL_CONTENT_URI, columns,
				filter, placeholders, null);
		boolean imageExists = results.moveToFirst();
		results.close();
		Log.d(TAG, "existence: " + imageExists);
		// TODO: how to reject an image? offset presumably resumes upload;
		// what's the point of fileid? oh - returned by client in upload
		GetPhotoStatusResponse gpsr = new GetPhotoStatusResponse(photoStatus,
				1, 0);
		conn.sendResponseHeader(gpsr);
		conn.sendResponseEntity(gpsr);
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
						Log.d(TAG, "upload " + request.toString());
						HttpEntityEnclosingRequest eyefiRequest = (HttpEntityEnclosingRequest) request;
						Header contentTypes[] = eyefiRequest.getHeaders("Content-type");
						if((contentTypes == null) || contentTypes.length < 1) {
							Log.e(TAG, "no content type in upload request");
							conn.close();
						}
						Log.d(TAG, "contentTypes: " + contentTypes.length);
						Log.d(TAG, "content-type " + contentTypes[0].getValue());
						HeaderElement elements[] = contentTypes[0].getElements();
						Log.d(TAG, "elements: " + elements.length);
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
						long skipped = in.skip(10000);
						Log.d(TAG, "skipped " + skipped + " to find first boundary");
						// process headers
						in.setBoundary("\r\n\r\n");
						BufferedReader reader = new BufferedReader(new InputStreamReader(in));
						String line = reader.readLine();
						while(line != null) {
							Log.d(TAG, "read header " + line);
							line = reader.readLine();
						}
						// check we got a SOAPENVELOPE
						in.setBoundary(boundary);
						UploadPhoto up = new UploadPhoto();
						up.parse(in);
						skipped = in.skip(1000);
						Log.d(TAG, "parsed uploadphoto skipped " + skipped + " to find second boundary");
						// process headers
						in.setBoundary("\r\n\r\n");
						reader = new BufferedReader(new InputStreamReader(in));
						line = reader.readLine();
						while(line != null) {
							Log.d(TAG, "read header " + line);
							line = reader.readLine();
						}
						// confirm we're looking at the tar file
						in.setBoundary(boundary);
						TarInputStream tar = new TarInputStream(in);
						TarEntry entry = tar.getNextEntry();
						while(entry != null) {
							Log.d(TAG, "Found " + entry.getName() + " of " + entry.getSize() + " bytes");
							entry = tar.getNextEntry();
						}
						tar.close();
						skipped = in.skip(10000000);
						Log.d(TAG, "skipped " + skipped + " to find third boundary");
						// process headers
						in.setBoundary("\r\n\r\n");
						reader = new BufferedReader(new InputStreamReader(in));
						line = reader.readLine();
						while(line != null) {
							Log.d(TAG, "read header " + line);
							line = reader.readLine();
						}
						// more stupid code here
						String line2 = ""; // read from inputstream
						CharArrayBuffer headerLine = new CharArrayBuffer(line2.length());
						headerLine.append(line2);
						BufferedHeader header = new BufferedHeader(headerLine);
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
