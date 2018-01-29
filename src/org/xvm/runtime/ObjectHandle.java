package org.xvm.runtime;


import java.util.Map;

import org.xvm.asm.Constants;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.util.ListMap;


/**
 * Runtime operates on Object handles holding the struct references or the values themselves
 * for the following types:
 *  Bit, Boolean, Char, Int, UInt, Nullable.Null, and optionally for some Tuples
 *
 * Note, that the equals() and hashCode() methods should be only for immutable handles.
 */
public abstract class ObjectHandle
        implements Cloneable
    {
    protected TypeComposition m_clazz;
    protected boolean m_fMutable = false;

    protected ObjectHandle(TypeComposition clazz)
        {
        m_clazz = clazz;
        }

    /**
     * Clone this handle using the specified TypeComposition.
     *
     * @param clazz  the TypeComposition to mask/reveal this handle as
     *
     * @return the new handle
     */
    public ObjectHandle cloneAs(TypeComposition clazz)
        {
        try
            {
            ObjectHandle handle = (ObjectHandle) super.clone();
            handle.m_clazz = clazz;
            return handle;
            }
        catch (CloneNotSupportedException e)
            {
            throw new IllegalStateException();
            }
        }

    /**
     * Reveal this handle using the "inception" type.
     *
     * @return the "fully accessible" handle
     */
    public ObjectHandle revealOrigin()
        {
        return m_clazz.ensureOrigin(this);
        }

    public boolean isMutable()
        {
        return m_fMutable;
        }

    public void makeImmutable()
        {
        m_fMutable = false;
        }

    public boolean isStruct()
        {
        return m_clazz.isStruct();
        }

    public boolean isSelfContained()
        {
        return false;
        }

    /**
     * @return the TypeComposition for this handle
     */
    public TypeComposition getComposition()
        {
        return m_clazz;
        }

    /**
     * @return the OpSupport for the inception type of this handle
     */
    public OpSupport getOpSupport()
        {
        return m_clazz.getSupport();
        }

    /**
     * @return the revealed type of this handle
     */
    public TypeConstant getType()
        {
        return m_clazz.getType();
        }

    public ObjectHandle ensureAccess(Constants.Access access)
        {
        return m_clazz.ensureAccess(this, access);
        }

    @Override
    public int hashCode()
        {
        throw new UnsupportedOperationException(getClass() + " cannot be used as a constant");
        }

    @Override
    public boolean equals(Object obj)
        {
        throw new UnsupportedOperationException(getClass() + " cannot be used as a constant");
        }


    @Override
    public String toString()
        {
        return "(" + m_clazz + ") ";
        }

    public static class GenericHandle
            extends ObjectHandle
        {
        // keyed by the property name
        public Map<String, ObjectHandle> m_mapFields = new ListMap<>();

        public GenericHandle(TypeComposition clazz)
            {
            super(clazz);

            m_fMutable = true;

            clazz.createFields(m_mapFields);
            }

        public ObjectHandle getField(String sName)
            {
            return m_mapFields.get(sName);
            }

        @Override
        public int hashCode()
            {
            return m_mapFields.hashCode();
            }

        @Override
        public boolean equals(Object obj)
            {
            return m_mapFields.equals(((GenericHandle) obj).m_mapFields);
            }
        }

    public static class ExceptionHandle
            extends GenericHandle
        {
        protected WrapperException m_exception;

        public ExceptionHandle(TypeComposition clazz, boolean fInitialize, Throwable eCause)
            {
            super(clazz);

            if (fInitialize)
                {
                m_exception = eCause == null ?
                        new WrapperException() : new WrapperException(eCause);;
                }
            }

        public WrapperException getException()
            {
            return new WrapperException();
            }

        public class WrapperException
                extends Exception
            {
            public WrapperException()
                {
                super();
                }

            public WrapperException(Throwable cause)
                {
                super(cause);
                }

            public ExceptionHandle getExceptionHandle()
                {
                return ExceptionHandle.this;
                }

            @Override
            public String toString()
                {
                return getExceptionHandle().toString();
                }
            }
        }

    // anything that fits in a long
    public static class JavaLong
            extends ObjectHandle
        {
        protected long m_lValue = UNASSIGNED;

        public JavaLong(TypeComposition clazz, long lValue)
            {
            super(clazz);
            m_lValue = lValue;
            }

        @Override
        public boolean isSelfContained()
            {
            return true;
            }

        public long getValue()
            {
            return m_lValue;
            }

        @Override
        public int hashCode()
            {
            return Long.hashCode(m_lValue);
            }

        @Override
        public boolean equals(Object obj)
            {
            return m_lValue == ((JavaLong) obj).m_lValue;
            }

        @Override
        public String toString()
            {
            return super.toString() + m_lValue;
            }
        private final static long UNASSIGNED = 0xBADBAD0BADBADBADl;
        }

    // abstract array handle
    public abstract static class ArrayHandle
            extends ObjectHandle
        {
        public boolean m_fFixed;
        public int m_cSize;

        protected ArrayHandle(TypeComposition clzArray)
            {
            super(clzArray);

            m_fMutable = true;
            }
        }

    // ----- DEFERRED ----

    public static long createHandle(int nTypeId, int nIdentityId, boolean fMutable)
        {
        assert (nTypeId & ~MASK_TYPE) == 0;
        return (((long) nTypeId) & MASK_TYPE) | ((long) nIdentityId << 32 & MASK_IDENTITY) | HANDLE;
        }

    /**
     * @return true iff the specified long value could be used "in place" of the handle
     */
    public static boolean isNaked(long lValue)
        {
        return (lValue & HANDLE) == 0;
        }

    /**
     * @return true iff the specified double value could be used "in place" of the handle
     */
    public static boolean isNaked(double dValue)
        {
        return (Double.doubleToRawLongBits(dValue) & HANDLE) == 0;
        }

    public static int getTypeId(long lHandle)
        {
        assert (lHandle & HANDLE) != 0;
        return (int) (lHandle & MASK_TYPE);
        }

    public static int getIdentityId(long lHandle)
        {
        assert (lHandle & HANDLE) != 0;
        return (int) (lHandle & MASK_IDENTITY >>> 32);
        }

    public static boolean isImmutable(long lHandle)
        {
        assert (lHandle & HANDLE) != 0;
        return (lHandle & STYLE_IMMUTABLE) != 0;
        }

    public static boolean isService(long lHandle)
        {
        assert (lHandle & HANDLE) != 0;
        return (lHandle & STYLE_SERVICE) != 0;
        }

    public static boolean isFunction(long lHandle)
        {
        assert (lHandle & HANDLE) != 0;
        return (lHandle & FUNCTION) != 0;
        }

    public static boolean isGlobal(long lHandle)
        {
        assert (lHandle & HANDLE) != 0;
        return (lHandle & GLOBAL) != 0;
        }

    // zero identity indicates a non-initialized handle
    public static boolean isAssigned(long lHandle)
        {
        return getIdentityId(lHandle) != 0;
        }

    // bits 0-26: type id
    private final static long MASK_TYPE       = 0x07FF_FFFF;

    // bit 26: always set unless the value is a naked Int or Double
    private final static long HANDLE          = 0x0800_0000;

    // bit 27: reserved
    private final static long BIT_27          = 0x1000_0000;

    // bits 28-29 style
    private final static long MASK_STYLE      = 0x3000_0000;
    private final static long STYLE_MUTABLE   = 0x0000_0000;
    private final static long STYLE_IMMUTABLE = 0x1000_0000;
    private final static long STYLE_SERVICE   = 0x2000_0000;
    private final static long STYLE_RESERVED  = 0x3000_0000;

    // bit 30: function
    private final static long FUNCTION        = 0x4000_0000L;

    // bit 31: global - if indicates that the object resides in the global heap; must be immutable
    private final static long GLOBAL = 0x8000_0000L;

    // bits 32-63: identity id
    private final static long MASK_IDENTITY   = 0xFFFF_FFFF_0000_0000L;
    }
