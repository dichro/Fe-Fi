package to.rcpt.fefi.eyefi;

import java.io.InputStream;
import java.security.MessageDigest;

import android.sax.Element;
import android.sax.EndTextElementListener;
import android.sax.RootElement;
import android.util.Log;
import android.util.Xml;

public class StartSession extends EyefiMessage {
	public String getMacaddress() {
		return macaddress;
	}
	public String getCnonce() {
		return cnonce;
	}
	protected void setMacaddress(String macaddress) {
		this.macaddress = macaddress;
	}
	protected void setCnonce(String cnonce) {
		this.cnonce = cnonce;
	}
	String macaddress;
	String cnonce;
	protected void setTransfermode(String transfermode) {
		this.transfermode = transfermode;
	}
	protected void setTransfermodetimestamp(String transfermodetimestamp) {
		this.transfermodetimestamp = transfermodetimestamp;
	}
	public String getTransfermode() {
		return transfermode;
	}
	public String getTransfermodetimestamp() {
		return transfermodetimestamp;
	}
	String transfermode;
	String transfermodetimestamp;
	
	static final String TAG = "StartSession";
	
	public static StartSession parse(InputStream is) {
		String soapNs = "http://schemas.xmlsoap.org/soap/envelope/";
		RootElement soapEnvelope = new RootElement(soapNs, "Envelope");
		Element soapBody = soapEnvelope.getChild(soapNs, "Body");
		String eyefiNs = "EyeFi/SOAP/EyeFilm";
		Element eyefiStartsession = soapBody.getChild(eyefiNs, "StartSession");
		Element eyefiMacaddress = eyefiStartsession.getChild("macaddress");
		Element eyefiCnonce = eyefiStartsession.getChild("cnonce");
		Element eyefiTransfermode = eyefiStartsession.getChild("transfermode");
		Element eyefiTransfermodetimestamp = eyefiStartsession.getChild("transfermodetimestamp");
		final StartSession ss = new StartSession();
		eyefiMacaddress.setEndTextElementListener(new EndTextElementListener() {
			public void end(String body) {
				Log.d(TAG, "parsed mac: " + body);
				ss.setMacaddress(body);
			}
		});
		eyefiCnonce.setEndTextElementListener(new EndTextElementListener() {
			public void end(String body) {
				Log.d(TAG, "parsed cnonce: " + body);
				ss.setCnonce(body);
			}
		});
		eyefiTransfermode.setEndTextElementListener(new EndTextElementListener() {
			public void end(String body) {
				Log.d(TAG, "parsed transfermode: " + body);
				ss.setTransfermode(body);
			}
		});
		eyefiTransfermodetimestamp.setEndTextElementListener(new EndTextElementListener() {
			public void end(String body) {
				Log.d(TAG, "parsed transfermodetimestamp: " + body);
				ss.setTransfermodetimestamp(body);
			}
		});
		try {
			Xml.parse(is, Xml.Encoding.UTF_8, soapEnvelope.getContentHandler());
		} catch (Exception e) {
			return null;
		}
		return ss;
	}
	
	String calculateCredential(String uploadKey) {
		String credential_hex = getMacaddress() + getCnonce() + uploadKey;
		Log.d(TAG, "parsing " + credential_hex);
		int credentialLength = credential_hex.length() / 2;
		byte credential[] = new byte[credentialLength];
		for(int i = 0; i < credentialLength; ++i) {
			String hexpair = credential_hex.substring(2 * i, 2 * i + 2);
			credential[i] = (byte)(Integer.parseInt(hexpair, 16) & 0xff);
		}
		Log.d(TAG, "parsed " + toHexString(credential));
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] authentication = md.digest(credential);
			StringBuffer s = toHexString(authentication);
			return s.toString();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
