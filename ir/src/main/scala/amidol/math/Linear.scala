package amidol.math

import scala.util.parsing.combinator._
import scala.util.parsing.input._
import scala.util._
import scala.collection.mutable.WrappedArray

// Dense representation of linear system
case class LinearSystem(
  variables: WrappedArray[Variable],      // dimension: N
  coefficients: WrappedArray[Array[Expr]] // dimensions: N x N
)

object Linear {

  // Decompose an expression into its immediate terms
  def immediateTerms(e: Expr): Seq[Expr] = e match {
    case Plus(x, y) => immediateTerms(x) ++ immediateTerms(y)
    case factor => Seq(factor)
  }
  
  // Decompose an expression into its immediate factors
  def immediateFactors(e: Expr): Seq[Expr] = e match {
    case Mult(x, y) => immediateFactors(x) ++ immediateFactors(y)
    case atom => Seq(atom)
  }

  // TODO: make this try to normalize the system before giving up
  def fromEquations(equations: Map[Variable, Expr]): Option[LinearSystem] = {
    
    // Checks if the expression contains any variables that are not constants
    def isConstant: Expr => Boolean = {
      case Plus(lhs, rhs) => isConstant(lhs) && isConstant(rhs) 
      case Mult(lhs, rhs) => isConstant(lhs) && isConstant(rhs)
      case Negate(e) => isConstant(e)
      case Inverse(e) => isConstant(e)
      case v: Variable => !equations.contains(v)
      case _: Literal => true
    }

    // Linear equations
    val decomposed: Map[Variable, Map[Variable, Expr]] = 
      equations.mapValues { case expr =>
        val linearTerms: Seq[(Variable, Expr)] = for {
          term <- immediateTerms(expr)
          factors = immediateFactors(term)
          (theVars, theRest) = factors.partition {
            case x if isConstant(x) => false
            case v: Variable => true
            case _ => return None
          }
          theVar = theVars match {
            case List(v: Variable) => v
            case _ => return None
          }
        } yield (theVar, theRest.foldLeft[Expr](1)(Mult(_,_)))

        linearTerms.toMap
      }

    // Pick an order for variables and make the matrix
    val variables: Array[Variable] = equations.keys.toArray
    val dim = variables.length
    val coefficients: Array[Array[Expr]] = Array.tabulate(dim, dim) { (i: Int, j: Int) =>
      decomposed
        .getOrElse(variables(i), Map())
        .getOrElse(variables(j), 0)
   }
  
    Some(LinearSystem(variables, coefficients))
  }

}