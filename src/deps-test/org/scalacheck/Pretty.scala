/*-------------------------------------------------------------------------*\
**  ScalaCheck                                                             **
**  Copyright (c) 2007-2009 Rickard Nilsson. All rights reserved.          **
**  http://www.scalacheck.org                                              **
**                                                                         **
**  This software is released under the terms of the Revised BSD License.  **
**  There is NO WARRANTY. See the file LICENSE for the full text.          **
\*-------------------------------------------------------------------------*/

package org.scalacheck

import Math.round


sealed trait Pretty {
  def apply(prms: Pretty.Params): String

  def map(f: String => String) = Pretty(prms => f(Pretty.this(prms)))

  def flatMap(f: String => Pretty) = Pretty(prms => f(Pretty.this(prms))(prms))
}

object Pretty {

  case class Params(verbosity: Int)

  def apply(f: Params => String) = new Pretty { def apply(p: Params) = f(p) }

  def pretty[T <% Pretty](t: T, prms: Params): String = t(prms)

  implicit def strBreak(s1: String) = new {
    def /(s2: String) = if(s2 == "") s1 else s1+"\n"+s2
  }

  def pad(s: String, c: Char, length: Int) = 
    if(s.length >= length) s
    else s + List.make(length-s.length, c).mkString

  def break(s: String, lead: String, length: Int): String =
    if(s.length <= length) s
    else s.substring(0, length) / break(lead+s.substring(length), lead, length)

  def format(s: String, lead: String, trail: String, width: Int) =
    s.lines.map(l => break(lead+l+trail, "  ", width)).mkString("\n")

  implicit def prettyAny[T](t: T) = Pretty { p => t.toString }

  implicit def prettyList(l: List[_]) = Pretty { p => 
    l.map("\""+_+"\"").mkString("List(", ", ", ")") 
  }

  implicit def prettyThrowable(e: Throwable) = Pretty { prms =>
    val strs = e.getStackTrace.map { st =>
      import st._
      getClassName+"."+getMethodName + "("+getFileName+":"+getLineNumber+")"
    }
    
    val strs2 = if(prms.verbosity > 0) strs else strs.take(5)
    
    e.getClass.getName / strs2.mkString("\n")
  }

  implicit def prettyArgs(args: List[Arg[_]]) = Pretty { prms =>
    if(args.isEmpty) "" else {
      for((a,i) <- args.zipWithIndex) yield {
        val l = if(a.label == "") "ARG_"+i else a.label
        val s = 
          if(a.shrinks == 0) "" 
          else " (orig arg: "+pretty(a.origArg, prms)(a.prettyPrinter)+")"

        "> "+l+": "+pretty(a.arg, prms)(a.prettyPrinter)+""+s
      }
    }.mkString("\n")
  }

  implicit def prettyFreqMap(fm: Prop.FM) = Pretty { prms =>
    if(fm.total == 0) "" 
    else {
      "> Collected test data: " / {
        for {
          (xs,r) <- fm.getRatios
          ys = xs - ()
          if !ys.isEmpty
        } yield round(r*100)+"% " + ys.mkString(", ")
      }.mkString("\n")
    }
  }

  implicit def prettyTestRes(res: Test.Result) = Pretty { prms =>
    def labels(ls: collection.immutable.Set[String]) = 
      if(ls.isEmpty) "" 
      else "> Labels of failing property: " / ls.mkString("\n")
    val s = res.status match {
      case Test.Proved(args) => "OK, proved property."/pretty(args,prms)
      case Test.Passed => "OK, passed "+res.succeeded+" tests."
      case Test.Failed(args, l) =>
        "Falsified after "+res.succeeded+" passed tests."/labels(l)/pretty(args,prms)
      case Test.Exhausted =>
        "Gave up after only "+res.succeeded+" passed tests. " +
        res.discarded+" tests were discarded."
      case Test.PropException(args,e,l) =>
        "Exception raised on property evaluation."/labels(l)/pretty(args,prms)/
        "> Stack trace: "+pretty(e,prms)
      case Test.GenException(e) =>
        "Exception raised on argument generation."/"> Stack trace: "/pretty(e,prms)
    }
    s/pretty(res.freqMap,prms)
  }

}
