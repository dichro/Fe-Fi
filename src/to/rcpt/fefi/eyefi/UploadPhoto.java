package to.rcpt.fefi.eyefi;

import java.io.InputStream;

public class UploadPhoto extends EyefiMessage {
	public void parse(InputStream is) {
		parse("UploadPhoto", is);
	}
}
