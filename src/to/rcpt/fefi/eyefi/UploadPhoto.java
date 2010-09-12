package to.rcpt.fefi.eyefi;

import java.io.InputStream;

import android.util.Xml;

public class UploadPhoto extends EyefiMessage {
	public void parse(InputStream is) {
		setupParser("UploadPhoto");
		
		try {
			Xml.parse(is, Xml.Encoding.UTF_8, soapEnvelope.getContentHandler());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
