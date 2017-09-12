package org.xvm.proto.op;

import org.xvm.proto.CallChain;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.template.Function;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * MBIND rvalue-target, CONST-METHOD, lvalue-fn-result
 *
 * @author gg 2017.03.08
 */
public class MBind extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int f_nResultValue;

    public MBind(int nTarget, int nMethodId, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
        f_nResultValue = nRet;
        }

    public MBind(DataInput in)
            throws IOException
        {
        f_nTargetValue = in.readInt();
        f_nMethodId = in.readInt();
        f_nResultValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_MBIND);
        out.writeInt(f_nTargetValue);
        out.writeInt(f_nMethodId);
        out.writeInt(f_nResultValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTargetValue);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            TypeComposition clz = hTarget.f_clazz;
            CallChain chain = getCallChain(frame, clz, f_nMethodId);

            return frame.assignValue(f_nResultValue, clz.f_template.isService() ?
                    Function.makeAsyncHandle(chain, 0).bindTarget(hTarget) :
                    Function.makeHandle(chain, 0).bindTarget(hTarget));
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
