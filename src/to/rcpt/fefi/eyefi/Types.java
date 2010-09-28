package to.rcpt.fefi.eyefi;

import to.rcpt.fefi.util.Hexstring;

public class Types {
	public static class UploadKey extends Hexstring {
		public UploadKey(byte[] b) {
			super(b);
		}
		
		public UploadKey(String s) {
			super(s);
		}
	}

	public static class Credential extends Hexstring {
		public Credential(byte[] b) {
			super(b);
		}
		
		public Credential(String s) {
			super(s);
		}
	}

	public static class MacAddress extends Hexstring {
		public MacAddress(byte[] b) {
			super(b);
		}
		
		public MacAddress(String s) {
			super(s);
		}
	}

	public static class ServerNonce extends Hexstring {
		public ServerNonce(byte[] b) {
			super(b);
		}
		
		public ServerNonce(String s) {
			super(s);
		}
	}
}
