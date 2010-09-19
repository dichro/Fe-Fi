package to.rcpt.fefi.eyefi;

import java.io.InputStream;
import java.security.MessageDigest;

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
	
	public void authenticate(byte[] uploadKey, String serverNonce) {
		String credential_hex = getParameter(MACADDRESS) + toHexString(uploadKey) + serverNonce;
		Log.d(TAG, "parsing " + credential_hex);
		int credentialLength = credential_hex.length() / 2;
		byte credential[] = new byte[credentialLength];
		for(int i = 0; i < credentialLength; ++i) {
			String hexpair = credential_hex.substring(2 * i, 2 * i + 2);
			credential[i] = (byte)(Integer.parseInt(hexpair, 16) & 0xff);
		}
		Log.d(TAG, "parsed  " + toHexString(credential));
		StringBuffer s;
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] authentication = md.digest(credential);
			s = toHexString(authentication);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		Log.d(TAG, "calculated credential string " + s);
		String suppliedCredential = getParameter(CREDENTIAL);
		Log.d(TAG, "supplied credential string " + suppliedCredential);
		if(!s.equals(suppliedCredential))
//			throw new RuntimeException(); // TODO: make a better exception
			Log.e(TAG, "FAIL!");
			// TODO: FIX authentication!
	}
}
