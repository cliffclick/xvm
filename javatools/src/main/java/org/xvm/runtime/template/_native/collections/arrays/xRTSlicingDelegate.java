package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;


/**
 * The native RTSlicingDelegate<Object> implementation.
 */
public class xRTSlicingDelegate
        extends xRTDelegate
    {
    public static xRTSlicingDelegate INSTANCE;

    public xRTSlicingDelegate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        }

    @Override
    public ClassTemplate getTemplate(TypeConstant type)
        {
        return this;
        }


    // ----- delegate API --------------------------------------------------------------------------

    @Override
    protected int getPropertyCapacity(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getPropertySize(frame, hTarget, iReturn);
        }

    @Override
    protected int setPropertyCapacity(Frame frame, ObjectHandle hTarget, long nCapacity)
        {
        SliceHandle hSlice = (SliceHandle) hTarget;

        return nCapacity == hSlice.m_cSize
            ? Op.R_NEXT
            : frame.raiseException(xException.readOnly(frame));
        }

    @Override
    protected int getPropertySize(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        SliceHandle hSlice = (SliceHandle) hTarget;

        return frame.assignValue(iReturn, xInt64.makeHandle(hSlice.m_cSize));
        }

    @Override
    protected int invokeInsertElement(Frame frame, ObjectHandle hTarget,
                                      ObjectHandle.JavaLong hIndex, ObjectHandle hValue, int iReturn)
        {
        return frame.raiseException(xException.readOnly(frame));
        }

    @Override
    protected int invokeDeleteElement(Frame frame, ObjectHandle hTarget, ObjectHandle hValue, int iReturn)
        {
        return frame.raiseException(xException.readOnly(frame));
        }

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        SliceHandle    hSlice  = (SliceHandle) hTarget;
        DelegateHandle hSource = hSlice.f_hSource;

        return ((xRTDelegate) hSource.getTemplate()).
                extractArrayValue(frame, hSource, translateIndex(hSlice, lIndex), iReturn);
        }

    @Override
    public int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        SliceHandle    hSlice  = (SliceHandle) hTarget;
        DelegateHandle hSource = hSlice.f_hSource;

        return ((xRTDelegate) hSource.getTemplate()).
                assignArrayValue(frame, hSource, translateIndex(hSlice, lIndex), hValue);
        }

    @Override
    public TypeConstant getElementType(Frame frame, ObjectHandle hTarget, long lIndex)
        {
        SliceHandle hSlice = (SliceHandle) hTarget;

        return hSlice.f_hSource.getType();
        }

    @Override
    public void fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue)
        {
        throw new IllegalStateException();
        }

    @Override
    public DelegateHandle slice(DelegateHandle hTarget, long ofStart, long cSize, boolean fReverse)
        {
        SliceHandle    hSlice  = (SliceHandle) hTarget;
        DelegateHandle hSource = hSlice.f_hSource;

        return ofStart == 0 && cSize == hSlice.m_cSize && !fReverse
                ? hSlice
                : ((xRTDelegate) hSource.getTemplate()).slice(hSource,
                        ofStart + hSlice.f_ofStart, cSize, fReverse ^ hSlice.f_fReverse);
        }

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            int ofStart, int cSize, boolean fReverse)
        {
        SliceHandle    hSlice  = (SliceHandle) hTarget;
        DelegateHandle hSource = hSlice.f_hSource;

        return ((xRTDelegate) hSource.getTemplate()).createCopyImpl(hSource, mutability,
                (int) translateIndex(hSlice, ofStart), cSize, fReverse);
        }

    private static long translateIndex(SliceHandle hSlice, long lIndex)
        {
        return hSlice.f_fReverse
                ? hSlice.f_ofStart + hSlice.m_cSize - 1 - lIndex
                : hSlice.f_ofStart + lIndex;
        }


    // ----- handle --------------------------------------------------------------------------------

    public static SliceHandle makeHandle(DelegateHandle hSource,
                                         long ofStart, long cSize, boolean fReverse)
        {
        return new SliceHandle(INSTANCE.getCanonicalClass(),
                hSource, Mutability.Fixed, (int) ofStart, (int) cSize, fReverse);
        }

    /**
     * Array slice delegate.
     */
    public static class SliceHandle
            extends DelegateHandle
        {
        public final DelegateHandle f_hSource;
        public final int            f_ofStart;
        public final boolean        f_fReverse;

        protected SliceHandle(TypeComposition clazz, DelegateHandle hSource,
                              Mutability mutability, int ofStart, int cSize, boolean fReverse)
            {
            super(clazz, mutability);

            f_hSource  = hSource;
            f_ofStart  = ofStart;
            f_fReverse = fReverse;
            m_cSize    = cSize;
            }

        @Override
        public String toString()
            {
            return super.toString() + " @" + f_ofStart;
            }
        }
    }