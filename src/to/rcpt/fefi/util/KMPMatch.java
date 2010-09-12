package to.rcpt.fefi.util;

/**
 * Knuth-Morris-Pratt Algorithm for Pattern Matching
 * Quoted by Chris Smith in http://www.velocityreviews.com/forums/t129673-search-byte-for-pattern.html
 */
public class KMPMatch {
	private int[] failures;
	private byte[] pattern;

	public KMPMatch(byte[] pattern) {
		failures = computeFailure(pattern);
		this.pattern = pattern;
	}

	public int indexOf(byte[] data) {
		return indexOf(data, 0, data.length);
	}
	
	/**
	 * Finds the first occurrence of the pattern in the text.
	 */
	public int indexOf(byte[] data, int dataOffset, int dataLength) {
		int j = 0;

		for (int i = dataOffset; i < dataLength; i++) {
			while (j > 0 && pattern[j] != data[i]) {
				j = failures[j - 1];
			}
			if (pattern[j] == data[i]) {
				j++;
			}
			if (j == pattern.length) {
				return i - pattern.length + 1;
			}
		}
		return -1;
	}

	/**
	 * Computes the failure function using a boot-strapping process, where the
	 * pattern is matched against itself.
	 */
	private int[] computeFailure(byte[] pattern) {
		int[] failure = new int[pattern.length];

		int j = 0;
		for (int i = 1; i < pattern.length; i++) {
			while (j > 0 && pattern[j] != pattern[i]) {
				j = failure[j - 1];
			}
			if (pattern[j] == pattern[i]) {
				j++;
			}
			failure[i] = j;
		}

		return failure;
	}
}
