/*
Copyright (C) 2011-2020 Maksim Petrov

Redistribution and use in source and binary forms, with or without
modification, are permitted for widgets, plugins, applications and other software
which communicate with Poweramp application on Android platform.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE FOUNDATION OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.maxmpz.poweramp.player;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import java.io.FileDescriptor;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Simple "seekable" socket protocol:
 * - unix domain socket is used instead of a pipe for the duplex communication
 * - seek command exposed from {@link #sendData} and can be processed as needed
 * - {@link #sendData} is blocking almost in the same way as standard ParcelFileDescriptor pipe write is
 *
 * NOTE: it's not possible to use timeouts on this side of the socket as Poweramp may open and hold the socket while in paused state for indefinite time
 */
public class TrackProviderProto implements AutoCloseable {
	private static final String TAG = "TrackProviderProto";
	private static final boolean LOG = false;
	/** If true, a bit more checks happen. Disable for production builds */
	private static final boolean DEBUG_CHECKS = false;

	/** Invalid seek position */
	public static final long INVALID_SEEK_POS = Long.MIN_VALUE;

	/** Maximum number of data bytes we're sending in the packet (excluding header overhead) */
	public static final int MAX_DATA_SIZE = 4 * 1024;

	/** Packet header is TAG(4) + PACKET_TYPE(2) + DATA_SIZE(2) + RESERVED(4) => 12 */
	private static final int MAX_PACKET_HEADER_SIZE = 12;

	/** Header(12) + fileLength(8) + size(4) + */
	private static final int INITIAL_PACKET_SIZE = TrackProviderProto.MAX_PACKET_HEADER_SIZE + 8 + 4;

	/** Index of the first data byte in the packet */
	private static final int PACKET_DATA_IX = TrackProviderProto.MAX_PACKET_HEADER_SIZE;

	/** Index of the data size (short) */
	private static final int PACKET_DATA_SIZE_IX = 6;

	private static final int PACKET_TAG = 0xF1F20001;

	private static final short PACKET_TYPE_HEADER   = 1;
	private static final short PACKET_TYPE_DATA     = 2;
	private static final short PACKET_TYPE_SEEK     = 3;
	private static final short PACKET_TYPE_SEEK_RES = 4;

	private static final int STATE_INITIAL = 0;
	private static final int STATE_CLOSED  = 1;
	private static final int STATE_DATA    = 2;

	private static final int LONG_BYTES    = 8;
	private static final int INTEGER_BYTES = 4;


	private final @NonNull FileDescriptor mSocket;
	/** Buffer for header + some extra space for few small packet types */
	private final @NonNull ByteBuffer mHeaderBuffer;
	private final long mFileLength;
	private int mState = TrackProviderProto.STATE_INITIAL;
	private final StructPollfd @NonNull[] mStructPollFds;
	private final @NonNull SeekRequest mTempSeekRequest = new SeekRequest();


	/** Raised if we failed with the connection/action and can't continue anymore */
	@SuppressWarnings("serial")
	public static class TrackProviderProtoException extends RuntimeException {
		public TrackProviderProtoException(final Throwable ex) {
			super(ex);
		}

		public TrackProviderProtoException(final String msg) {
			super(msg);
		}
	}

	/** Raised when connection is closed by Poweramp */
	@SuppressWarnings("serial")
	public static class TrackProviderProtoClosed extends RuntimeException {
		public TrackProviderProtoClosed(final Throwable ex) {
			super(ex);
		}
	}

	/**
	 * Simple data structure with seek offsetBytes and seek milliseconds.<br>
	 * NOTE: there is only on instance of SeekRequest per TrackProviderProto instance, don't share or use from other threads
	 */
	public static class SeekRequest {
		/** >=0 for seek from start of the file, < 0 for seek from the end of the file */
		public long offsetBytes;
		/*
		 *If >=0, provides a hint for the seek request in milliseconds timebase
		 * @since 883
		 */
		public int ms = Integer.MIN_VALUE;

		@Override
		public String toString() {
			return super.toString() + " offsetBytes=" + this.offsetBytes + " ms=" + this.ms;
		}
	}


	/**
	 * @param pfd the socket pfd created by ParcelFileDescriptor.createSocketPair
	 * @param fileLength the actual total length of the track being played
	 */
	@SuppressLint("NewApi")
	public TrackProviderProto(@NonNull final ParcelFileDescriptor pfd, final long fileLength) {
		if(0 >= fileLength) throw new IllegalArgumentException("bad fileLength=" + fileLength);
		final FileDescriptor socket = pfd.getFileDescriptor();
		try {
			if(null == socket || !OsConstants.S_ISSOCK(Os.fstat(socket).st_mode)) throw new IllegalArgumentException("bad pfd=" + pfd);
		} catch(final ErrnoException ex) {
			throw new TrackProviderProtoException(ex);
		}
        this.mSocket = socket;
        this.mFileLength = fileLength;

		final ByteBuffer headerBuffer = ByteBuffer.allocateDirect(TrackProviderProto.INITIAL_PACKET_SIZE);
		if(null == headerBuffer) throw new TrackProviderProtoException("headerBuffer");
        this.mHeaderBuffer = headerBuffer;
        this.mHeaderBuffer.order(ByteOrder.nativeOrder());

        this.mStructPollFds = new StructPollfd[] {
			new StructPollfd()
		};
        this.mStructPollFds[0].fd = this.mSocket;
        this.mStructPollFds[0].events = (short)OsConstants.POLLIN;
	}

	@Override
	public void close() {
		if(TrackProviderProto.LOG) Log.w(TrackProviderProto.TAG, "close");
		if(TrackProviderProto.DEBUG_CHECKS && STATE_CLOSED == mState) throw new AssertionError();
		if(STATE_CLOSED != mState) {
			try {
				Os.shutdown(this.mSocket, 0);
			} catch(final ErrnoException ex) {
				Log.e(TrackProviderProto.TAG, "", ex);
			}
			try {
				Os.close(this.mSocket);
			} catch(final ErrnoException ex) {
				Log.e(TrackProviderProto.TAG, "", ex);
			}
            this.mState = TrackProviderProto.STATE_CLOSED;
			if(TrackProviderProto.LOG) Log.w(TrackProviderProto.TAG, "close OK");
		}
	}

	/** Prepares packet header buffer */
	private @NonNull ByteBuffer preparePacketHeader(final short packetType, final int dataSize) {
		final ByteBuffer buf = this.mHeaderBuffer;
		buf.clear();
		buf.putInt(TrackProviderProto.PACKET_TAG);
		buf.putShort(packetType);
		if(TrackProviderProto.DEBUG_CHECKS && 0 > dataSize || MAX_DATA_SIZE < dataSize) throw new AssertionError(dataSize);
		buf.putShort((short)dataSize);
		buf.putInt(0);
		return buf;
	}

	/** Send the header required to be sent after the socket is connected */
	public void sendHeader() {
		if(TrackProviderProto.LOG) Log.w(TrackProviderProto.TAG, "sendHeader");
		if(STATE_INITIAL == mState) {
			try {
				final ByteBuffer buf = this.preparePacketHeader(TrackProviderProto.PACKET_TYPE_HEADER, TrackProviderProto.LONG_BYTES + TrackProviderProto.INTEGER_BYTES);
				buf.putLong(this.mFileLength);
				buf.putInt(TrackProviderProto.MAX_DATA_SIZE);
				buf.flip();

				while(buf.hasRemaining()) {
					final int res = Os.sendto(this.mSocket, buf, 0, null, 0); // sendto updates buffer position
					if(21 == Build.VERSION.SDK_INT) TrackProviderProto.maybeUpdateBufferPosition(buf, res);
				}

                this.mState = TrackProviderProto.STATE_DATA;
				if(TrackProviderProto.LOG) Log.w(TrackProviderProto.TAG, "sendHeader OK");

			} catch(final ErrnoException | SocketException ex) {
				if(TrackProviderProto.LOG) Log.e(TrackProviderProto.TAG, "", ex);
				throw new TrackProviderProtoException(ex);
			}
		} else if(TrackProviderProto.DEBUG_CHECKS) throw new AssertionError(this.mState);
	}

	/**
	 * Send the data to Poweramp. This will block until Poweramp is resumed, playing, and requires more data<br>
	 * If Poweramp is paused this may be blocked for a very long time, until Poweramp resumes, exists, force-closes, etc.<br><br>
	 *
	 * When Poweramp request seek, this method returns appropriate long seek position (>= 0 for seek from start, < 0 for seek from end)<br>
	 * Poweramp then waits for the PACKET_TYPE_SEEK_RES packet type, ignoring any other packets, so it's required to send PACKET_TYPE_SEEK_RES packet,
	 * or close the connection.
	 *
	 * @param data buffer to send. Should be properly flipped prior sending
	 * @return request for the new seek position, or INVALID_SEEK_POS(==Long.MIN_VALUE) if none requested
	 */
	public long sendData(@NonNull final ByteBuffer data) {
		final SeekRequest request = this.sendData2(data);
		if(null != request) {
			return request.offsetBytes;
		}
		return TrackProviderProto.INVALID_SEEK_POS;
	}

	/**
	 * Send the data to Poweramp. This will block until Poweramp is resumed, playing, and requires more data<br>
	 * If Poweramp is paused this may be blocked for a very long time, until Poweramp resumes, exists, force-closes, etc.<br><br>
	 *
	 * When Poweramp request seek, this method returns appropriate long seek position (>= 0 for seek from start, < 0 for seek from end)<br>
	 * Poweramp then waits for the PACKET_TYPE_SEEK_RES packet type, ignoring any other packets, so it's required to send PACKET_TYPE_SEEK_RES packet,
	 * or close the connection.
	 *
	 * @param data buffer to send. Should be properly flipped prior sending
	 * @return request for the new seek position, or null if none requested
	 */
	public @Nullable SeekRequest sendData2(@NonNull final ByteBuffer data) {
		if(TrackProviderProto.LOG) Log.w(TrackProviderProto.TAG, "sendData2 data.remaining=" + data.remaining());
		if(STATE_DATA == mState) {
			@SuppressWarnings("unused")
			int packetsSent = 0;
			int originalDataLimit = data.limit(); // Keep original limit as we'll modify it to send up to MAX_DATA_SIZE bytes per packet
			while(data.hasRemaining()) {
				try {
					int size = data.remaining();
					if(MAX_DATA_SIZE < size) { // Sending up to MAX_DATA_SIZE
						size = TrackProviderProto.MAX_DATA_SIZE;
					}
					data.limit(data.position() + size);

					final ByteBuffer buf = this.preparePacketHeader(TrackProviderProto.PACKET_TYPE_DATA, size);
					buf.flip();
					while(buf.hasRemaining()) {
						final int res = Os.sendto(this.mSocket, buf, 0, null, 0);
						if(21 == Build.VERSION.SDK_INT) TrackProviderProto.maybeUpdateBufferPosition(buf, res);
					}
					while(data.hasRemaining()) {
						final int res = Os.sendto(this.mSocket, data, 0, null, 0); // data.position changed by # of bytes actually sent
						if(21 == Build.VERSION.SDK_INT) TrackProviderProto.maybeUpdateBufferPosition(data, res);
					}
					packetsSent++;

					buf.clear();

				} catch(final ErrnoException ex) {
					if(ex.errno == OsConstants.ECONNRESET || ex.errno == OsConstants.EPIPE) throw new TrackProviderProtoClosed(ex);
					if(TrackProviderProto.LOG) Log.e(TrackProviderProto.TAG, "", ex);
					throw new TrackProviderProtoException(ex);
				} catch(final SocketException ex) {
					if(TrackProviderProto.LOG) Log.e(TrackProviderProto.TAG, "", ex);
					throw new TrackProviderProtoException(ex);
				} finally {
					data.limit(originalDataLimit); //  Restore limit
				}

				try {
					final int fdsReady = Os.poll(this.mStructPollFds, 0); // Check for possible incoming packet header

					if(1 == fdsReady) {
						final SeekRequest seekPosEncoded = this.readSeekRequest(true); // This shouldn't block as we checked we have some incoming data
						if(null != seekPosEncoded) {
							return seekPosEncoded; // Got valid seek request, return it
						}
					}

				} catch(final ErrnoException ex) {
					if(TrackProviderProto.LOG) Log.e(TrackProviderProto.TAG, "", ex);
				}
			}
			if(TrackProviderProto.LOG) Log.w(TrackProviderProto.TAG, "sendDataPackets OK packetsSent=" + packetsSent);

		} else if(TrackProviderProto.DEBUG_CHECKS) throw new AssertionError(this.mState);

		return null;
	}

	/**
	 * Wait until Poweramp sends seek request or closes socket
	 * @return request for the new seek position, or INVALID_SEEK_POS(==Long.MIN_VALUE) if none requested, socket closed, error happened, etc.
	 */
	public long sendEOFAndWaitForSeekOrClose() {
		if(TrackProviderProto.LOG) Log.w(TrackProviderProto.TAG, "sendEOFAndWaitForSeekOrClose");

		final SeekRequest seekRequest = this.sendEOFAndWaitForSeekOrClose2();
		if(null != seekRequest) {
			if(TrackProviderProto.LOG) Log.w(TrackProviderProto.TAG, "sendEOFAndWaitForSeekOrClose got seek=>" + seekRequest.offsetBytes);
			return seekRequest.offsetBytes;
		}
		if(TrackProviderProto.LOG) Log.w(TrackProviderProto.TAG, "sendEOFAndWaitForSeekOrClose DONE");
		return TrackProviderProto.INVALID_SEEK_POS;
	}

	/**
	 * Wait until Poweramp sends seek request or closes socket
	 * @return request for the new seek position, or null if none requested, socket closed, error happened, etc.
	 */
	public @Nullable SeekRequest sendEOFAndWaitForSeekOrClose2() {
		if(TrackProviderProto.LOG) Log.w(TrackProviderProto.TAG, "waitForSeekOrClose");

		try {
			// Send EOF (empty data buffer)
			final ByteBuffer buf = this.preparePacketHeader(TrackProviderProto.PACKET_TYPE_DATA, 0);
			buf.flip();
			while(buf.hasRemaining()) {
				final int res = Os.sendto(this.mSocket, buf, 0, null, 0);
				if(21 == Build.VERSION.SDK_INT) TrackProviderProto.maybeUpdateBufferPosition(buf, res);
			}
		} catch(final ErrnoException | SocketException ex) {
			if(TrackProviderProto.LOG) Log.e(TrackProviderProto.TAG, "", ex);
			throw new TrackProviderProtoException(ex);
		}

		try {
			return this.readSeekRequest(false);
		} catch(final TrackProviderProtoException ex) {
			return null;
		}
	}

	/**
	 * Try to read Poweramp sends seek request. Blocks until socket has some data
	 * @return request for the new seek position, or null if none requested
	 */
	private @Nullable SeekRequest readSeekRequest(final boolean noBlock) {
		if(TrackProviderProto.LOG) Log.w(TrackProviderProto.TAG, "readSeekRequest");
		try {
			final ByteBuffer buf = this.mHeaderBuffer;
			buf.clear();
			buf.limit(TrackProviderProto.MAX_PACKET_HEADER_SIZE); // Read just header

			int res = Os.recvfrom(this.mSocket, buf, 0, null);
			if(21 == Build.VERSION.SDK_INT) TrackProviderProto.maybeUpdateBufferPosition(buf, res);

			if(MAX_PACKET_HEADER_SIZE == res) {
				final int type = TrackProviderProto.getPacketType(buf);
				final int dataSize = TrackProviderProto.getPacketDataSize(buf);
				if(PACKET_TYPE_SEEK == type && LONG_BYTES <= dataSize) {
					if(TrackProviderProto.LOG) Log.w(TrackProviderProto.TAG, "readSeekRequest got PACKET_TYPE_SEEK dataSize=>" + dataSize);
					buf.limit(buf.limit() + dataSize);

					res = Os.recvfrom(this.mSocket, buf, noBlock ? OsConstants.O_NONBLOCK : 0, null); // Read seek position
					if(21 == Build.VERSION.SDK_INT) TrackProviderProto.maybeUpdateBufferPosition(buf, res);

					if(LONG_BYTES <= res) {
						final SeekRequest seekRequest = this.mTempSeekRequest;
						seekRequest.offsetBytes = buf.getLong(TrackProviderProto.PACKET_DATA_IX);
						if(TrackProviderProto.LOG) Log.w(TrackProviderProto.TAG, "readSeekRequest got offsetBytes=>" + seekRequest.offsetBytes);
						if(LONG_BYTES + TrackProviderProto.INTEGER_BYTES <= res) {
							seekRequest.ms = buf.getInt(TrackProviderProto.PACKET_DATA_IX + TrackProviderProto.LONG_BYTES);
							if(TrackProviderProto.LOG) Log.w(TrackProviderProto.TAG, "readSeekRequest got ms=>" + seekRequest.ms);
						} else {
							seekRequest.ms = Integer.MIN_VALUE;
						}
						return seekRequest;

					} else Log.e(TrackProviderProto.TAG, "readSeekRequest FAIL recvfrom data res=" + res);
				} else
					Log.e(TrackProviderProto.TAG, "readSeekRequest FAIL recvfrom type=" + type + " dataSize=" + dataSize);
			} else if(0 == res) {
				// EOF
				if(TrackProviderProto.LOG) Log.w(TrackProviderProto.TAG, "readSeekRequest EOF");
				return null;
			} else Log.e(TrackProviderProto.TAG, "readSeekRequest FAIL recvfrom res=" + res);
		} catch(final ErrnoException ex) {
			if(TrackProviderProto.LOG) Log.e(TrackProviderProto.TAG, "", ex);
			if(ex.errno == OsConstants.ECONNRESET || ex.errno == OsConstants.EPIPE) throw new TrackProviderProtoClosed(ex);
			if(ex.errno == OsConstants.EAGAIN) { // Timed out
				return null;
			}
			throw new TrackProviderProtoException(ex);
		} catch(final SocketException ex) {
			if(TrackProviderProto.LOG) Log.e(TrackProviderProto.TAG, "", ex);
			throw new TrackProviderProtoException(ex);
		}
		return null;
	}

	/**
	 * Send a seek result as a response to the seek request
	 * @param newPos if >= 0 - indicates a new byte position within the track, or <0 if the seek failed
	 */

	public void sendSeekResult(final long newPos) {
		if(TrackProviderProto.LOG) Log.w(TrackProviderProto.TAG, "sendSeekResult newPos=" + newPos);
		final ByteBuffer buf = this.preparePacketHeader(TrackProviderProto.PACKET_TYPE_SEEK_RES, TrackProviderProto.LONG_BYTES);
		buf.putLong(newPos);
		buf.flip();
		try {
			while(buf.hasRemaining()) {
				final int res = Os.sendto(this.mSocket, buf, 0, null, 0);
				if(21 == Build.VERSION.SDK_INT) TrackProviderProto.maybeUpdateBufferPosition(buf, res);
			}
		} catch(final ErrnoException | SocketException ex) {
			if(TrackProviderProto.LOG) Log.e(TrackProviderProto.TAG, "", ex);
			throw new TrackProviderProtoException(ex);
		}
	}

	/**
	 * @return packetType > 0, or -1 on failure
	 */
	private static int getPacketType(@NonNull final ByteBuffer buf) {
		// Packet header is TAG(4) + PACKET_TYPE(2) + DATA_SIZE(2) + DATA_SERIAL(4) => 12
		if(MAX_PACKET_HEADER_SIZE <= buf.limit() && PACKET_TAG == buf.getInt(0)) {
			final int type = buf.getShort(4);
			final int dataSize = TrackProviderProto.getPacketDataSize(buf);
			if(0 < type && 0 < dataSize) {
				return type;
			}
		}
		return -1;
	}

	/**
	 * @return packet data size
	 */
	private static int getPacketDataSize(@NonNull final ByteBuffer buf) {
		return buf.getShort(TrackProviderProto.PACKET_DATA_SIZE_IX);
	}

	/** Required for Android 5.0.0 which doesn't update buffers */
	private static void maybeUpdateBufferPosition(final ByteBuffer buffer, final int bytesReadOrWritten) {
		if(0 < bytesReadOrWritten) {
			buffer.position(bytesReadOrWritten + buffer.position());
		}
	}
}
