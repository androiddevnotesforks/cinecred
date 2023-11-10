// Generated by jextract

package com.loadingbyte.cinecred.natives.zimg;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.lang.foreign.*;
import static java.lang.foreign.ValueLayout.*;
/**
 * {@snippet :
 * struct zimg_image_format {
 *     unsigned int version;
 *     unsigned int width;
 *     unsigned int height;
 *     zimg_pixel_type_e pixel_type;
 *     unsigned int subsample_w;
 *     unsigned int subsample_h;
 *     zimg_color_family_e color_family;
 *     zimg_matrix_coefficients_e matrix_coefficients;
 *     zimg_transfer_characteristics_e transfer_characteristics;
 *     zimg_color_primaries_e color_primaries;
 *     unsigned int depth;
 *     zimg_pixel_range_e pixel_range;
 *     zimg_field_parity_e field_parity;
 *     zimg_chroma_location_e chroma_location;
 *     struct  active_region;
 *     zimg_alpha_type_e alpha;
 * };
 * }
 */
public class zimg_image_format {

    public static MemoryLayout $LAYOUT() {
        return constants$0.const$26;
    }
    public static VarHandle version$VH() {
        return constants$0.const$27;
    }
    /**
     * Getter for field:
     * {@snippet :
     * unsigned int version;
     * }
     */
    public static int version$get(MemorySegment seg) {
        return (int)constants$0.const$27.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * unsigned int version;
     * }
     */
    public static void version$set(MemorySegment seg, int x) {
        constants$0.const$27.set(seg, x);
    }
    public static int version$get(MemorySegment seg, long index) {
        return (int)constants$0.const$27.get(seg.asSlice(index*sizeof()));
    }
    public static void version$set(MemorySegment seg, long index, int x) {
        constants$0.const$27.set(seg.asSlice(index*sizeof()), x);
    }
    public static VarHandle width$VH() {
        return constants$0.const$28;
    }
    /**
     * Getter for field:
     * {@snippet :
     * unsigned int width;
     * }
     */
    public static int width$get(MemorySegment seg) {
        return (int)constants$0.const$28.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * unsigned int width;
     * }
     */
    public static void width$set(MemorySegment seg, int x) {
        constants$0.const$28.set(seg, x);
    }
    public static int width$get(MemorySegment seg, long index) {
        return (int)constants$0.const$28.get(seg.asSlice(index*sizeof()));
    }
    public static void width$set(MemorySegment seg, long index, int x) {
        constants$0.const$28.set(seg.asSlice(index*sizeof()), x);
    }
    public static VarHandle height$VH() {
        return constants$0.const$29;
    }
    /**
     * Getter for field:
     * {@snippet :
     * unsigned int height;
     * }
     */
    public static int height$get(MemorySegment seg) {
        return (int)constants$0.const$29.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * unsigned int height;
     * }
     */
    public static void height$set(MemorySegment seg, int x) {
        constants$0.const$29.set(seg, x);
    }
    public static int height$get(MemorySegment seg, long index) {
        return (int)constants$0.const$29.get(seg.asSlice(index*sizeof()));
    }
    public static void height$set(MemorySegment seg, long index, int x) {
        constants$0.const$29.set(seg.asSlice(index*sizeof()), x);
    }
    public static VarHandle pixel_type$VH() {
        return constants$0.const$30;
    }
    /**
     * Getter for field:
     * {@snippet :
     * zimg_pixel_type_e pixel_type;
     * }
     */
    public static int pixel_type$get(MemorySegment seg) {
        return (int)constants$0.const$30.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * zimg_pixel_type_e pixel_type;
     * }
     */
    public static void pixel_type$set(MemorySegment seg, int x) {
        constants$0.const$30.set(seg, x);
    }
    public static int pixel_type$get(MemorySegment seg, long index) {
        return (int)constants$0.const$30.get(seg.asSlice(index*sizeof()));
    }
    public static void pixel_type$set(MemorySegment seg, long index, int x) {
        constants$0.const$30.set(seg.asSlice(index*sizeof()), x);
    }
    public static VarHandle subsample_w$VH() {
        return constants$0.const$31;
    }
    /**
     * Getter for field:
     * {@snippet :
     * unsigned int subsample_w;
     * }
     */
    public static int subsample_w$get(MemorySegment seg) {
        return (int)constants$0.const$31.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * unsigned int subsample_w;
     * }
     */
    public static void subsample_w$set(MemorySegment seg, int x) {
        constants$0.const$31.set(seg, x);
    }
    public static int subsample_w$get(MemorySegment seg, long index) {
        return (int)constants$0.const$31.get(seg.asSlice(index*sizeof()));
    }
    public static void subsample_w$set(MemorySegment seg, long index, int x) {
        constants$0.const$31.set(seg.asSlice(index*sizeof()), x);
    }
    public static VarHandle subsample_h$VH() {
        return constants$0.const$32;
    }
    /**
     * Getter for field:
     * {@snippet :
     * unsigned int subsample_h;
     * }
     */
    public static int subsample_h$get(MemorySegment seg) {
        return (int)constants$0.const$32.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * unsigned int subsample_h;
     * }
     */
    public static void subsample_h$set(MemorySegment seg, int x) {
        constants$0.const$32.set(seg, x);
    }
    public static int subsample_h$get(MemorySegment seg, long index) {
        return (int)constants$0.const$32.get(seg.asSlice(index*sizeof()));
    }
    public static void subsample_h$set(MemorySegment seg, long index, int x) {
        constants$0.const$32.set(seg.asSlice(index*sizeof()), x);
    }
    public static VarHandle color_family$VH() {
        return constants$0.const$33;
    }
    /**
     * Getter for field:
     * {@snippet :
     * zimg_color_family_e color_family;
     * }
     */
    public static int color_family$get(MemorySegment seg) {
        return (int)constants$0.const$33.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * zimg_color_family_e color_family;
     * }
     */
    public static void color_family$set(MemorySegment seg, int x) {
        constants$0.const$33.set(seg, x);
    }
    public static int color_family$get(MemorySegment seg, long index) {
        return (int)constants$0.const$33.get(seg.asSlice(index*sizeof()));
    }
    public static void color_family$set(MemorySegment seg, long index, int x) {
        constants$0.const$33.set(seg.asSlice(index*sizeof()), x);
    }
    public static VarHandle matrix_coefficients$VH() {
        return constants$0.const$34;
    }
    /**
     * Getter for field:
     * {@snippet :
     * zimg_matrix_coefficients_e matrix_coefficients;
     * }
     */
    public static int matrix_coefficients$get(MemorySegment seg) {
        return (int)constants$0.const$34.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * zimg_matrix_coefficients_e matrix_coefficients;
     * }
     */
    public static void matrix_coefficients$set(MemorySegment seg, int x) {
        constants$0.const$34.set(seg, x);
    }
    public static int matrix_coefficients$get(MemorySegment seg, long index) {
        return (int)constants$0.const$34.get(seg.asSlice(index*sizeof()));
    }
    public static void matrix_coefficients$set(MemorySegment seg, long index, int x) {
        constants$0.const$34.set(seg.asSlice(index*sizeof()), x);
    }
    public static VarHandle transfer_characteristics$VH() {
        return constants$0.const$35;
    }
    /**
     * Getter for field:
     * {@snippet :
     * zimg_transfer_characteristics_e transfer_characteristics;
     * }
     */
    public static int transfer_characteristics$get(MemorySegment seg) {
        return (int)constants$0.const$35.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * zimg_transfer_characteristics_e transfer_characteristics;
     * }
     */
    public static void transfer_characteristics$set(MemorySegment seg, int x) {
        constants$0.const$35.set(seg, x);
    }
    public static int transfer_characteristics$get(MemorySegment seg, long index) {
        return (int)constants$0.const$35.get(seg.asSlice(index*sizeof()));
    }
    public static void transfer_characteristics$set(MemorySegment seg, long index, int x) {
        constants$0.const$35.set(seg.asSlice(index*sizeof()), x);
    }
    public static VarHandle color_primaries$VH() {
        return constants$0.const$36;
    }
    /**
     * Getter for field:
     * {@snippet :
     * zimg_color_primaries_e color_primaries;
     * }
     */
    public static int color_primaries$get(MemorySegment seg) {
        return (int)constants$0.const$36.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * zimg_color_primaries_e color_primaries;
     * }
     */
    public static void color_primaries$set(MemorySegment seg, int x) {
        constants$0.const$36.set(seg, x);
    }
    public static int color_primaries$get(MemorySegment seg, long index) {
        return (int)constants$0.const$36.get(seg.asSlice(index*sizeof()));
    }
    public static void color_primaries$set(MemorySegment seg, long index, int x) {
        constants$0.const$36.set(seg.asSlice(index*sizeof()), x);
    }
    public static VarHandle depth$VH() {
        return constants$0.const$37;
    }
    /**
     * Getter for field:
     * {@snippet :
     * unsigned int depth;
     * }
     */
    public static int depth$get(MemorySegment seg) {
        return (int)constants$0.const$37.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * unsigned int depth;
     * }
     */
    public static void depth$set(MemorySegment seg, int x) {
        constants$0.const$37.set(seg, x);
    }
    public static int depth$get(MemorySegment seg, long index) {
        return (int)constants$0.const$37.get(seg.asSlice(index*sizeof()));
    }
    public static void depth$set(MemorySegment seg, long index, int x) {
        constants$0.const$37.set(seg.asSlice(index*sizeof()), x);
    }
    public static VarHandle pixel_range$VH() {
        return constants$0.const$38;
    }
    /**
     * Getter for field:
     * {@snippet :
     * zimg_pixel_range_e pixel_range;
     * }
     */
    public static int pixel_range$get(MemorySegment seg) {
        return (int)constants$0.const$38.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * zimg_pixel_range_e pixel_range;
     * }
     */
    public static void pixel_range$set(MemorySegment seg, int x) {
        constants$0.const$38.set(seg, x);
    }
    public static int pixel_range$get(MemorySegment seg, long index) {
        return (int)constants$0.const$38.get(seg.asSlice(index*sizeof()));
    }
    public static void pixel_range$set(MemorySegment seg, long index, int x) {
        constants$0.const$38.set(seg.asSlice(index*sizeof()), x);
    }
    public static VarHandle field_parity$VH() {
        return constants$0.const$39;
    }
    /**
     * Getter for field:
     * {@snippet :
     * zimg_field_parity_e field_parity;
     * }
     */
    public static int field_parity$get(MemorySegment seg) {
        return (int)constants$0.const$39.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * zimg_field_parity_e field_parity;
     * }
     */
    public static void field_parity$set(MemorySegment seg, int x) {
        constants$0.const$39.set(seg, x);
    }
    public static int field_parity$get(MemorySegment seg, long index) {
        return (int)constants$0.const$39.get(seg.asSlice(index*sizeof()));
    }
    public static void field_parity$set(MemorySegment seg, long index, int x) {
        constants$0.const$39.set(seg.asSlice(index*sizeof()), x);
    }
    public static VarHandle chroma_location$VH() {
        return constants$0.const$40;
    }
    /**
     * Getter for field:
     * {@snippet :
     * zimg_chroma_location_e chroma_location;
     * }
     */
    public static int chroma_location$get(MemorySegment seg) {
        return (int)constants$0.const$40.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * zimg_chroma_location_e chroma_location;
     * }
     */
    public static void chroma_location$set(MemorySegment seg, int x) {
        constants$0.const$40.set(seg, x);
    }
    public static int chroma_location$get(MemorySegment seg, long index) {
        return (int)constants$0.const$40.get(seg.asSlice(index*sizeof()));
    }
    public static void chroma_location$set(MemorySegment seg, long index, int x) {
        constants$0.const$40.set(seg.asSlice(index*sizeof()), x);
    }
    /**
     * {@snippet :
     * struct {
     *     double left;
     *     double top;
     *     double width;
     *     double height;
     * };
     * }
     */
    public static final class active_region {

        // Suppresses default constructor, ensuring non-instantiability.
        private active_region() {}
        public static MemoryLayout $LAYOUT() {
            return constants$0.const$41;
        }
        public static VarHandle left$VH() {
            return constants$0.const$42;
        }
        /**
         * Getter for field:
         * {@snippet :
         * double left;
         * }
         */
        public static double left$get(MemorySegment seg) {
            return (double)constants$0.const$42.get(seg);
        }
        /**
         * Setter for field:
         * {@snippet :
         * double left;
         * }
         */
        public static void left$set(MemorySegment seg, double x) {
            constants$0.const$42.set(seg, x);
        }
        public static double left$get(MemorySegment seg, long index) {
            return (double)constants$0.const$42.get(seg.asSlice(index*sizeof()));
        }
        public static void left$set(MemorySegment seg, long index, double x) {
            constants$0.const$42.set(seg.asSlice(index*sizeof()), x);
        }
        public static VarHandle top$VH() {
            return constants$0.const$43;
        }
        /**
         * Getter for field:
         * {@snippet :
         * double top;
         * }
         */
        public static double top$get(MemorySegment seg) {
            return (double)constants$0.const$43.get(seg);
        }
        /**
         * Setter for field:
         * {@snippet :
         * double top;
         * }
         */
        public static void top$set(MemorySegment seg, double x) {
            constants$0.const$43.set(seg, x);
        }
        public static double top$get(MemorySegment seg, long index) {
            return (double)constants$0.const$43.get(seg.asSlice(index*sizeof()));
        }
        public static void top$set(MemorySegment seg, long index, double x) {
            constants$0.const$43.set(seg.asSlice(index*sizeof()), x);
        }
        public static VarHandle width$VH() {
            return constants$0.const$44;
        }
        /**
         * Getter for field:
         * {@snippet :
         * double width;
         * }
         */
        public static double width$get(MemorySegment seg) {
            return (double)constants$0.const$44.get(seg);
        }
        /**
         * Setter for field:
         * {@snippet :
         * double width;
         * }
         */
        public static void width$set(MemorySegment seg, double x) {
            constants$0.const$44.set(seg, x);
        }
        public static double width$get(MemorySegment seg, long index) {
            return (double)constants$0.const$44.get(seg.asSlice(index*sizeof()));
        }
        public static void width$set(MemorySegment seg, long index, double x) {
            constants$0.const$44.set(seg.asSlice(index*sizeof()), x);
        }
        public static VarHandle height$VH() {
            return constants$0.const$45;
        }
        /**
         * Getter for field:
         * {@snippet :
         * double height;
         * }
         */
        public static double height$get(MemorySegment seg) {
            return (double)constants$0.const$45.get(seg);
        }
        /**
         * Setter for field:
         * {@snippet :
         * double height;
         * }
         */
        public static void height$set(MemorySegment seg, double x) {
            constants$0.const$45.set(seg, x);
        }
        public static double height$get(MemorySegment seg, long index) {
            return (double)constants$0.const$45.get(seg.asSlice(index*sizeof()));
        }
        public static void height$set(MemorySegment seg, long index, double x) {
            constants$0.const$45.set(seg.asSlice(index*sizeof()), x);
        }
        public static long sizeof() { return $LAYOUT().byteSize(); }
        public static MemorySegment allocate(SegmentAllocator allocator) { return allocator.allocate($LAYOUT()); }
        public static MemorySegment allocateArray(long len, SegmentAllocator allocator) {
            return allocator.allocate(MemoryLayout.sequenceLayout(len, $LAYOUT()));
        }
        public static MemorySegment ofAddress(MemorySegment addr, Arena arena) { return RuntimeHelper.asArray(addr, $LAYOUT(), 1, arena); }
    }

    public static MemorySegment active_region$slice(MemorySegment seg) {
        return seg.asSlice(56, 32);
    }
    public static VarHandle alpha$VH() {
        return constants$0.const$46;
    }
    /**
     * Getter for field:
     * {@snippet :
     * zimg_alpha_type_e alpha;
     * }
     */
    public static int alpha$get(MemorySegment seg) {
        return (int)constants$0.const$46.get(seg);
    }
    /**
     * Setter for field:
     * {@snippet :
     * zimg_alpha_type_e alpha;
     * }
     */
    public static void alpha$set(MemorySegment seg, int x) {
        constants$0.const$46.set(seg, x);
    }
    public static int alpha$get(MemorySegment seg, long index) {
        return (int)constants$0.const$46.get(seg.asSlice(index*sizeof()));
    }
    public static void alpha$set(MemorySegment seg, long index, int x) {
        constants$0.const$46.set(seg.asSlice(index*sizeof()), x);
    }
    public static long sizeof() { return $LAYOUT().byteSize(); }
    public static MemorySegment allocate(SegmentAllocator allocator) { return allocator.allocate($LAYOUT()); }
    public static MemorySegment allocateArray(long len, SegmentAllocator allocator) {
        return allocator.allocate(MemoryLayout.sequenceLayout(len, $LAYOUT()));
    }
    public static MemorySegment ofAddress(MemorySegment addr, Arena arena) { return RuntimeHelper.asArray(addr, $LAYOUT(), 1, arena); }
}


