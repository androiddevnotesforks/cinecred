// Generated by jextract

package com.loadingbyte.cinecred.natives.harfbuzz;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import jdk.incubator.foreign.*;
import static jdk.incubator.foreign.CLinker.*;
public class hb_glyph_info_t {

    static final MemoryLayout $struct$LAYOUT = MemoryLayout.structLayout(
        C_INT.withName("codepoint"),
        C_INT.withName("mask"),
        C_INT.withName("cluster"),
        MemoryLayout.unionLayout(
            C_INT.withName("u32"),
            C_INT.withName("i32"),
            MemoryLayout.sequenceLayout(2, C_SHORT).withName("u16"),
            MemoryLayout.sequenceLayout(2, C_SHORT).withName("i16"),
            MemoryLayout.sequenceLayout(4, C_CHAR).withName("u8"),
            MemoryLayout.sequenceLayout(4, C_CHAR).withName("i8")
        ).withName("var1"),
        MemoryLayout.unionLayout(
            C_INT.withName("u32"),
            C_INT.withName("i32"),
            MemoryLayout.sequenceLayout(2, C_SHORT).withName("u16"),
            MemoryLayout.sequenceLayout(2, C_SHORT).withName("i16"),
            MemoryLayout.sequenceLayout(4, C_CHAR).withName("u8"),
            MemoryLayout.sequenceLayout(4, C_CHAR).withName("i8")
        ).withName("var2")
    ).withName("hb_glyph_info_t");
    public static MemoryLayout $LAYOUT() {
        return hb_glyph_info_t.$struct$LAYOUT;
    }
    static final VarHandle codepoint$VH = $struct$LAYOUT.varHandle(int.class, MemoryLayout.PathElement.groupElement("codepoint"));
    public static VarHandle codepoint$VH() {
        return hb_glyph_info_t.codepoint$VH;
    }
    public static int codepoint$get(MemorySegment seg) {
        return (int)hb_glyph_info_t.codepoint$VH.get(seg);
    }
    public static void codepoint$set( MemorySegment seg, int x) {
        hb_glyph_info_t.codepoint$VH.set(seg, x);
    }
    public static int codepoint$get(MemorySegment seg, long index) {
        return (int)hb_glyph_info_t.codepoint$VH.get(seg.asSlice(index*sizeof()));
    }
    public static void codepoint$set(MemorySegment seg, long index, int x) {
        hb_glyph_info_t.codepoint$VH.set(seg.asSlice(index*sizeof()), x);
    }
    static final VarHandle mask$VH = $struct$LAYOUT.varHandle(int.class, MemoryLayout.PathElement.groupElement("mask"));
    public static VarHandle mask$VH() {
        return hb_glyph_info_t.mask$VH;
    }
    public static int mask$get(MemorySegment seg) {
        return (int)hb_glyph_info_t.mask$VH.get(seg);
    }
    public static void mask$set( MemorySegment seg, int x) {
        hb_glyph_info_t.mask$VH.set(seg, x);
    }
    public static int mask$get(MemorySegment seg, long index) {
        return (int)hb_glyph_info_t.mask$VH.get(seg.asSlice(index*sizeof()));
    }
    public static void mask$set(MemorySegment seg, long index, int x) {
        hb_glyph_info_t.mask$VH.set(seg.asSlice(index*sizeof()), x);
    }
    static final VarHandle cluster$VH = $struct$LAYOUT.varHandle(int.class, MemoryLayout.PathElement.groupElement("cluster"));
    public static VarHandle cluster$VH() {
        return hb_glyph_info_t.cluster$VH;
    }
    public static int cluster$get(MemorySegment seg) {
        return (int)hb_glyph_info_t.cluster$VH.get(seg);
    }
    public static void cluster$set( MemorySegment seg, int x) {
        hb_glyph_info_t.cluster$VH.set(seg, x);
    }
    public static int cluster$get(MemorySegment seg, long index) {
        return (int)hb_glyph_info_t.cluster$VH.get(seg.asSlice(index*sizeof()));
    }
    public static void cluster$set(MemorySegment seg, long index, int x) {
        hb_glyph_info_t.cluster$VH.set(seg.asSlice(index*sizeof()), x);
    }
    public static MemorySegment var1$slice(MemorySegment seg) {
        return seg.asSlice(12, 4);
    }
    public static MemorySegment var2$slice(MemorySegment seg) {
        return seg.asSlice(16, 4);
    }
    public static long sizeof() { return $LAYOUT().byteSize(); }
    public static MemorySegment allocate(SegmentAllocator allocator) { return allocator.allocate($LAYOUT()); }
    public static MemorySegment allocate(ResourceScope scope) { return allocate(SegmentAllocator.ofScope(scope)); }
    public static MemorySegment allocateArray(int len, SegmentAllocator allocator) {
        return allocator.allocate(MemoryLayout.sequenceLayout(len, $LAYOUT()));
    }
    public static MemorySegment allocateArray(int len, ResourceScope scope) {
        return allocateArray(len, SegmentAllocator.ofScope(scope));
    }
    public static MemorySegment ofAddress(MemoryAddress addr, ResourceScope scope) { return RuntimeHelper.asArray(addr, $LAYOUT(), 1, scope); }
}


