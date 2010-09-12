package to.rcpt.fefi.eyefi;

import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHttpResponse;

public class EyefiResponse extends BasicHttpResponse {
	public EyefiResponse() {
		super(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
		setHeader("Server", "Eye-Fi Agent/2.0.4.0 (Windows XP SP2)");
		setHeader("Pragma", "no-cache");
		setHeader("Content-Type", "text/xml; charset=\"utf-8\"");
	}
}
