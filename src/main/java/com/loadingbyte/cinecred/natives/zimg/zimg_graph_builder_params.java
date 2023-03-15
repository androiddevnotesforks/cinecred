// Generated by jextract

package com.loadingbyte.cinecred.natives.zimg;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import jdk.incubator.foreign.*;
import static jdk.incubator.foreign.CLinker.*;
public class zimg_graph_builder_params {

    static final MemoryLayout $struct$LAYOUT = MemoryLayout.structLayout(
        C_INT.withName("version"),
        C_INT.withName("resample_filter"),
        C_DOUBLE.withName("filter_param_a"),
        C_DOUBLE.withName("filter_param_b"),
        C_INT.withName("resample_filter_uv"),
        MemoryLayout.paddingLayout(32),
        C_DOUBLE.withName("filter_param_a_uv"),
        C_DOUBLE.withName("filter_param_b_uv"),
        C_INT.withName("dither_type"),
        C_INT.withName("cpu_type"),
        C_DOUBLE.withName("nominal_peak_luminance"),
        C_CHAR.withName("allow_approximate_gamma"),
        MemoryLayout.paddingLayout(56)
    ).withName("zimg_graph_builder_params");
    public static MemoryLayout $LAYOUT() {
        return zimg_graph_builder_params.$struct$LAYOUT;
    }
    static final VarHandle version$VH = $struct$LAYOUT.varHandle(int.class, MemoryLayout.PathElement.groupElement("version"));
    public static VarHandle version$VH() {
        return zimg_graph_builder_params.version$VH;
    }
    public static int version$get(MemorySegment seg) {
        return (int)zimg_graph_builder_params.version$VH.get(seg);
    }
    public static void version$set( MemorySegment seg, int x) {
        zimg_graph_builder_params.version$VH.set(seg, x);
    }
    public static int version$get(MemorySegment seg, long index) {
        return (int)zimg_graph_builder_params.version$VH.get(seg.asSlice(index*sizeof()));
    }
    public static void version$set(MemorySegment seg, long index, int x) {
        zimg_graph_builder_params.version$VH.set(seg.asSlice(index*sizeof()), x);
    }
    static final VarHandle resample_filter$VH = $struct$LAYOUT.varHandle(int.class, MemoryLayout.PathElement.groupElement("resample_filter"));
    public static VarHandle resample_filter$VH() {
        return zimg_graph_builder_params.resample_filter$VH;
    }
    public static int resample_filter$get(MemorySegment seg) {
        return (int)zimg_graph_builder_params.resample_filter$VH.get(seg);
    }
    public static void resample_filter$set( MemorySegment seg, int x) {
        zimg_graph_builder_params.resample_filter$VH.set(seg, x);
    }
    public static int resample_filter$get(MemorySegment seg, long index) {
        return (int)zimg_graph_builder_params.resample_filter$VH.get(seg.asSlice(index*sizeof()));
    }
    public static void resample_filter$set(MemorySegment seg, long index, int x) {
        zimg_graph_builder_params.resample_filter$VH.set(seg.asSlice(index*sizeof()), x);
    }
    static final VarHandle filter_param_a$VH = $struct$LAYOUT.varHandle(double.class, MemoryLayout.PathElement.groupElement("filter_param_a"));
    public static VarHandle filter_param_a$VH() {
        return zimg_graph_builder_params.filter_param_a$VH;
    }
    public static double filter_param_a$get(MemorySegment seg) {
        return (double)zimg_graph_builder_params.filter_param_a$VH.get(seg);
    }
    public static void filter_param_a$set( MemorySegment seg, double x) {
        zimg_graph_builder_params.filter_param_a$VH.set(seg, x);
    }
    public static double filter_param_a$get(MemorySegment seg, long index) {
        return (double)zimg_graph_builder_params.filter_param_a$VH.get(seg.asSlice(index*sizeof()));
    }
    public static void filter_param_a$set(MemorySegment seg, long index, double x) {
        zimg_graph_builder_params.filter_param_a$VH.set(seg.asSlice(index*sizeof()), x);
    }
    static final VarHandle filter_param_b$VH = $struct$LAYOUT.varHandle(double.class, MemoryLayout.PathElement.groupElement("filter_param_b"));
    public static VarHandle filter_param_b$VH() {
        return zimg_graph_builder_params.filter_param_b$VH;
    }
    public static double filter_param_b$get(MemorySegment seg) {
        return (double)zimg_graph_builder_params.filter_param_b$VH.get(seg);
    }
    public static void filter_param_b$set( MemorySegment seg, double x) {
        zimg_graph_builder_params.filter_param_b$VH.set(seg, x);
    }
    public static double filter_param_b$get(MemorySegment seg, long index) {
        return (double)zimg_graph_builder_params.filter_param_b$VH.get(seg.asSlice(index*sizeof()));
    }
    public static void filter_param_b$set(MemorySegment seg, long index, double x) {
        zimg_graph_builder_params.filter_param_b$VH.set(seg.asSlice(index*sizeof()), x);
    }
    static final VarHandle resample_filter_uv$VH = $struct$LAYOUT.varHandle(int.class, MemoryLayout.PathElement.groupElement("resample_filter_uv"));
    public static VarHandle resample_filter_uv$VH() {
        return zimg_graph_builder_params.resample_filter_uv$VH;
    }
    public static int resample_filter_uv$get(MemorySegment seg) {
        return (int)zimg_graph_builder_params.resample_filter_uv$VH.get(seg);
    }
    public static void resample_filter_uv$set( MemorySegment seg, int x) {
        zimg_graph_builder_params.resample_filter_uv$VH.set(seg, x);
    }
    public static int resample_filter_uv$get(MemorySegment seg, long index) {
        return (int)zimg_graph_builder_params.resample_filter_uv$VH.get(seg.asSlice(index*sizeof()));
    }
    public static void resample_filter_uv$set(MemorySegment seg, long index, int x) {
        zimg_graph_builder_params.resample_filter_uv$VH.set(seg.asSlice(index*sizeof()), x);
    }
    static final VarHandle filter_param_a_uv$VH = $struct$LAYOUT.varHandle(double.class, MemoryLayout.PathElement.groupElement("filter_param_a_uv"));
    public static VarHandle filter_param_a_uv$VH() {
        return zimg_graph_builder_params.filter_param_a_uv$VH;
    }
    public static double filter_param_a_uv$get(MemorySegment seg) {
        return (double)zimg_graph_builder_params.filter_param_a_uv$VH.get(seg);
    }
    public static void filter_param_a_uv$set( MemorySegment seg, double x) {
        zimg_graph_builder_params.filter_param_a_uv$VH.set(seg, x);
    }
    public static double filter_param_a_uv$get(MemorySegment seg, long index) {
        return (double)zimg_graph_builder_params.filter_param_a_uv$VH.get(seg.asSlice(index*sizeof()));
    }
    public static void filter_param_a_uv$set(MemorySegment seg, long index, double x) {
        zimg_graph_builder_params.filter_param_a_uv$VH.set(seg.asSlice(index*sizeof()), x);
    }
    static final VarHandle filter_param_b_uv$VH = $struct$LAYOUT.varHandle(double.class, MemoryLayout.PathElement.groupElement("filter_param_b_uv"));
    public static VarHandle filter_param_b_uv$VH() {
        return zimg_graph_builder_params.filter_param_b_uv$VH;
    }
    public static double filter_param_b_uv$get(MemorySegment seg) {
        return (double)zimg_graph_builder_params.filter_param_b_uv$VH.get(seg);
    }
    public static void filter_param_b_uv$set( MemorySegment seg, double x) {
        zimg_graph_builder_params.filter_param_b_uv$VH.set(seg, x);
    }
    public static double filter_param_b_uv$get(MemorySegment seg, long index) {
        return (double)zimg_graph_builder_params.filter_param_b_uv$VH.get(seg.asSlice(index*sizeof()));
    }
    public static void filter_param_b_uv$set(MemorySegment seg, long index, double x) {
        zimg_graph_builder_params.filter_param_b_uv$VH.set(seg.asSlice(index*sizeof()), x);
    }
    static final VarHandle dither_type$VH = $struct$LAYOUT.varHandle(int.class, MemoryLayout.PathElement.groupElement("dither_type"));
    public static VarHandle dither_type$VH() {
        return zimg_graph_builder_params.dither_type$VH;
    }
    public static int dither_type$get(MemorySegment seg) {
        return (int)zimg_graph_builder_params.dither_type$VH.get(seg);
    }
    public static void dither_type$set( MemorySegment seg, int x) {
        zimg_graph_builder_params.dither_type$VH.set(seg, x);
    }
    public static int dither_type$get(MemorySegment seg, long index) {
        return (int)zimg_graph_builder_params.dither_type$VH.get(seg.asSlice(index*sizeof()));
    }
    public static void dither_type$set(MemorySegment seg, long index, int x) {
        zimg_graph_builder_params.dither_type$VH.set(seg.asSlice(index*sizeof()), x);
    }
    static final VarHandle cpu_type$VH = $struct$LAYOUT.varHandle(int.class, MemoryLayout.PathElement.groupElement("cpu_type"));
    public static VarHandle cpu_type$VH() {
        return zimg_graph_builder_params.cpu_type$VH;
    }
    public static int cpu_type$get(MemorySegment seg) {
        return (int)zimg_graph_builder_params.cpu_type$VH.get(seg);
    }
    public static void cpu_type$set( MemorySegment seg, int x) {
        zimg_graph_builder_params.cpu_type$VH.set(seg, x);
    }
    public static int cpu_type$get(MemorySegment seg, long index) {
        return (int)zimg_graph_builder_params.cpu_type$VH.get(seg.asSlice(index*sizeof()));
    }
    public static void cpu_type$set(MemorySegment seg, long index, int x) {
        zimg_graph_builder_params.cpu_type$VH.set(seg.asSlice(index*sizeof()), x);
    }
    static final VarHandle nominal_peak_luminance$VH = $struct$LAYOUT.varHandle(double.class, MemoryLayout.PathElement.groupElement("nominal_peak_luminance"));
    public static VarHandle nominal_peak_luminance$VH() {
        return zimg_graph_builder_params.nominal_peak_luminance$VH;
    }
    public static double nominal_peak_luminance$get(MemorySegment seg) {
        return (double)zimg_graph_builder_params.nominal_peak_luminance$VH.get(seg);
    }
    public static void nominal_peak_luminance$set( MemorySegment seg, double x) {
        zimg_graph_builder_params.nominal_peak_luminance$VH.set(seg, x);
    }
    public static double nominal_peak_luminance$get(MemorySegment seg, long index) {
        return (double)zimg_graph_builder_params.nominal_peak_luminance$VH.get(seg.asSlice(index*sizeof()));
    }
    public static void nominal_peak_luminance$set(MemorySegment seg, long index, double x) {
        zimg_graph_builder_params.nominal_peak_luminance$VH.set(seg.asSlice(index*sizeof()), x);
    }
    static final VarHandle allow_approximate_gamma$VH = $struct$LAYOUT.varHandle(byte.class, MemoryLayout.PathElement.groupElement("allow_approximate_gamma"));
    public static VarHandle allow_approximate_gamma$VH() {
        return zimg_graph_builder_params.allow_approximate_gamma$VH;
    }
    public static byte allow_approximate_gamma$get(MemorySegment seg) {
        return (byte)zimg_graph_builder_params.allow_approximate_gamma$VH.get(seg);
    }
    public static void allow_approximate_gamma$set( MemorySegment seg, byte x) {
        zimg_graph_builder_params.allow_approximate_gamma$VH.set(seg, x);
    }
    public static byte allow_approximate_gamma$get(MemorySegment seg, long index) {
        return (byte)zimg_graph_builder_params.allow_approximate_gamma$VH.get(seg.asSlice(index*sizeof()));
    }
    public static void allow_approximate_gamma$set(MemorySegment seg, long index, byte x) {
        zimg_graph_builder_params.allow_approximate_gamma$VH.set(seg.asSlice(index*sizeof()), x);
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


