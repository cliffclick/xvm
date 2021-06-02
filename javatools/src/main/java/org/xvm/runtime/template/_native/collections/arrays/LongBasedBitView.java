package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xBit;

import org.xvm.runtime.template._native.collections.arrays.LongBasedDelegate.LongArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate.SliceHandle;


/**
 * A base class for native ArrayDelegate<Bit> views that point to delegates holding long arrays.
 */
public abstract class LongBasedBitView
        extends xRTViewToBit
        implements BitView
    {
    public static LongBasedBitView INSTANCE;

    public LongBasedBitView(TemplateRegistry templates, ClassStructure structure,
                            int nBitsPerValue)
        {
        super(templates, structure, false);

        f_nBitsPerValue = nBitsPerValue;
        }

    @Override
    public void initNative()
        {
        }

    @Override
    public DelegateHandle createBitViewDelegate(DelegateHandle hSource, Mutability mutability)
        {
        if (hSource instanceof SliceHandle)
            {
            // ints.slice().asBitArray() -> ints.asBitArray().slice()
            SliceHandle     hSlice = (SliceHandle) hSource;
            LongArrayHandle hLong  = (LongArrayHandle) hSlice.f_hSource;
            ViewHandle      hView  = new ViewHandle(getCanonicalClass(),
                                            hLong, hLong.m_cSize*f_nBitsPerValue, mutability);
            return slice(hView, hSlice.f_ofStart*f_nBitsPerValue, hSlice.m_cSize*f_nBitsPerValue, false);
            }
        return new ViewHandle(getCanonicalClass(),
                (LongArrayHandle) hSource, hSource.m_cSize*f_nBitsPerValue, mutability);
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse)
        {
        ViewHandle hView = (ViewHandle) hTarget;

        byte[] abBits = getBits(hView, ofStart, cSize, fReverse);

        return xRTBitDelegate.INSTANCE.makeHandle(abBits, cSize, mutability);
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        ViewHandle hView = (ViewHandle) hTarget;

        return frame.assignValue(iReturn, xBit.makeHandle(
                LongBasedDelegate.getBit(hView.f_hSource.m_alValue, lIndex)));
        }

    @Override
    public int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                    ObjectHandle hValue)
        {
        ViewHandle hView = (ViewHandle) hTarget;

        LongBasedDelegate.setBit(hView.f_hSource.m_alValue, lIndex, ((JavaLong) hValue).getValue() != 0);
        return Op.R_NEXT;
        }


    // ----- BitView implementation ----------------------------------------------------------------

    @Override
    public byte[] getBits(DelegateHandle hDelegate, long ofStart, long cBits, boolean fReverse)
        {
        ViewHandle hView = (ViewHandle) hDelegate;

        byte[] abBits = LongBasedDelegate.extractBits(hView.f_hSource.m_alValue, ofStart, cBits);
        if (fReverse)
            {
            abBits = BitBasedDelegate.reverseBits(abBits, cBits);
            }
        return abBits;
        }

    @Override
    public boolean extractBit(DelegateHandle hDelegate, long of)
        {
        ViewHandle hView = (ViewHandle) hDelegate;

        return LongBasedDelegate.getBit(hView.f_hSource.m_alValue, of);
        }

    @Override
    public void assignBit(DelegateHandle hDelegate, long of, boolean fBit)
        {
        ViewHandle hView = (ViewHandle) hDelegate;

        LongBasedDelegate.setBit(hView.f_hSource.m_alValue, of, fBit);
        }


    // ----- ByteView implementation ---------------------------------------------------------------

    @Override
    public byte[] getBytes(DelegateHandle hDelegate, long ofStart, long cBytes, boolean fReverse)
        {
        return getBits(hDelegate, ofStart*8, cBytes*8, fReverse);
        }

    @Override
    public byte extractByte(DelegateHandle hDelegate, long of)
        {
        ViewHandle hView = (ViewHandle) hDelegate;

        return LongBasedDelegate.getByte(hView.f_hSource.m_alValue, of);
        }

    @Override
    public void assignByte(DelegateHandle hDelegate, long of, byte bValue)
        {
        ViewHandle hView = (ViewHandle) hDelegate;

        LongBasedDelegate.setByte(hView.f_hSource.m_alValue, of, bValue);
        }


    // ----- handle --------------------------------------------------------------------------------

    /**
     * DelegateArray<Bit> view delegate.
     */
    protected static class ViewHandle
            extends DelegateHandle
        {
        protected final LongArrayHandle f_hSource;

        protected ViewHandle(TypeComposition clazz, LongArrayHandle hSource, long cSize,
                             Mutability mutability)
            {
            super(clazz, mutability);

            f_hSource = hSource;
            m_cSize   = cSize;
            }
        }


    // ----- constants -----------------------------------------------------------------------------

    final int f_nBitsPerValue;
    }