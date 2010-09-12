package to.rcpt.fefi.util;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;

import android.util.Log;

public class MultipartInputStream extends FilterInputStream {
	KMPMatch match;
	String boundary;
	int bufSize = 40000;
	int maxRead;
	byte[] buf;
	boolean isEof = true;
	private static final String TAG = "MultipartInputStream";
	private int boundaryLength;
	
	public MultipartInputStream(BufferedInputStream is, String boundary) {
		super(is);
		buf = new byte[bufSize];
		setBoundary(boundary);
	}
	
	public void setBoundary(String b) {
		// must be called only when buffer has been read.
		Log.d(TAG, "new boundary " + b);
		if(!isEof)
			throw new RuntimeException("bah, humbug");
		boundary = b;
		byte[] pattern = boundary.getBytes();
		Log.d(TAG, "new boundary2 " + boundary);
		match = new KMPMatch(pattern);
		Log.d(TAG, "new match " + match);
		boundaryLength = boundary.length();
		if(boundaryLength > (bufSize / 2))
			throw new RuntimeException("boundary string too long");
		maxRead = bufSize - boundaryLength;
		isEof = false;
	}
	
	protected int readChunk(int requested) throws IOException {
		// we never keep partial data in the buffer between calls to this
		// function. All data read from underlying
		// bufferedInputStream must be consumed by caller or pushed back.
		Log.d(TAG, "readChunk " + requested);
		if(isEof)
			return -1;
		if(requested > maxRead)
			requested = maxRead;
		in.mark(requested);
		int read = in.read(buf, 0, requested);
		Log.d(TAG, "read " + read);
		if(read == -1) {
			isEof = true;
			return -1;
		}
		// look for boundary
		if(read >= boundaryLength) {
			int boundaryPosition = match.indexOf(buf, 0, read);
			//   if found, push back everything beyond it; return everything above it
			if(boundaryPosition >= 0) {
				Log.d(TAG, "boundary at " + boundaryPosition);
				// can has boundary! re-read only up to the end of the boundary
				in.reset();
				read = in.read(buf, 0, boundaryPosition + boundaryLength);
				isEof = true;
				return boundaryPosition > 0 ? boundaryPosition : -1;
			}
		}
		//   otherwise read boundaryLength more into the buffer, recheck for presence
		in.mark(boundaryLength);
		int additionalRead = in.read(buf, read, boundaryLength - 1);
		Log.d(TAG, "additionally read " + read);
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
			Log.d(TAG, "boundary at " + boundaryPosition);
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
		while(!isEof) {
			readChunk(maxRead);
		}
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
}
