package to.rcpt.fefi.eyefi;

import java.io.InputStream;
import java.security.MessageDigest;

import to.rcpt.fefi.eyefi.Types.MacAddress;
import to.rcpt.fefi.eyefi.Types.ServerNonce;
import to.rcpt.fefi.eyefi.Types.UploadKey;
import to.rcpt.fefi.util.Hexstring;

public class StartSession extends EyefiMessage {
	public MacAddress getMacaddress() {
		return new MacAddress(getParameter(MACADDRESS));
	}
	public String getCnonce() {
		return getParameter(CNONCE);
	}
	public String getTransfermode() {
		return getParameter(TRANSFERMODE);
	}
	public String getTransfermodetimestamp() {
		return getParameter(TRANSFERMODETIMESTAMP);
	}
	
	static final String TAG = "StartSession";
	
	public void parse(InputStream is) {
		parse("StartSession", is);
	}
	
	ServerNonce calculateCredential(UploadKey uploadKey) {
		String credential_hex = getMacaddress() + getCnonce() + uploadKey;
		Hexstring credential = new Hexstring(credential_hex);
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
