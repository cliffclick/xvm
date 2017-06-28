package org.xvm.proto.op;

import org.xvm.asm.constants.CharStringConstant;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;
import org.xvm.proto.ServiceContext;
import org.xvm.proto.TypeComposition;

/**
 * NVAR CONST_CLASS, CONST_STRING ; (next register is an uninitialized named variable)
 *
 * @author gg 2017.03.08
 */
public class NVar extends Op
    {
    private final int f_nClassConstId;
    private final int f_nNameConstId;

    public NVar(int nClassConstId, int nNameConstId)
        {
        f_nClassConstId = nClassConstId;
        f_nNameConstId = nNameConstId;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ServiceContext context = frame.f_context;

        TypeComposition clazz = context.f_types.ensureComposition(frame, f_nClassConstId);
        CharStringConstant constName = (CharStringConstant)
                context.f_pool.getConstant(f_nNameConstId);

        frame.introduceVar(clazz, constName.getValue(), Frame.VAR_STANDARD, null);

        return iPC + 1;
        }
    }
