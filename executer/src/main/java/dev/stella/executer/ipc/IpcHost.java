package dev.stella.executer.ipc;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Hosts the shared-memory IPC channel on the Java side.
 *
 * The channel is a regular file mapped into memory. The native client opens
 * the same file (CreateFileMappingW over the same path); Windows keeps both
 * views page-coherent, giving a zero-copy bidirectional channel made of two
 * SPSC ring buffers (native->java and java->native).
 */
public final class IpcHost {
    private static final VarHandle INT_HANDLE =
            MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.nativeOrder());

    /** A decoded inbound message. */
    public static final class Msg {
        public int opcode;
        public byte[] payload = EMPTY;

        Msg(int capacity) {
            if (capacity > 0) {
                payload = new byte[capacity];
            }
        }
    }

    private static final byte[] EMPTY = new byte[0];

    private final MappedByteBuffer map;
    private final Ring rx; // native -> java (we consume)
    private final Ring tx; // java -> native (we produce)

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread readerThread;
    private volatile BiConsumer<Integer, byte[]> dispatcher;

    public IpcHost() throws IOException {
        String path = System.getProperty("stella.ipcfile",
                System.getProperty("java.io.tmpdir") + "\\stella_ipc.bin");

        try (RandomAccessFile raf = new RandomAccessFile(path, "rw")) {
            if (raf.length() != Protocol.FILE_SIZE) {
                raf.setLength(Protocol.FILE_SIZE);
            }
            map = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, Protocol.FILE_SIZE);
        }

        putInt(Protocol.H_MAGIC, Protocol.MAGIC);
        putInt(Protocol.H_VERSION, Protocol.VERSION);
        putInt(Protocol.H_DATA_CAP, Protocol.DATA_CAP);

        this.tx = new Ring((int) Protocol.OFF_JAVA_TO_NATIVE);
        this.rx = new Ring((int) Protocol.OFF_NATIVE_TO_JAVA);

        // --- seamless reconnection ---
        // Reset our tx ring meta (head=0, tail=0) so it starts empty
        INT_HANDLE.setVolatile(map, (int) Protocol.OFF_JAVA_TO_NATIVE + Protocol.RING_HEAD, 0);
        INT_HANDLE.setVolatile(map, (int) Protocol.OFF_JAVA_TO_NATIVE + Protocol.RING_TAIL, 0);
        // Drain any stale messages left in our rx ring (native->java)
        Msg discard = new Msg(0);
        while (rx.read(discard)) { /* discard */ }
    }

    public void start(BiConsumer<Integer, byte[]> dispatcher) {
        this.dispatcher = dispatcher;
        if (!running.compareAndSet(false, true)) {
            return;
        }
        readerThread = new Thread(this::readerLoop, "Stella-IPC-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void stop() {
        running.set(false);
        if (readerThread != null) {
            readerThread.interrupt();
        }
    }

    /** Send a message to the native client. Returns false if the ring is full. */
    public boolean send(int opcode, byte[] payload) {
        return tx.write(opcode, payload == null ? EMPTY : payload);
    }

    /** Reset our tx ring (java->native) meta to clean state. Called on native reconnect. */
    public void resetTx() {
        INT_HANDLE.setVolatile(map, (int) Protocol.OFF_JAVA_TO_NATIVE + Protocol.RING_HEAD, 0);
        INT_HANDLE.setVolatile(map, (int) Protocol.OFF_JAVA_TO_NATIVE + Protocol.RING_TAIL, 0);
    }

    private void readerLoop() {
        while (running.get()) {
            boolean didWork = false;
            Msg msg = new Msg(0);
            while (rx.read(msg)) {
                didWork = true;
                byte[] payloadCopy = new byte[msg.payload.length];
                System.arraycopy(msg.payload, 0, payloadCopy, 0, msg.payload.length);
                BiConsumer<Integer, byte[]> d = dispatcher;
                if (d != null) {
                    try {
                        d.accept(msg.opcode, payloadCopy);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            if (!didWork) {
                Thread.onSpinWait();
            }
        }
    }

    /**
     * SPSC ring over one region of the shared mapping.
     * Record layout: [u32 payloadLen][u32 opcode][payload bytes...]
     * Indices are u32 monotonic counters; data area size is a power of two,
     * so positions map via idx & mask even across u32 wraparound.
     */
    private final class Ring {
        private final int base;
        private final int mask = Protocol.DATA_CAP - 1;

        Ring(int base) {
            this.base = base;
        }

        private int head() {
            return (int) INT_HANDLE.getAcquire(map, base + Protocol.RING_HEAD);
        }

        private int tail() {
            return (int) INT_HANDLE.getAcquire(map, base + Protocol.RING_TAIL);
        }

        private void setHead(int v) {
            INT_HANDLE.setRelease(map, base + Protocol.RING_HEAD, v);
        }

        private void setTail(int v) {
            INT_HANDLE.setRelease(map, base + Protocol.RING_TAIL, v);
        }

        private int dataPos(long logicalIdx) {
            return base + Protocol.RING_DATA + (int) ((logicalIdx & 0xFFFFFFFFL) & mask);
        }

        private void putIntAt(long logicalIdx, int value) {
            int pos = dataPos(logicalIdx);
            map.put(pos, (byte) value);
            map.put(pos + 1, (byte) (value >>> 8));
            map.put(pos + 2, (byte) (value >>> 16));
            map.put(pos + 3, (byte) (value >>> 24));
        }

        private int getIntAt(long logicalIdx) {
            int pos = dataPos(logicalIdx);
            return (map.get(pos) & 0xFF)
                    | ((map.get(pos + 1) & 0xFF) << 8)
                    | ((map.get(pos + 2) & 0xFF) << 16)
                    | ((map.get(pos + 3) & 0xFF) << 24);
        }

        // Bulk write: handles wrap-around by splitting into two parts
        private void bulkPut(long logicalIdx, byte[] src, int off, int len) {
            int pos = dataPos(logicalIdx);
            int first = Math.min(len, Protocol.DATA_CAP - (pos - base - Protocol.RING_DATA));
            map.position(pos);
            map.put(src, off, first);
            if (first < len) {
                map.position(base + Protocol.RING_DATA);
                map.put(src, off + first, len - first);
            }
        }

        // Bulk read: handles wrap-around by splitting into two parts
        private void bulkGet(long logicalIdx, byte[] dst, int off, int len) {
            int pos = dataPos(logicalIdx);
            int first = Math.min(len, Protocol.DATA_CAP - (pos - base - Protocol.RING_DATA));
            map.position(pos);
            map.get(dst, off, first);
            if (first < len) {
                map.position(base + Protocol.RING_DATA);
                map.get(dst, off + first, len - first);
            }
        }

        // ---- producer side ----

        boolean write(int opcode, byte[] payload) {
            long total = 8 + payload.length;
            if (total > Protocol.DATA_CAP - 1) {
                throw new IllegalArgumentException("payload too large");
            }
            int hRaw = head();
            int tRaw = tail();
            long used = ((long) hRaw - (long) tRaw) & 0xFFFFFFFFL;
            if (used + total > Protocol.DATA_CAP - 1) {
                return false; // full
            }
            long head = hRaw & 0xFFFFFFFFL;
            putIntAt(head, payload.length);
            putIntAt(head + 4, opcode);
            bulkPut(head + 8, payload, 0, payload.length);
            setHead((int) ((head + total) & 0xFFFFFFFFL));
            return true;
        }

        // ---- consumer side ----

        boolean read(Msg out) {
            int hRaw = head();
            int tRaw = tail();
            if (hRaw == tRaw) {
                return false;
            }
            long tail = tRaw & 0xFFFFFFFFL;
            long len = getIntAt(tail) & 0xFFFFFFFFL;
            long total = 8 + len;
            long used = ((long) hRaw - (long) tRaw) & 0xFFFFFFFFL;
            if (used < total) {
                return false; // not fully published yet
            }
            out.opcode = getIntAt(tail + 4);
            if (out.payload.length != (int) len) {
                out.payload = new byte[(int) len];
            }
            bulkGet(tail + 8, out.payload, 0, (int) len);
            setTail((int) ((tail + total) & 0xFFFFFFFFL));
            return true;
        }
    }

    private void putInt(int pos, int value) {
        INT_HANDLE.setVolatile(map, pos, value);
    }

    @SuppressWarnings("unused")
    private int getInt(int pos) {
        return (int) INT_HANDLE.getVolatile(map, pos);
    }
}
