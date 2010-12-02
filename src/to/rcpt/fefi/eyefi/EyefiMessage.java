package to.rcpt.fefi.eyefi;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;

import to.rcpt.fefi.eyefi.Types.MacAddress;

import android.sax.Element;
import android.sax.EndTextElementListener;
import android.sax.RootElement;
import android.util.Log;
import android.util.Xml;

public class EyefiMessage {
	private static String TAG = "EyefiMessage";
	private String name = "EyefiMessage";
	private String content = "";

	class ContentSnatcher implements EndTextElementListener {
		String key;
		
		public ContentSnatcher(String key) {
			this.key = key;
		}
		
		public void end(String body) {
			contents.put(key, body);
		}
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

	public MacAddress getMacAddress() {
		return new MacAddress(getParameter(MACADDRESS));
	}
	
	@Override
	public String toString() {
		return name + ": " + content;
	}
	
	public void parse(String type, InputStream is) {
		setupParser(type);
		name = type;
		
		try {
			Writer writer = new StringWriter();
			Reader reader = new BufferedReader(new InputStreamReader(is));
			char tmp[] = new char[1024]; // ugh
			int n;
			
			while((n = reader.read(tmp)) != -1)
				writer.write(tmp, 0, n);
			content = writer.toString();
			
			Log.d(TAG, "parsing " + type + " from " + content);
			Xml.parse(content, soapEnvelope.getContentHandler());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
