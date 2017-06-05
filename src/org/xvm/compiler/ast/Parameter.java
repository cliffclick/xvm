package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * A parameter type and name, with an optional default value.
 *
 * @author cp 2017.03.28
 */
public class Parameter
        extends AstNode
    {
    // ----- constructors --------------------------------------------------------------------------

    public Parameter(TypeExpression type, Token name)
        {
        this (type, name, null);
        }

    public Parameter(TypeExpression type, Token name, Expression value)
        {
        this.type  = type;
        this.name  = name;
        this.value = value;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public TypeExpression getType()
        {
        return type;
        }

    public String getName()
        {
        return (String) name.getValue();
        }

    @Override
    public long getEndPosition()
        {
        return type.getStartPosition();
        }

    @Override
    public long getStartPosition()
        {
        return value == null ? name.getEndPosition() : value.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(type)
          .append(' ')
          .append(name.getValue());

        if (value != null)
            {
            sb.append(" = ")
              .append(value);
            }

        return sb.toString();
        }

    public String toTypeParamString()
        {
        String s = String.valueOf(name.getValue());
        return type == null ? s : (s + " extends " + type);
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression type;
    protected Token name;
    protected Expression value;

    private static final Field[] CHILD_FIELDS = fieldsForNames(Parameter.class, "type", "value");
    }
