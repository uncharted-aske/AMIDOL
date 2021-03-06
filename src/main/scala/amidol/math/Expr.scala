package amidol.math

import amidol._
import scala.util.parsing.combinator._
import scala.util.parsing.input._
import scala.util._
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport


// TODO parametrize over number space
// TODO debruijn index for variables 9so you know _what_ is closed over)
sealed trait Expr[A] {
  val precedence: Int
  def eval(vals: Map[Symbol, Double] = Map()): A
  def mapVariables(func: Variable => Expr[Double]): Expr[A]

  final def renameVariables(renamer: Renamer[Variable, Variable]): Expr[A] = 
    mapVariables(renamer.getOrInsert(_))

  final def applySubstitution(theta: Map[Variable, Expr[Double]]): Expr[A] =
    mapVariables((v: Variable) => theta.getOrElse(v, v))

  def asVariable: Try[Variable] =
    scala.util.Failure(throw new Exception(s"Expression ${this.prettyPrint()} is not a variable"))

  def asConstant: Try[Literal[A]] =
    scala.util.Failure(throw new Exception(s"Expression ${this.prettyPrint()} is not a constant"))
}
case class Not(e: Expr[Boolean]) extends Expr[Boolean] {
  val precedence: Int = 8
  def eval(vals: Map[Symbol, Double] = Map()) = !e.eval(vals)
  def mapVariables(func: Variable => Expr[Double]) = Not(e.mapVariables(func))
}
case class And(lhs: Expr[Boolean], rhs: Expr[Boolean]) extends Expr[Boolean] {
  val precedence: Int = 4
  def eval(vals: Map[Symbol, Double] = Map()) = lhs.eval(vals) && rhs.eval(vals)
  def mapVariables(func: Variable => Expr[Double]) = And(lhs.mapVariables(func), rhs.mapVariables(func))
}
case class Or(lhs: Expr[Boolean], rhs: Expr[Boolean]) extends Expr[Boolean] {
  val precedence: Int = 3
  def eval(vals: Map[Symbol, Double] = Map()) = lhs.eval(vals) || rhs.eval(vals)
  def mapVariables(func: Variable => Expr[Double]) = Or(lhs.mapVariables(func), rhs.mapVariables(func))
}
case class Comparision(lhs: Expr[Double], op: ComparisionOp, rhs: Expr[Double]) extends Expr[Boolean] {
  val precedence: Int = 5
  def eval(vals: Map[Symbol, Double] = Map()) = op match {
    case GT => lhs.eval(vals) > rhs.eval(vals)
    case EQ => lhs.eval(vals) == rhs.eval(vals)
    case LT => lhs.eval(vals) < rhs.eval(vals)
  }
  def mapVariables(func: Variable => Expr[Double]) = Comparision(lhs.mapVariables(func), op, rhs.mapVariables(func))
}
case class Plus(lhs: Expr[Double], rhs: Expr[Double]) extends Expr[Double] {
  val precedence: Int = 6
  def eval(vals: Map[Symbol, Double] = Map()) = lhs.eval(vals) + rhs.eval(vals)
  def mapVariables(func: Variable => Expr[Double]) = Plus(lhs.mapVariables(func), rhs.mapVariables(func))

}
case class Mult(lhs: Expr[Double], rhs: Expr[Double]) extends Expr[Double] {
  val precedence: Int = 7
  def eval(vals: Map[Symbol, Double] = Map()) = lhs.eval(vals) * rhs.eval(vals)
  def mapVariables(func: Variable => Expr[Double]) = Mult(lhs.mapVariables(func), rhs.mapVariables(func))
}
case class Negate(e: Expr[Double]) extends Expr[Double] {
  val precedence: Int = 8
  def eval(vals: Map[Symbol, Double] = Map()) = -e.eval(vals)
  def mapVariables(func: Variable => Expr[Double]) = Negate(e.mapVariables(func))
}
case class Inverse(e: Expr[Double]) extends Expr[Double] {
  val precedence: Int = 9
  def eval(vals: Map[Symbol, Double] = Map()) = 1.0 / e.eval(vals)
  def mapVariables(func: Variable => Expr[Double]) = Inverse(e.mapVariables(func))
}
case class Variable(s: Symbol) extends Expr[Double] {
  val precedence: Int = Int.MaxValue
  def eval(vals: Map[Symbol, Double] = Map()) = vals.getOrElse(s, throw new Exception(s"Unbound variable $s"))
  def mapVariables(func: Variable => Expr[Double]) = func(this)

  override def asVariable: Try[Variable] = scala.util.Success(this)
}
case class Literal[A](d: A) extends Expr[A] {
  val precedence: Int = Int.MaxValue
  def eval(vals: Map[Symbol, Double] = Map()) = d
  def mapVariables(func: Variable => Expr[Double]) = Literal(d)

  override def asConstant: Try[Literal[A]] = scala.util.Success(this)
}

case class Sin(arg: Expr[Double]) extends Expr[Double] {
  val precedence: Int = 10
  def eval(vals: Map[Symbol, Double] = Map()) = Math.sin(arg.eval(vals))
  def mapVariables(func: Variable => Expr[Double]) = Sin(arg.mapVariables(func))
}
case class Max(lhs: Expr[Double], rhs: Expr[Double]) extends Expr[Double] {
  val precedence: Int = 10
  def eval(vals: Map[Symbol, Double] = Map()) = Math.max(lhs.eval(vals), rhs.eval(vals))
  def mapVariables(func: Variable => Expr[Double]) = Max(lhs.mapVariables(func), rhs.mapVariables(func))
}

case class DataSeries(name: String) extends Expr[Double] {
  val precedence: Int = 10
  def eval(vals: Map[Symbol, Double] = Map()) = 0.0
  def mapVariables(func: Variable => Expr[Double]) = this
}

sealed trait ComparisionOp
case object GT extends ComparisionOp
case object EQ extends ComparisionOp
case object LT extends ComparisionOp

object Expr extends AmidolParser with SprayJsonSupport with DefaultJsonProtocol {

  // Simple arithmetic grammar with a packrat parser (cuz it's fast and I like my left recursion)
  val (predicateParser: PackratParser[Expr[Boolean]], arithmeticParser: PackratParser[Expr[Double]]) = {

    lazy val booleanAtom: PackratParser[Expr[Boolean]] =
      ( ("true" | "TRUE")             ^^ { _ => Literal(true) }
      | ("false" | "FALSE")           ^^ { _ => Literal(false) }
      | "(" ~> disj <~ ")"
      | ("!" | "NOT") ~> booleanAtom  ^^ { b => Not(b) }
      )

    lazy val doubleAtom: PackratParser[Expr[Double]] =
      ( "-" ~> doubleAtom             ^^ { e => Negate(e) }
      | floatingPointNumber           ^^ { s => Literal(s.toDouble)  }
      | "sin" ~> "(" ~> term <~ ")"   ^^ { a => Sin(a) }
      | ("max" ~> "(" ~> term) ~ ("," ~> term <~ ")")   ^^ { case (l ~ r) => Max(l,r) }
      | raw"(?U)\p{L}[\p{L}\p{No}_]*".r     ^^ { v => Variable(Symbol(v))  }
      | raw"`[^`]+`".r                ^^ { v => Variable(Symbol(v.tail.init)) }
      | "(" ~> term <~ ")"
      )

    lazy val factor: PackratParser[Expr[Double]] =
      ( factor ~ "*" ~ doubleAtom     ^^ { case (l ~ _ ~ r) => Mult(l,         r ) }
      | factor ~ "/" ~ doubleAtom     ^^ { case (l ~ _ ~ r) => Mult(l, Inverse(r)) }
      | doubleAtom
      )

    lazy val term: PackratParser[Expr[Double]] =
      ( term ~ "+" ~ factor          ^^ { case (l ~ _ ~ r) => Plus(l,        r ) }
      | term ~ "-" ~ factor          ^^ { case (l ~ _ ~ r) => Plus(l, Negate(r)) }
      | factor
      )

    lazy val comp: PackratParser[Expr[Boolean]] =
      ( booleanAtom
      | term ~ ">"          ~ term   ^^ { case (l ~ _ ~ r) => Comparision(l, GT, r) }
      | term ~ ("==" | "=") ~ term   ^^ { case (l ~ _ ~ r) => Comparision(l, EQ, r) }
      | term ~ "<"          ~ term   ^^ { case (l ~ _ ~ r) => Comparision(l, LT, r) }
      )

    lazy val conj: PackratParser[Expr[Boolean]] =
      ( conj ~ ("&&" | "&" | "AND" | "and") ~ comp ^^ { case (l ~ _ ~ r) => And(l, r) }
      | comp
      )

    lazy val disj: PackratParser[Expr[Boolean]] =
      ( disj ~ ("||" | "|" | "OR" | "or") ~ conj   ^^ { case (l ~ _ ~ r) => Or(l, r) }
      | conj
      )

    (disj, term)
  }

  // Parse an arithmetic expression from a string
  def expression(input: String): Try[Expr[Double]] = runParser(arithmeticParser, input)

  // Parse a predicate expression from a string
  def predicate(input: String): Try[Expr[Boolean]] = runParser(predicateParser, input)

  // Convenience implicits
  implicit def symbol2Variable(sym: Symbol): Variable = Variable(sym)
  implicit def double2Literal(double: Double): Literal[Double] = Literal(double)
  implicit def boolean2Literal(boolean:  Boolean): Literal[Boolean] = Literal(boolean)
}
