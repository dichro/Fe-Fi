package to.rcpt.fefi.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import android.util.Log;

public class MultipartInputStream extends FilterInputStream {
	private KMPMatch match;
	private String currentBoundary, originalBoundary;
	private int bufSize = 256 * 1024;
	private int maxRead;
	private byte[] buf;
	private boolean isEof = true;
	private static final String TAG = "MultipartInputStream";
	private int boundaryLength;
	
	public MultipartInputStream(BufferedInputStream is, String boundary) {
		super(is);
		buf = new byte[bufSize];
		originalBoundary = boundary;
		setBoundary(boundary);
	}
	
	public void setBoundary(String b) {
		// must be called only when buffer has been read.
		if(!isEof)
			throw new RuntimeException("bah, humbug");
		currentBoundary = b;
		byte[] pattern = currentBoundary.getBytes();
		match = new KMPMatch(pattern);
		boundaryLength = currentBoundary.length();
		if(boundaryLength > (bufSize / 2))
			throw new RuntimeException("boundary string too long");
		maxRead = bufSize - boundaryLength;
		isEof = false;
	}
	
	protected int readChunk(int requested) throws IOException {
		// we never keep partial data in the buffer between calls to this
		// function. All data read from underlying
		// bufferedInputStream must be consumed by caller or pushed back.
		if(isEof)
			return -1;
		if(requested > maxRead)
			requested = maxRead;
		in.mark(requested);
		int read = in.read(buf, 0, requested);
		if(read == -1) {
			isEof = true;
			return -1;
		}
		// look for boundary
		if(read >= boundaryLength) {
			int boundaryPosition = match.indexOf(buf, 0, read);
			//   if found, push back everything beyond it; return everything above it
			if(boundaryPosition >= 0) {
				// can has boundary! re-read only up to the end of the boundary
				in.reset();
				read = in.read(buf, 0, boundaryPosition + boundaryLength);
				isEof = true;
				return boundaryPosition > 0 ? boundaryPosition : -1;
			}
		}
		// check for a boundary that extends beyond the end of the buffer
		in.mark(boundaryLength);
		int additionalRead = in.read(buf, read, boundaryLength - 1);
		if(additionalRead == -1) {
			isEof = true;
			return read;
		}
		if((read + additionalRead) < boundaryLength) {
			in.reset();
			return read;
		}
		int additionalStart = read - boundaryLength + 1;
		if(additionalStart < 0)
			additionalStart = 0;
		int boundaryPosition = match.indexOf(buf, additionalStart, read + additionalRead - additionalStart);
		//     if found, as above
		if(boundaryPosition >= 0) {
			// can has boundary! re-read only up to the end of the boundary
			in.reset();
			read = in.read(buf, read, boundaryPosition + boundaryLength - read);
			isEof = true;
			return boundaryPosition > 0 ? boundaryPosition : -1;
		}
		//     if not, push back boundaryLength
		in.reset();
		return read;
	}
	
	public void close() throws IOException {
		// reads to next boundary or EOF
		long discarded = 0;
		while(!isEof) {
			discarded += readChunk(maxRead);
		}
		Log.d(TAG, "discarded " + discarded + " in close");
	}
	
	public void mark(int readlimit) {
		Log.e(TAG, "mark attempted");
	}
	
	public boolean markSupported() {
		return false; // fuck that shit
	}
	
	public int available() {
		// retarded
		if(isEof)
			return 0;
		else
			return 1;
	}
	
	public int read(byte[] buffer, int offset, int count) throws IOException {
		int read = readChunk(count);
		if(read < 0)
			return read;
		System.arraycopy(buf, 0, buffer, offset, read);
		return read;
	}
	
	public int read(byte[] buffer) throws IOException {
		return read(buffer, 0, buffer.length);
	}
	
	public int read() throws IOException {
		int read = readChunk(1);
		if(read < 1)
			return -1;
		return buf[0];
	}
	
	public void reset() throws IOException {
		throw new IOException("mark/reset not supported");
	}
	
	public long skip(long count) throws IOException {
		long skipped = 0;
		while(count > skipped) {
			long toSkip = maxRead;
			if(toSkip > count)
				toSkip = count;
			int read = readChunk((int)toSkip);
			if(read <= 0)
				break;
			skipped += read;
		}
		return skipped;
	}
	
	public Map<String, String> getHeaders() throws IOException {
		Map<String, String> headers = new HashMap<String, String>();
		setBoundary("\r\n\r\n");
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		String line = reader.readLine();
		while(line != null) {
			int pos = line.indexOf(": ");
			if(pos > 0)
				headers.put(line.substring(0, pos), line.substring(pos + 2));
			line = reader.readLine();
		}
		setBoundary("\r\n" + originalBoundary);
		return headers;
	}
}
