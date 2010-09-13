package to.rcpt.fefi.eyefi;

import java.io.UnsupportedEncodingException;

import org.apache.http.entity.StringEntity;

public class UploadPhotoResponse extends EyefiResponse {
	public UploadPhotoResponse(boolean success) {
		super();
		try {
			setEntity(new StringEntity(
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
					"<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
					"<SOAP-ENV:Body><UploadPhotoResponse xmlns=\"http://localhost/api/soap/eyefilm\">" +
					"<success>" + success + "</success>" +
					"</UploadPhotoResponse>" +
					"</SOAP-ENV:Body>" +
					"</SOAP-ENV:Envelope>"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
}
