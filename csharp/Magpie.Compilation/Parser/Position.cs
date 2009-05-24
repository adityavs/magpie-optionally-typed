﻿using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace Magpie.Compilation
{
    /// <summary>
    /// A position is a range of consecutive characters in a source file. Used to report where
    /// compile errors occur in the source.
    /// </summary>
    public class Position
    {
        public static Position None { get { return new Position(-1, -1, -1); } }

        public int Line { get; private set; }
        public int Column { get; private set; }
        public int Length { get; private set; }

        public Position(int line, int column, int length)
        {
            Line = line;
            Column = column;
            Length = length;
        }

        public override string ToString()
        {
            return String.Format("line {0} column {1}-{2}", Line, Column, Column + Length);
        }
    }
}
