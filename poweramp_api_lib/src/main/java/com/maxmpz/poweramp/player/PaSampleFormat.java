package com.maxmpz.poweramp.player;


import org.eclipse.jdt.annotation.NonNull;


public enum PaSampleFormat {
    ;
    public static final int PA_SAMPLE_FMT_NONE     = -1;

	public static final int PA_SAMPLE_FMT_U8       = 0;      ///< unsigned 8 bits
	public static final int PA_SAMPLE_FMT_S16      = 1;      ///< signed 16 bits
	public static final int PA_SAMPLE_FMT_S32      = 2;      ///< signed 32 bits
	public static final int PA_SAMPLE_FMT_FLT      = 3;      ///< float
	public static final int PA_SAMPLE_FMT_DBL      = 4;      ///< double
	public static final int PA_SAMPLE_FMT_U8P      = 5;      ///< unsigned 8 bits, planar
	public static final int PA_SAMPLE_FMT_S16P     = 6;      ///< signed 16 bits, planar
	public static final int PA_SAMPLE_FMT_S32P     = 7;      ///< signed 32 bits, planar
	public static final int PA_SAMPLE_FMT_FLTP     = 8;      ///< float, planar
	public static final int PA_SAMPLE_FMT_DBLP     = 9;      ///< double, planar
	public static final int PA_SAMPLE_FMT_S64      = 10; ///< signed 64 bits
	public static final int PA_SAMPLE_FMT_S64P     = 11; ///< signed 64 bits, planar
	// 12..19 are reserved, but still valid values
	public static final int PA_SAMPLE_FMT_S24      = 20; // packed 24bit   
	public static final int PA_SAMPLE_FMT_S8_24    = 21; // Android Q8.23
	// 14

	/** NOTE: this indicates max sample format + 1, as we have reserved values */
	public static final int PA_SAMPLE_FMT_NB       = 22;


	public static boolean isValidFormat(final int format, final boolean allowReserve) {
		if(0 > format) return false;
		if(PA_SAMPLE_FMT_NB <= format) return false;
        return allowReserve || PA_SAMPLE_FMT_S64P >= format || PA_SAMPLE_FMT_S24 <= format;
    }

	/** This is storage bits per given sample */
	public static int getBitsPerSample(final int sampleFormat) {
		switch(sampleFormat) {
			default:
			case PaSampleFormat.PA_SAMPLE_FMT_NONE:
				return 0;
			case PaSampleFormat.PA_SAMPLE_FMT_U8:
				return 8;
			case PaSampleFormat.PA_SAMPLE_FMT_S16:
				return 16;
			case PaSampleFormat.PA_SAMPLE_FMT_S32:
				return 32;
			case PaSampleFormat.PA_SAMPLE_FMT_FLT:
				return 32;
			case PaSampleFormat.PA_SAMPLE_FMT_DBL:
				return 64;
			case PaSampleFormat.PA_SAMPLE_FMT_U8P:
				return 8;
			case PaSampleFormat.PA_SAMPLE_FMT_S16P:
				return 16;
			case PaSampleFormat.PA_SAMPLE_FMT_S32P:
				return 32;
			case PaSampleFormat.PA_SAMPLE_FMT_FLTP:
				return 32;
			case PaSampleFormat.PA_SAMPLE_FMT_DBLP:
				return 64;
			case PaSampleFormat.PA_SAMPLE_FMT_S24:
				return 24;
			case PaSampleFormat.PA_SAMPLE_FMT_S8_24:
				return 32;
			case PaSampleFormat.PA_SAMPLE_FMT_S64:
			case PaSampleFormat.PA_SAMPLE_FMT_S64P:
				return 64;
		}
	}

	/** This is storage bits per given sample */
	public static int getBytesPerSample(final int sampleFormat) {
		return PaSampleFormat.getBitsPerSample(sampleFormat) / 8;
	}

	/** This is significant range bits per given sample, i.e. 24 for Float32 or S8_24 */
	public static int getSignificantBitsPerSample(final int sampleFormat) {
		switch(sampleFormat) {
			default:
			case PaSampleFormat.PA_SAMPLE_FMT_NONE:
				return 0;
			case PaSampleFormat.PA_SAMPLE_FMT_U8:
				return 8;
			case PaSampleFormat.PA_SAMPLE_FMT_S16:
				return 16;
			case PaSampleFormat.PA_SAMPLE_FMT_S32:
				return 32;
			case PaSampleFormat.PA_SAMPLE_FMT_FLT:
				return 24;
			case PaSampleFormat.PA_SAMPLE_FMT_FLTP:
				return 24;
			case PaSampleFormat.PA_SAMPLE_FMT_DBL:
				return 53;
			case PaSampleFormat.PA_SAMPLE_FMT_U8P:
				return 8;
			case PaSampleFormat.PA_SAMPLE_FMT_S16P:
				return 16;
			case PaSampleFormat.PA_SAMPLE_FMT_S32P:
				return 32;
			case PaSampleFormat.PA_SAMPLE_FMT_DBLP:
				return 53;
			case PaSampleFormat.PA_SAMPLE_FMT_S24:
				return 24;
			case PaSampleFormat.PA_SAMPLE_FMT_S8_24: // This is Q8.23
				return 24; // NOTE: not sure about this, actually this is closer to normal float, thus this is 24 bit
			case PaSampleFormat.PA_SAMPLE_FMT_S64:
			case PaSampleFormat.PA_SAMPLE_FMT_S64P:
				return 53;
		}
	}
}
