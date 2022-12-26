/**
 * `Int` is an automatically-sized integer, and supports the [Int128] range of values.
 *
 * When integer bitwise operations are not required, use the `Int` type by default for all integer
 * properties, variables, parameters, and return values whose runtime range is _unknown_, and thus
 * whose ideal storage size is also unknown. Conversely, if bitwise operations (as defined by the
 * [Bitwise] mixin) are required, or if the exact range of a value is known, then use the
 * appropriate sized integer type ([Int8], [Int16], [Int32], [Int64], or [Int128]) or the variably-
 * sized integer type ([IntN]) instead.
 *
 * "Automatically-sized" does not mean "variably sized" at runtime; a variably sized value means
 * that the class for that value decides on an instance-by-instance basis what to use for the
 * storage of the value that the instance represents. "Automatically-sized" means that the _runtime_
 * is responsible for handling all values in the largest possible range, but is permitted to select
 * a far smaller space for the storage of the integer value than the largest possible range would
 * indicate, so long as the runtime can adjust that storage to handle larger values when necessary.
 * An expected implementation for properties, for example, would utilize runtime statistics to
 * determine the actual ranges of values encountered in classes with large numbers of
 * instantiations, and would then reshape the storage layout of those classes, reducing the memory
 * footprint of each instance of those classes; this operation would likely be performed during a
 * service-level garbage collection, or when a service is idle. The reverse is not true, though:
 * When a value is encountered that is larger than the storage size that was previously assumed to
 * be sufficient, then the runtime must immediately provide for the storage of that value in some
 * manner, so that information is not lost. As with all statistics-driven optimizations that require
 * real-time de-optimization to handle unexpected and otherwise-unsupported conditions, there will
 * be significant and unavoidable one-time costs, every time such a scenario is encountered.
 */
const Xnt
        extends IntNumber
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an Xnt.
     */
    static IntLiteral MinValue = Int128.MinValue;

    /**
     * The maximum value for an Xnt.
     */
    static IntLiteral MaxValue = Int128.MaxValue;


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength()
        {
        return False;
        }

    @Override
    static Xnt zero()
        {
        return 0;
        }

    @Override
    static Xnt one()
        {
        return 1;
        }

    @Override
    static conditional Range<Xnt> range()
        {
        return True, MinValue..MaxValue;
        }


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 64-bit signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits)
        {
        assert bits.size <= 128;
        super(bits);
        }

    /**
     * Construct a 64-bit signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes)
        {
        assert bytes.size <= 16;
        super(bytes);
        }

    /**
     * Construct a 64-bit signed integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text)
        {
        construct Xnt(new IntLiteral(text).toInt().bits);
        }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        return switch (this <=> 0)
            {
            case Lesser : Negative;
            case Equal  : Zero;
            case Greater: Positive;
            };
        }

    @Override
    UInt magnitude.get()
        {
        return this.toUInt();
        }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("-#")
    Xnt neg()
        {
        TODO
        }

    @Override
    @Op("+")
    Xnt add(Xnt! n)
        {
        TODO
        }

    @Override
    @Op("-")
    Xnt sub(Xnt! n)
        {
        TODO
        }

    @Override
    @Op("*")
    Xnt mul(Xnt! n)
        {
        TODO
        }

    @Override
    @Op("/")
    Xnt div(Xnt! n)
        {
        TODO
        }

    @Override
    @Op("%")
    Xnt mod(Xnt! n)
        {
        TODO
        }

    @Override
    Xnt abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    Xnt pow(Xnt! n)
        {
        Xnt result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Xnt next()
        {
        if (this < MaxValue)
            {
            return True, this + 1;
            }

        return False;
        }

    @Override
    conditional Xnt prev()
        {
        if (this > MinValue)
            {
            return True, this - 1;
            }

        return False;
        }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    Xnt toInt(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return this;
        }

    @Override
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= Int8.MinValue && this <= Int8.MaxValue;
        return new Int8(bits[bitLength-8 ..< bitLength]);
        }

    @Override
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= Int16.MinValue && this <= Int16.MaxValue;
        return new Int16(bits[bitLength-16 ..< bitLength]);
        }

    @Override
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= Int32.MinValue && this <= Int32.MaxValue;
        return new Int32(bits[bitLength-32 ..< bitLength]);
        }

    @Override
    Int64 toInt64(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= Int64.MinValue && this <= Int64.MaxValue;
        return new Int64(bits[bitLength-64 ..< bitLength]);
        }

    @Auto
    @Override
    Int128 toInt128(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return new Int128(new Bit[128](i -> bits[i < 128-bitLength ? 0 : i]));
        }

    @Auto
    @Override
    IntN toIntN(Rounding direction = TowardZero)
        {
        return new IntN(bits);
        }

    @Override
    UInt toUInt(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return magnitude;
        }

    @Override
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= UInt8.MinValue && this <= UInt8.MaxValue;
        return new UInt8(bits[bitLength-8 ..< bitLength]);
        }

    @Override
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= UInt16.MinValue && this <= UInt16.MaxValue;
        return new UInt16(bits[bitLength-16 ..< bitLength]);
        }

    @Override
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= UInt32.MinValue && this <= UInt32.MaxValue;
        return new UInt32(bits[bitLength-32 ..< bitLength]);
        }

    @Override
    UInt64 toUInt64(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= UInt64.MinValue && this <= UInt64.MaxValue;
        return new UInt64(bits[bitLength-64 ..< bitLength]);
        }

    @Override
    UInt128 toUInt128(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this >= 0;
        return new UInt128(new Bit[128](i -> (i < 128-bitLength ? 0 : bits[i])));
        }

    @Override
    UIntN toUIntN(Rounding direction = TowardZero)
        {
        assert:bounds this >= 0;
        return new UIntN(bits);
        }

    @Auto
    @Override
    BFloat16 toBFloat16();

    @Auto
    @Override
    Float16 toFloat16();

    @Auto
    @Override
    Float32 toFloat32();

    @Auto
    @Override
    Float64 toFloat64();

    @Auto
    @Override
    Float128 toFloat128();

    @Auto
    @Override
    FloatN toFloatN()
        {
        return toIntLiteral().toFloatN();
        }

    @Auto
    @Override
    Dec32 toDec32();

    @Auto
    @Override
    Dec64 toDec64();

    @Auto
    @Override
    Dec128 toDec128();

    @Auto
    @Override
    DecN toDecN()
        {
        return toIntLiteral().toDecN();
        }


    // ----- Hashable functions --------------------------------------------------------------------

    @Override
    static <CompileType extends Xnt> Int64 hashCode(CompileType value)
        {
        return value.bits.hashCode();
        }

    @Override
    static <CompileType extends Xnt> Boolean equals(CompileType value1, CompileType value2)
        {
        return value1.bits == value2.bits;
        }
    }