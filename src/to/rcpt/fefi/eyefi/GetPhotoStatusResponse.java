package to.rcpt.fefi.eyefi;

import java.io.UnsupportedEncodingException;

import org.apache.http.entity.StringEntity;

public class GetPhotoStatusResponse extends EyefiResponse {
	public GetPhotoStatusResponse(GetPhotoStatus getStatus, int fileid, int offset) {
		super();
		try {
			setEntity(new StringEntity(
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
					"<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
					"<SOAP-ENV:Body><GetPhotoStatusResponse xmlns=\"http://localhost/api/soap/eyefilm\">" +
					"<fileid>" + fileid + "</fileid>" +
					"<offset>" + offset + "</offset>" +
					"</GetPhotoStatusResponse>" +
					"</SOAP-ENV:Body>" +
					"</SOAP-ENV:Envelope>"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
}
