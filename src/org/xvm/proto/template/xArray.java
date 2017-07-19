package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ArrayHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.proto.Op;
import org.xvm.proto.Type;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.ClassTemplate;
import org.xvm.proto.TypeSet;

import org.xvm.proto.template.xFunction.FunctionHandle;

import java.util.Collections;
import java.util.function.Supplier;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xArray
        extends ClassTemplate
        implements IndexSupport
    {
    public static xArray INSTANCE;

    public xArray(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

        if (fInstance)
            {
            INSTANCE = this;

            new xIntArray(f_types, f_struct, true); // TODO: how to do it right?
            }
        }

    @Override
    public void initDeclared()
        {
        // TODO: remove
        f_types.f_adapter.addMethod(f_struct, "construct", new String[]{"Int64", "Function"}, VOID);

        markNativeMethod("construct", new String[]{"Int64", "Function"});
        markNativeMethod("elementAt", INT);
        markNativeMethod("reify", VOID);
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor,
                         TypeComposition clzArray, ObjectHandle[] ahVar, int iReturn)
        {
        Type typeEl = clzArray.f_mapGenericActual.get("ElementType");
        TypeComposition clzEl = typeEl.f_clazz;

        ClassTemplate templateEl = clzEl == null ? xObject.INSTANCE : clzEl.f_template;

        long cCapacity = ((JavaLong) ahVar[0]).getValue();

        int nR = templateEl.createArrayStruct(frame, clzArray, cCapacity, Frame.RET_LOCAL);
        if (nR == Op.R_EXCEPTION)
            {
            return Op.R_EXCEPTION;
            }

        ArrayHandle hArray = (ArrayHandle) frame.getFrameLocal();

        if (cCapacity > 0)
            {
            hArray.m_fFixed = true;

            if (ahVar.length == 2)
                {
                FunctionHandle hSupplier = (FunctionHandle) ahVar[1];
                xArray array = (xArray) hArray.f_clazz.f_template;

                int[] ai = new int[]{0}; // index holder
                ObjectHandle[] ahArg = new ObjectHandle[1];
                ahArg[0] = xInt64.makeHandle(ai[0]);

                // TODO: what if the supplier produces a "future" result
                hSupplier.call1(frame, null, ahArg, Frame.RET_LOCAL);
                Frame frame0 = frame.m_frameNext;

                frame0.m_continuation = new Supplier<Frame>()
                    {
                    public Frame get()
                        {
                        int i = ai[0]++;
                        ExceptionHandle hException =
                                array.assignArrayValue(hArray, i, frame.getFrameLocal());
                        if (hException != null)
                            {
                            frame.m_hException = hException;
                            return null;
                            }

                        if (++i < cCapacity)
                            {
                            ahArg[0] = xInt64.makeHandle(i);
                            // TODO: ditto
                            hSupplier.call1(frame, null, ahArg, Frame.RET_LOCAL);
                            Frame frameNext = frame.m_frameNext;
                            frameNext.m_continuation = this;
                            return frameNext;
                            }

                        frame.assignValue(iReturn, hArray);
                        return null;
                        }
                    };

                return Op.R_CALL;
                }
            }

        return frame.assignValue(iReturn, hArray);
        }

    @Override
    public boolean callEquals(TypeComposition clazz, ObjectHandle hValue1, ObjectHandle hValue2)
        {
        GenericArrayHandle h1 = (GenericArrayHandle) hValue1;
        GenericArrayHandle h2 = (GenericArrayHandle) hValue2;

        ObjectHandle[] ah1 = h1.m_ahValue;
        ObjectHandle[] ah2 = h2.m_ahValue;

        if (ah1.length != ah2.length)
            {
            return false;
            }

        Type type1 = getElementType(h1, 0);
        Type type2 = getElementType(h2, 0);

        TypeComposition clazzEl = type1.f_clazz;
        if (clazzEl == null || clazzEl != type2.f_clazz)
            {
            return false;
            }

        for (int i = 0, c = ah1.length; i < c; i++)
            {
            if (!clazzEl.callEquals(ah1[i], ah2[i]))
                {
                return false;
                }
            }
        return true;
        }

    // ----- IndexSupport methods -----

    @Override
    public ObjectHandle extractArrayValue(ObjectHandle hTarget, long lIndex)
            throws ExceptionHandle.WrapperException
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hArray.m_cSize)
            {
            throw IndexSupport.outOfRange(lIndex, hArray.m_cSize).getException();
            }

        return hArray.m_ahValue[(int) lIndex];
        }

    @Override
    public ExceptionHandle assignArrayValue(ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;

        int cSize = hArray.m_cSize;

        if (lIndex < 0 || lIndex > cSize)
            {
            return IndexSupport.outOfRange(lIndex, cSize);
            }

        if (lIndex == cSize)
            {
            // an array can only grow without any "holes"
            int cCapacity = hArray.m_ahValue.length;
            if (cSize == cCapacity)
                {
                if (hArray.m_fFixed)
                    {
                    return IndexSupport.outOfRange(lIndex, cSize);
                    }

                // resize (TODO: we should be much smarter here)
                cCapacity = cCapacity + Math.max(cCapacity >> 2, 16);

                ObjectHandle[] ahNew = new ObjectHandle[cCapacity];
                System.arraycopy(hArray.m_ahValue, 0, ahNew, 0, cSize);
                hArray.m_ahValue = ahNew;
                }

            hArray.m_cSize++;
            }

        hArray.m_ahValue[(int) lIndex] = hValue;
        return null;
        }

    @Override
    public Type getElementType(ObjectHandle hTarget, long lIndex)
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;

        return hArray.f_clazz.f_mapGenericActual.get("ElementType");
        }

    @Override
    public long size(ObjectHandle hTarget)
        {
        ArrayHandle hArray = (ArrayHandle) hTarget;

        return hArray.m_cSize;
        }

    // ----- ObjectHandle helpers -----

    public static GenericArrayHandle makeHandle(long cCapacity)
        {
        return new GenericArrayHandle(INSTANCE.f_clazzCanonical, cCapacity);
        }

    public static GenericArrayHandle makeHandle(Type typeEl, ObjectHandle[] ahValue)
        {
        return new GenericArrayHandle(INSTANCE.ensureClass(
                Collections.singletonMap("ElementType", typeEl)), ahValue);
        }

    public static GenericArrayHandle makeHandle(TypeComposition clzArray, long cCapacity)
        {
        return new GenericArrayHandle(clzArray, cCapacity);
        }

    // generic array handle
    public static class GenericArrayHandle
            extends ArrayHandle
        {
        public ObjectHandle[] m_ahValue;

        protected GenericArrayHandle(TypeComposition clzArray, ObjectHandle[] ahValue)
            {
            super(clzArray);

            m_ahValue = ahValue;
            }

        protected GenericArrayHandle(TypeComposition clzArray, long cCapacity)
            {
            super(clzArray);

            m_ahValue = new ObjectHandle[(int) cCapacity];
            }

        @Override
        public String toString()
            {
            return super.toString() + (m_fFixed ? "fixed" : "capacity=" + m_ahValue.length)
                    + ", size=" + m_cSize;
            }
        }
    }
