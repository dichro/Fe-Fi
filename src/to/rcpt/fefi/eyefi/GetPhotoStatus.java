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
		Hexstring hs = new Hexstring(credential_hex);
		MacAddress c;
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] authentication = md.digest(hs.toBytes());
			c = new MacAddress(authentication);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		String suppliedCredential = getParameter(CREDENTIAL);
		if(!suppliedCredential.equals(c.toString())) {
			Log.e(TAG, "FAIL!");
			throw new RuntimeException(); // TODO: make a better exception
		}
	}
}
