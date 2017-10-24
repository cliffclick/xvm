package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpCondJump;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xOrdered;


/**
 * JMP_LT rvalue1, rvalue2, addr ; jump if value1 is less than value2
 */
public class JumpLt
        extends OpCondJump
    {
    /**
     * Construct a JMP_LT op.
     *
     * @param nValue1   the first value to compare
     * @param nValue2   the second value to compare
     * @param nRelAddr  the relative address to jump to
     *
     * @deprecated
     */
    public JumpLt(int nValue1, int nValue2, int nRelAddr)
        {
        super((Argument) null, null, null);

        m_nArg  = nValue1;
        m_nArg2 = nValue2;
        m_ofJmp = nRelAddr;
        }

    /**
     * Construct a JMP_LT op.
     *
     * @param arg1  the first argument to compare
     * @param arg2  the second argument to compare
     * @param op    the op to conditionally jump to
     */
    public JumpLt(Argument arg1, Argument arg2, Op op)
        {
        super(arg1, arg2, op);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpLt(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_LT;
        }

    @Override
    protected boolean isBinaryOp()
        {
        return true;
        }

    @Override
    protected int completeBinaryOp(Frame frame, int iPC, TypeComposition clz,
                                   ObjectHandle hValue1, ObjectHandle hValue2)
        {
        switch (clz.callCompare(frame, hValue1, hValue2, Frame.RET_LOCAL))
            {
            case R_NEXT:
                return frame.getFrameLocal() == xOrdered.LESSER ? iPC + m_ofJmp : iPC + 1;

            case R_CALL:
                frame.m_frameNext.setContinuation(frameCaller ->
                    frameCaller.getFrameLocal() == xOrdered.LESSER ? iPC + m_ofJmp : iPC + 1);
                return R_CALL;

            case R_EXCEPTION:
                return R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }
    }
