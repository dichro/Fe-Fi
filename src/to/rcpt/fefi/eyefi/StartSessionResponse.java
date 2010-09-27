package to.rcpt.fefi.eyefi;

import java.io.UnsupportedEncodingException;

import org.apache.http.entity.StringEntity;

import to.rcpt.fefi.eyefi.Types.ServerNonce;
import to.rcpt.fefi.eyefi.Types.UploadKey;

public class StartSessionResponse extends EyefiResponse {
	public StartSessionResponse(StartSession ss, UploadKey uploadKey,
			ServerNonce serverNonce) {
		super();
		try {
			setEntity(new StringEntity(
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
							+ "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
							+ "<SOAP-ENV:Body><StartSessionResponse xmlns=\"http://localhost/api/soap/eyefilm\"><credential>"
							+ ss.calculateCredential(uploadKey)
							+ "</credential><snonce>"
							+ serverNonce
							+ "</snonce><transfermode>"
							+ ss.getTransfermode()
							+ "</transfermode><transfermodetimestamp>"
							+ ss.getTransfermodetimestamp()
							+ "</transfermodetimestamp><upsyncallowed>false</upsyncallowed>"
							+ "</StartSessionResponse></SOAP-ENV:Body></SOAP-ENV:Envelope>"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
}
