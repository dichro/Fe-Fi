package to.rcpt.fefi.eyefi;

import java.util.HashMap;

import android.sax.Element;
import android.sax.EndTextElementListener;
import android.sax.RootElement;

public class EyefiMessage {


	class ContentSnatcher implements EndTextElementListener {
		String key;
		
		public ContentSnatcher(String key) {
			this.key = key;
		}
		
		public void end(String body) {
			contents.put(key, body);
		}
	}

	public static StringBuffer toHexString(byte[] byteString) {
		StringBuffer s = new StringBuffer();
		for(int i = 0; i < byteString.length; ++i) {
			String stringByte = Integer.toHexString(((int)byteString[i]) & 0xff);
			if(stringByte.length() == 1)
				s.append("0");			
			s.append(stringByte);
		}
		return s;
	}

	protected HashMap<String, String> contents = new HashMap<String, String>();
	protected Element op;
	protected RootElement soapEnvelope;
	protected static final String CREDENTIAL = "credential";
	protected static final String FILENAME = "filename";
	protected static final String FILESIGNATURE = "filesignature";
	protected static final String FILESIZE = "filesize";
	protected static final String FLAGS = "flags";
	protected static final String MACADDRESS = "macaddress";

	void setupParameter(String parameter) {		
		op.getChild(parameter).setEndTextElementListener(new ContentSnatcher(parameter));		
	}

	void setupParser(String eyefiOperation) {
		String soapNs = "http://schemas.xmlsoap.org/soap/envelope/";
		String eyefiNs = "EyeFi/SOAP/EyeFilm";
		soapEnvelope = new RootElement(soapNs, "Envelope");
		op = soapEnvelope.getChild(soapNs, "Body").getChild(eyefiNs, eyefiOperation);
		setupParameter(CREDENTIAL);
		setupParameter(MACADDRESS);
		setupParameter(FILENAME);
		setupParameter(FILESIZE);
		setupParameter(FILESIGNATURE);
		setupParameter(FLAGS);
	}

	public String getParameter(String parameter) {
		String value = contents.get(parameter);
		if(value == null)
			throw new RuntimeException(); // TODO: make a useful exception
		return value;
	}

}
