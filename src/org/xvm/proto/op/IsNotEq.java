package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;
import org.xvm.proto.TypeComposition;

import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.template.xBoolean;
import org.xvm.proto.template.xBoolean.BooleanHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * IS_NEQ rvalue, rvalue, lvalue-return ; T != T -> Boolean
 *
 * @author gg 2017.03.08
 */
public class IsNotEq extends Op
    {
    private final int f_nValue1;
    private final int f_nValue2;
    private final int f_nRetValue;

    public IsNotEq(int nValue1, int nValue2, int nRet)
        {
        f_nValue1 = nValue1;
        f_nValue2 = nValue2;
        f_nRetValue = nRet;
        }

    public IsNotEq(DataInput in)
            throws IOException
        {
        f_nValue1 = in.readInt();
        f_nValue2 = in.readInt();
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_IS_NEQ);
        out.writeInt(f_nValue1);
        out.writeInt(f_nValue2);
        out.writeInt(f_nRetValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue1 = frame.getArgument(f_nValue1);
            ObjectHandle hValue2 = frame.getArgument(f_nValue2);

            if (hValue1 == null || hValue2 == null)
                {
                return R_REPEAT;
                }

            TypeComposition clz1 = frame.getArgumentClass(f_nValue1);
            TypeComposition clz2 = frame.getArgumentClass(f_nValue2);
            if (clz1 != clz2)
                {
                // this should've not compiled
                throw new IllegalStateException();
                }

            switch (clz1.callEquals(frame, hValue1, hValue2, Frame.RET_LOCAL))
                {
                case R_EXCEPTION:
                    return R_EXCEPTION;

                case R_NEXT:
                    {
                    BooleanHandle hValue = (BooleanHandle) frame.getFrameLocal();
                    return frame.assignValue(f_nRetValue, xBoolean.not(hValue));
                    }

                case R_CALL:
                    frame.m_frameNext.setContinuation(frameCaller ->
                        {
                        BooleanHandle hValue = (BooleanHandle) frameCaller.getFrameLocal();
                        return frame.assignValue(f_nRetValue, xBoolean.not(hValue));
                        });
                    return R_CALL;

                default:
                    throw new IllegalStateException();
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
