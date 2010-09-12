package to.rcpt.fefi.eyefi;

import java.util.HashMap;

import android.sax.Element;
import android.sax.EndTextElementListener;
import android.sax.RootElement;
import android.util.Log;

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

	void setupParameter(String parameter) {		
		op.getChild(parameter).setEndTextElementListener(new ContentSnatcher(parameter));		
	}

	void setupParser(String eyefiOperation) {
		String soapNs = "http://schemas.xmlsoap.org/soap/envelope/";
		String eyefiNs = "EyeFi/SOAP/EyeFilm";
		soapEnvelope = new RootElement(soapNs, "Envelope");
		op = soapEnvelope.getChild(soapNs, "Body").getChild(eyefiNs, eyefiOperation);
	}

	public String getParameter(String parameter) {
		String value = contents.get(parameter);
		if(value == null)
			throw new RuntimeException(); // TODO: make a useful exception
		return value;
	}

}
