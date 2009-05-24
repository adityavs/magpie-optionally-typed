﻿using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace Magpie.Compilation
{
    /// <summary>
    /// A pattern matching expression.
    /// </summary>
    public class MatchExpr : IUnboundExpr
    {
        public IUnboundExpr Match { get; private set; }
        public IList<MatchCase> Cases { get; private set; }

        public MatchExpr(Position position, IUnboundExpr match, IList<MatchCase> cases)
        {
            Position = position;
            Match = match;
            Cases = cases;
        }

        #region IUnboundExpr Members

        public Position Position { get; private set; }

        public TReturn Accept<TReturn>(IUnboundExprVisitor<TReturn> visitor)
        {
            return visitor.Visit(this);
        }

        #endregion
    }

    public class MatchCase
    {
        public Position Position { get; private set; }
        public ICaseExpr Case { get; private set; }
        public IUnboundExpr Body { get; private set; }

        public MatchCase(Position position, ICaseExpr caseExpr, IUnboundExpr body)
        {
            Position = position;
            Case = caseExpr;
            Body = body;
        }
    }
}
