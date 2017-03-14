package org.xvm.proto;

/**
 * TODO:
 *
 * @author gg 2017.02.15
 */
public class Frame
    {
    public final ServiceContext f_context;
    public final TypeCompositionTemplate.InvocationTemplate f_function;

    public final ObjectHandle   f_hTarget;      // target
    public final ObjectHandle[] f_ahVars;       // arguments/local vars (index 0 for target:private)
    public final ObjectHandle[] f_ahReturns;    // the return values (index 0 - for exceptions)
    public final int[]          f_anRetTypeId;  // the return types
    public final Frame          f_framePrev;

    public Frame(ServiceContext context, Frame framePrev, ObjectHandle hTarget,
                 TypeCompositionTemplate.InvocationTemplate function, ObjectHandle[] ahVars, ObjectHandle[] ahReturns)
        {
        f_context = context;
        f_framePrev = framePrev;
        f_function = function;
        f_hTarget = hTarget;
        f_anRetTypeId = function.m_anRetTypeId;
        f_ahReturns = ahReturns; // [0] - an exception
        f_ahVars = ahVars; // [0] - target:private for methods
        }

    public boolean execute()
        {
        int[] aiRegister = new int[1];       // current scope at index 0
        int[] anScopeNextVar = new int[128]; // at index i, the first var register for scope i

        anScopeNextVar[0] = f_function.m_cArgs;

        Op[] abOps = f_function.m_aop;

        if (f_hTarget != null)
            {
            f_ahVars[0] = f_hTarget; // TODO: replace with this:private
            anScopeNextVar[0]++; // this
            }

        int iPC = 0;

        do
            {
            Op op = abOps[iPC];

            if (op == null)
                {
                iPC++;
                }
            else
                {
                iPC = op.process(this, iPC, aiRegister, anScopeNextVar);
                }
            }
        while (iPC >= 0);

        switch (iPC)
            {
            default:
            case Op.RETURN_NORMAL:
                return true;

            case Op.RETURN_EXCEPTION:
                return false;
            }
        }
    }
