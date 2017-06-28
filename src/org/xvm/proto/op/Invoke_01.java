package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.ConstantPoolAdapter;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.Utils;

import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xService.ServiceHandle;

/**
 * INVOKE_01 rvalue-target, rvalue-method, lvalue-return
 *
 * @author gg 2017.03.08
 */
public class Invoke_01 extends OpInvocable
    {
    private final int f_nTargetValue;
    private final int f_nMethodId;
    private final int f_nRetValue;

    public Invoke_01(int nTarget, int nMethodId, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nMethodId = nMethodId;
        f_nRetValue = nRet;
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

            ClassTemplate template = hTarget.f_clazz.f_template;

            MethodStructure method = getMethodStructure(frame, template, f_nMethodId);

            if (ConstantPoolAdapter.isNative(method))
                {
                return template.invokeNative(frame, hTarget, method,
                        Utils.OBJECTS_NONE, f_nRetValue);
                }

            if (template.isService() && frame.f_context != ((ServiceHandle) hTarget).m_context)
                {
                return xFunction.makeAsyncHandle(method).
                        call1(frame, new ObjectHandle[]{hTarget}, f_nRetValue);
                }

            ObjectHandle[] ahVar = new ObjectHandle[ConstantPoolAdapter.getVarCount(method)];

            return frame.call1(method, hTarget, ahVar, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
