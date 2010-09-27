package to.rcpt.fefi.eyefi;

import java.util.HashMap;

import android.sax.Element;
import android.sax.EndTextElementListener;
import android.sax.RootElement;
import android.util.Log;

public class EyefiMessage {
	private static String TAG = "EyefiMessage";

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
	public static final String CREDENTIAL = "credential";
	public static final String FILENAME = "filename";
	public static final String FILESIGNATURE = "filesignature";
	public static final String FILESIZE = "filesize";
	public static final String FLAGS = "flags";
	public static final String MACADDRESS = "macaddress";

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
		if(value == null) {
			Log.d(TAG, "no " + parameter + " found in xml");
			throw new RuntimeException(); // TODO: make a useful exception
		}
		return value;
	}

}
