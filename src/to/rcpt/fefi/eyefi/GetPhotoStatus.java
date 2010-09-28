package to.rcpt.fefi.eyefi;

import java.io.InputStream;
import java.security.MessageDigest;

import to.rcpt.fefi.eyefi.Types.MacAddress;
import to.rcpt.fefi.eyefi.Types.ServerNonce;
import to.rcpt.fefi.eyefi.Types.UploadKey;
import to.rcpt.fefi.util.Hexstring;

import android.util.Log;
import android.util.Xml;

public class GetPhotoStatus extends EyefiMessage {
	public static final String TAG = "GetPhotoStatus";
	public void parse(InputStream is) {
		setupParser("GetPhotoStatus");
		
		try {
			Xml.parse(is, Xml.Encoding.UTF_8, soapEnvelope.getContentHandler());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public void authenticate(UploadKey uploadKey, ServerNonce serverNonce) {
		String credential_hex = getParameter(MACADDRESS) + uploadKey + serverNonce;
		Log.d(TAG, "parsing " + credential_hex);
		Hexstring hs = new Hexstring(credential_hex);
		Log.d(TAG, "parsed  " + hs);
		MacAddress c;
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] authentication = md.digest(hs.toBytes());
			c = new MacAddress(authentication);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		Log.d(TAG, "calculated credential string " + c);
		String suppliedCredential = getParameter(CREDENTIAL);
		Log.d(TAG, "supplied credential string " + suppliedCredential);
		if(!suppliedCredential.equals(c.toString())) {
			Log.e(TAG, "FAIL!");
			throw new RuntimeException(); // TODO: make a better exception
		}
	}
}
