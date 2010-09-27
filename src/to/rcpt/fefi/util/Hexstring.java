package to.rcpt.fefi.util;

public class Hexstring {
	private String string;
	private byte[] bytes;
	
	public Hexstring(String s) {
		string = s;
		bytes = toByteArray(s);
	}

	public Hexstring(byte[] b) {
		bytes = b;
		string = toHexString(b).toString();
	}

	public String toString() {
		return string;
	}
	
	public byte[] toBytes() {
		return bytes;
	}
	
	public static byte[] toByteArray(String string) {
		int length = string.length() / 2;
		byte ret[] = new byte[length];
		for(int i = 0; i < length; ++i) {
			String hexpair = string.substring(2 * i, 2 * i + 2);
			ret[i] = (byte)(Integer.parseInt(hexpair, 16) & 0xff);
		}
		return ret;
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
}
