package to.rcpt.fefi.eyefi;

import java.io.InputStream;
import java.security.MessageDigest;

import to.rcpt.fefi.eyefi.Types.MacAddress;
import to.rcpt.fefi.eyefi.Types.ServerNonce;
import to.rcpt.fefi.eyefi.Types.UploadKey;
import to.rcpt.fefi.util.Hexstring;

import android.sax.Element;
import android.sax.EndTextElementListener;
import android.sax.RootElement;
import android.util.Log;
import android.util.Xml;

public class StartSession extends EyefiMessage {
	public MacAddress getMacaddress() {
		return new MacAddress(macaddress);
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
	
	ServerNonce calculateCredential(UploadKey uploadKey) {
		String credential_hex = getMacaddress() + getCnonce() + uploadKey;
		Log.d(TAG, "parsing " + credential_hex);
		Hexstring credential = new Hexstring(credential_hex);
		Log.d(TAG, "parsed " + credential);
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] authentication = md.digest(credential.toBytes());
			ServerNonce c = new ServerNonce(authentication);
			return c;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
