// Generated by jextract

package com.loadingbyte.cinecred.natives.skcms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.lang.foreign.*;
import static java.lang.foreign.ValueLayout.*;
/**
 * {@snippet :
 * struct __locale_struct {
 *     struct __locale_data* __locales[13];
 *     unsigned short* __ctype_b;
 *     int* __ctype_tolower;
 *     int* __ctype_toupper;
 *     char* __names[13];
 * };
 * }
 */
public class __locale_struct {

    public static MemoryLayout $LAYOUT() {
        return constants$0.const$25;
    }
    public static MemorySegment __locales$slice(MemorySegment seg) {
        return seg.asSlice(0, 104);
    }
    public static VarHandle __ctype_b$VH() {
        return constants$0.const$26;
    }
    /**
     * Getter for field:
     * {@snippet :
     * unsigned short* __ctype_b;
     * }
     */
    public static MemorySegment __ctype_b$get(MemorySegment seg) {
        return (java.lang.foreign.MemorySegment)constants$0.const$26.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * unsigned short* __ctype_b;
     * }
     */
    public static void __ctype_b$set(MemorySegment seg, MemorySegment x) {
        constants$0.const$26.set(seg, x);
    }
    public static MemorySegment __ctype_b$get(MemorySegment seg, long index) {
        return (java.lang.foreign.MemorySegment)constants$0.const$26.get(seg.asSlice(index*sizeof()));
    }
    public static void __ctype_b$set(MemorySegment seg, long index, MemorySegment x) {
        constants$0.const$26.set(seg.asSlice(index*sizeof()), x);
    }
    public static VarHandle __ctype_tolower$VH() {
        return constants$0.const$27;
    }
    /**
     * Getter for field:
     * {@snippet :
     * int* __ctype_tolower;
     * }
     */
    public static MemorySegment __ctype_tolower$get(MemorySegment seg) {
        return (java.lang.foreign.MemorySegment)constants$0.const$27.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * int* __ctype_tolower;
     * }
     */
    public static void __ctype_tolower$set(MemorySegment seg, MemorySegment x) {
        constants$0.const$27.set(seg, x);
    }
    public static MemorySegment __ctype_tolower$get(MemorySegment seg, long index) {
        return (java.lang.foreign.MemorySegment)constants$0.const$27.get(seg.asSlice(index*sizeof()));
    }
    public static void __ctype_tolower$set(MemorySegment seg, long index, MemorySegment x) {
        constants$0.const$27.set(seg.asSlice(index*sizeof()), x);
    }
    public static VarHandle __ctype_toupper$VH() {
        return constants$0.const$28;
    }
    /**
     * Getter for field:
     * {@snippet :
     * int* __ctype_toupper;
     * }
     */
    public static MemorySegment __ctype_toupper$get(MemorySegment seg) {
        return (java.lang.foreign.MemorySegment)constants$0.const$28.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * int* __ctype_toupper;
     * }
     */
    public static void __ctype_toupper$set(MemorySegment seg, MemorySegment x) {
        constants$0.const$28.set(seg, x);
    }
    public static MemorySegment __ctype_toupper$get(MemorySegment seg, long index) {
        return (java.lang.foreign.MemorySegment)constants$0.const$28.get(seg.asSlice(index*sizeof()));
    }
    public static void __ctype_toupper$set(MemorySegment seg, long index, MemorySegment x) {
        constants$0.const$28.set(seg.asSlice(index*sizeof()), x);
    }
    public static MemorySegment __names$slice(MemorySegment seg) {
        return seg.asSlice(128, 104);
    }
    public static long sizeof() { return $LAYOUT().byteSize(); }
    public static MemorySegment allocate(SegmentAllocator allocator) { return allocator.allocate($LAYOUT()); }
    public static MemorySegment allocateArray(long len, SegmentAllocator allocator) {
        return allocator.allocate(MemoryLayout.sequenceLayout(len, $LAYOUT()));
    }
    public static MemorySegment ofAddress(MemorySegment addr, Arena arena) { return RuntimeHelper.asArray(addr, $LAYOUT(), 1, arena); }
}


