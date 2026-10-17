package us.drullk.graphmachine.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.jspecify.annotations.Nullable;

/**
 * Two sets of GPU buffers for the graph: the front set is drawn, the back set receives the next
 * {@link GraphView} a slice at a time, then the sets swap.
 *
 * <p>Each set holds one vertex buffer per {@link Slot} and one indirect buffer with four
 * {@code DrawIndirectCommand}s (vertex count, instance count, first vertex, first instance; 16 bytes
 * each) in slot order. Buffers grow by doubling and are never shrunk.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-10-16_23-43_3d80b42f-de61-4c60-b018-5e44e36d8860.md}.
 */
public final class GraphGpuBuffers implements AutoCloseable {
    /** Bytes of one indirect draw command. */
    public static final int COMMAND_BYTES = 16;
    private static final long MIN_CAPACITY = 1024;

    /** The four instance streams, in indirect command order. */
    public enum Slot {
        NODES(GraphPipelines.NODE_VERTICES),
        NAMES(GraphPipelines.QUAD_VERTICES),
        PACKAGES(GraphPipelines.QUAD_VERTICES),
        EDGES(GraphPipelines.QUAD_VERTICES);

        public final int verticesPerInstance;

        Slot(int verticesPerInstance) {
            this.verticesPerInstance = verticesPerInstance;
        }
    }

    private static final Slot[] SLOTS = Slot.values();

    private static final class BufferSet implements AutoCloseable {
        final @Nullable GpuBuffer[] buffers = new GpuBuffer[SLOTS.length];
        final long[] used = new long[SLOTS.length];
        final int[] counts = new int[SLOTS.length];
        final GpuBuffer indirect;
        @Nullable GraphView view;

        BufferSet(GpuDevice device, String name) {
            this.indirect = device.createBuffer(() -> "Graph indirect " + name, GpuBuffer.USAGE_INDIRECT_PARAMETERS | GpuBuffer.USAGE_COPY_DST, (long) COMMAND_BYTES * SLOTS.length);
        }

        GpuBuffer ensure(GpuDevice device, Slot slot, long bytes) {
            GpuBuffer buffer = this.buffers[slot.ordinal()];
            if (buffer == null || buffer.size() < bytes) {
                long capacity = Math.max(MIN_CAPACITY, buffer == null ? bytes : Math.max(bytes, buffer.size() * 2));
                if (buffer != null) {
                    buffer.close();
                }
                buffer = device.createBuffer(() -> "Graph " + slot.name().toLowerCase(java.util.Locale.ROOT), GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, capacity);
                this.buffers[slot.ordinal()] = buffer;
            }
            return buffer;
        }

        @Override
        public void close() {
            for (GpuBuffer buffer : this.buffers) {
                if (buffer != null) {
                    buffer.close();
                }
            }
            this.indirect.close();
        }
    }

    private BufferSet front;
    private BufferSet back;
    private @Nullable GraphView pending;
    private int pendingSlot;
    private long pendingOffset;
    private long pendingDone;

    public GraphGpuBuffers() {
        GpuDevice device = RenderSystem.getDevice();
        this.front = new BufferSet(device, "A");
        this.back = new BufferSet(device, "B");
    }

    /** The view the front set currently draws, or null before the first swap. */
    public @Nullable GraphView frontView() {
        return this.front.view;
    }

    /** True while a view waits to be uploaded. */
    public boolean uploading() {
        return this.pending != null;
    }

    /** Fraction of the pending upload that is done, 0 to 1. */
    public float progress() {
        GraphView view = this.pending;
        if (view == null) {
            return 1.0F;
        }
        long total = view.uploadBytes();
        return total == 0 ? 1.0F : (float) ((double) this.pendingDone / total);
    }

    /** Starts uploading {@code view} into the back set; replaces an upload in progress. */
    public void beginUpload(GraphView view) {
        this.pending = view;
        this.pendingSlot = 0;
        this.pendingOffset = 0;
        this.pendingDone = 0;
        GpuDevice device = RenderSystem.getDevice();
        for (Slot slot : SLOTS) {
            ByteBuffer data = data(view, slot);
            this.back.used[slot.ordinal()] = data.remaining();
            this.back.counts[slot.ordinal()] = count(view, slot);
            if (data.remaining() > 0) {
                this.back.ensure(device, slot, data.remaining());
            }
        }
    }

    /**
     * Uploads up to {@code budgetBytes} more of the pending view. Returns true when the upload finished
     * this call and the sets swapped.
     */
    public boolean uploadSome(long budgetBytes) {
        GraphView view = this.pending;
        if (view == null) {
            return false;
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        long remainingBudget = Math.max(budgetBytes, 1);
        while (this.pendingSlot < SLOTS.length) {
            Slot slot = SLOTS[this.pendingSlot];
            ByteBuffer data = data(view, slot);
            long left = data.remaining() - this.pendingOffset;
            if (left <= 0) {
                this.pendingSlot++;
                this.pendingOffset = 0;
                continue;
            }
            if (remainingBudget <= 0) {
                return false;
            }
            int chunk = (int) Math.min(left, remainingBudget);
            GpuBuffer target = this.back.ensure(RenderSystem.getDevice(), slot, data.remaining());
            ByteBuffer piece = data.slice((int) this.pendingOffset, chunk).order(ByteOrder.nativeOrder());
            encoder.writeToBuffer(target.slice(this.pendingOffset, chunk), piece);
            this.pendingOffset += chunk;
            this.pendingDone += chunk;
            remainingBudget -= chunk;
        }

        ByteBuffer commands = ByteBuffer.allocateDirect(COMMAND_BYTES * SLOTS.length).order(ByteOrder.nativeOrder());
        for (Slot slot : SLOTS) {
            commands.putInt(slot.verticesPerInstance).putInt(this.back.counts[slot.ordinal()]).putInt(0).putInt(0);
        }
        encoder.writeToBuffer(this.back.indirect.slice(), commands.flip());
        this.back.view = view;

        BufferSet swapped = this.front;
        this.front = this.back;
        this.back = swapped;
        this.pending = null;
        return true;
    }

    /** Number of instances of a slot in the front set. */
    public int count(Slot slot) {
        return this.front.counts[slot.ordinal()];
    }

    /** The front vertex data of a slot, or null when the slot is empty. */
    public @Nullable GpuBufferSlice vertices(Slot slot) {
        GpuBuffer buffer = this.front.buffers[slot.ordinal()];
        long used = this.front.used[slot.ordinal()];
        return buffer == null || used == 0 ? null : buffer.slice(0, used);
    }

    /** A sub-range of a slot's front vertex data, in instances. */
    public @Nullable GpuBufferSlice vertices(Slot slot, int firstInstance, int instanceCount, int stride) {
        GpuBuffer buffer = this.front.buffers[slot.ordinal()];
        return buffer == null || instanceCount <= 0 ? null : buffer.slice((long) firstInstance * stride, (long) instanceCount * stride);
    }

    /** The front indirect command of a slot. */
    public GpuBufferSlice command(Slot slot) {
        return this.front.indirect.slice((long) slot.ordinal() * COMMAND_BYTES, COMMAND_BYTES);
    }

    private static ByteBuffer data(GraphView view, Slot slot) {
        return switch (slot) {
            case NODES -> view.nodes();
            case NAMES -> view.nameGlyphs();
            case PACKAGES -> view.packageGlyphs();
            case EDGES -> view.edges();
        };
    }

    private static int count(GraphView view, Slot slot) {
        return switch (slot) {
            case NODES -> view.nodeCount();
            case NAMES -> view.nameGlyphCount();
            case PACKAGES -> view.packageGlyphCount();
            case EDGES -> view.edgeCount();
        };
    }

    @Override
    public void close() {
        this.front.close();
        this.back.close();
        this.pending = null;
    }
}
