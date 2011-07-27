package to.rcpt.fefi.eyefi;

import java.io.UnsupportedEncodingException;

import org.apache.http.entity.StringEntity;

public class MarkLastPhotoInRollResponse extends EyefiResponse {
	public MarkLastPhotoInRollResponse() {
		try {
			setEntity(new StringEntity(
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
					"<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
					"<SOAP-ENV:Body>" +
					"<ns1:MarkLastPhotoInRollResponse xmlns:ns1=\"http://localhost/api/soap/eyefilm\" />" +
					"</SOAP-ENV:Body>" +
					"</SOAP-ENV:Envelope>"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
}
