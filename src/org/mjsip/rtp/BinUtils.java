package org.mjsip.rtp;


/** collection of some methods for manipulating binary data.
 */
class BinUtils {

	/** Gets int value. */
	static int getInt(byte b) {
		return ((int)b+256)%256;
	}

	/** Gets long value. */
	static long getLong(byte[] data, int begin, int end) {
		long n=0;
		for (; begin<end; begin++) {
			n<<=8;
			n+=getInt(data[begin]);
		}
		return n;
	}

	/** Sets long value. */
	static void setLong(long n, byte[] data, int begin, int end) {
		for (end-- ; end>=begin; end--) {
			data[end]=(byte)(n%256);
			n>>=8;
		}
	}

	/** Gets Int value. */
	static int getInt(byte[] data, int begin, int end) {
		return (int)getLong(data,begin,end);
	}

	/** Sets Int value. */
	static void setInt(int n, byte[] data, int begin, int end) {
		setLong(n,data,begin,end);
	}

	/** Gets bit value. */
	static boolean getBit(byte b, int bit) {
		return (b>>bit)==1;
	}

	/** Sets bit value. */
	static void setBit(boolean value, byte b, int bit) {
		if (value) b=(byte)(b|(1<<bit));
		else b=(byte)((b|(1<<bit))^(1<<bit));
	}

}
