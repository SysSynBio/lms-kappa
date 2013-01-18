package kappa

import math.{Equiv,Ordering,PartialOrdering}

trait LanguageContext {
  // State types
  type AgentState
  type SiteState
  type LinkState

  // Partial orders over state types
  //
  // RHZ: Perhaps it'd be cleaner to define a lteq method in Agent,
  // Site, and Link?
  //
  // sstucki: There already is such a method, it's called `matches`
  // ;-) But the truth is that this class is a mess and the cleanest
  // way to implement the partial orders might be to introduce a
  // proper State trait that contains the comparison function and then
  // use it as a super-type for states, e.g.
  //
  //   type AgentState <: State
  //
  // and let concrete types extend it, e.g.
  //
  //   class AgentStateImpl extends State ...
  //
  // We will seldom be able to use arbitrary types as state types
  // anyway, since we will want to override the toString method, and
  // hence we might as well wrap the state types into a State.
  // Another advantage of this is that the State trait could contain a
  // method to check whether a given state is valid in a mixture
  // (which is missing now) so we can throw an error when one tries to
  // create a mixture from a pattern with e.g. undefined state.  I'm
  // working on this State trait already, and you would be surprised
  // with the sort of problem this simple task brings to light :-/ so
  // stay tuned...
  def apo: PartialOrdering[AgentState]
  def spo: PartialOrdering[SiteState]
  def lpo: PartialOrdering[LinkState]

  // FIXME: should these helper functions go somewhere else?
  def equalsPartialOrdering[T]: PartialOrdering[T] = new PartialOrdering[T] {
    def lteq(x: T, y: T) = (x == y)
    def tryCompare(x: T, y: T) = if (x == y) Some(0) else None
  }

  def partialOrderingFromEquiv[T](e: Equiv[T]): PartialOrdering[T] =
    new PartialOrdering[T] {
      def lteq(x: T, y: T) = e.equiv(x, y)
      def tryCompare(x: T, y: T) = if (lteq(x, y)) Some(0) else None
    }

  def optionPartialOrdering[T](
    po: PartialOrdering[T]): PartialOrdering[Option[T]] =
      new PartialOrdering[Option[T]] {
        def lteq(x: Option[T], y: Option[T]) = (x, y) match {
          case (_, None) => true
          case (None, Some(_)) => false
          case (Some(x), Some(y)) => po.lteq(x, y)
        }
        def tryCompare(x: Option[T], y: Option[T]) = (x, y) match {
          case (None, None) => Some(0)
          case (None, Some(_)) => Some(1)
          case (Some(_), None) => Some(-1)
          case (Some(x), Some(y)) => po.tryCompare(x, y)
        }
      }

  def pairPartialOrdering[T, U](
    po1: PartialOrdering[T],
    po2: PartialOrdering[U]): PartialOrdering[Pair[T, U]] =
      new PartialOrdering[Pair[T, U]] {
        def lteq(x: Pair[T, U], y: Pair[T, U]) =
          po1.lteq(x._1, y._1) && po2.lteq(x._2, y._2)
        def tryCompare(x: Pair[T, U], y: Pair[T, U]) =
          (po1.tryCompare(x._1, y._1), po2.tryCompare(x._2, y._2)) match {
            case (Some(x), Some(y)) if x == y => Some(x)
            case _ => None
          }
      }

  def refinePartialOrdering[U, T <: U](
    po: PartialOrdering[U]): PartialOrdering[T] =
    new PartialOrdering[T] {
      def lteq(x: T, y: T) = po.lteq(x, y)
      def tryCompare(x: T, y: T) = po.tryCompare(x, y)
    }
}

trait KappaContext extends LanguageContext {
  this: KappaSymbols =>

  type AgentState = AgentStateImpl
  type SiteState  = SiteStateImpl
  type LinkState  = LinkStateImpl

  // TODO: If we were to use dedicated symbol classes rather than Ints
  // to represent symbols, some of these wrappers would likely not be
  // necessary as we could use the symbols directly to represent the
  // states (in cases where the states are not tuples).
  case class AgentStateImpl(atype: AgentTypeSym) {
    override def toString = agentTypes(atype)
  }
  
  // FIXME: Adjust to new syntax?
  case class SiteStateImpl(name: SiteNameSym, state: Option[SiteStateNameSym]) {
    override def toString = siteNames(name) + (state match {
      case None => ""
      case Some(s) => "~" + siteStateNames(s)
    })
  }

  case class LinkStateImpl(state: Option[LinkStateNameSym]) {
    override def toString = state match {
      case None => ""
      case Some(s) => ": " + linkStateNames(s)
    }
  }

  val apo = equalsPartialOrdering[AgentState]
  val spo = new PartialOrdering[SiteState] {
    val snpo = equalsPartialOrdering[SiteNameSym]
    val ssnpo = optionPartialOrdering(equalsPartialOrdering[SiteStateNameSym])
    def lteq(x: SiteState, y: SiteState) =
      snpo.lteq(x.name, y.name) && ssnpo.lteq(x.state, y.state)
    def tryCompare(x: SiteState, y: SiteState) =
      (snpo.tryCompare(x.name, y.name),
       ssnpo.tryCompare(x.state, y.state)) match {
         case (Some(x), Some(y)) if x == y => Some(x)
         case _ => None
       }
  }    
  val lpo = new PartialOrdering[LinkState] {
    val lsnpo = optionPartialOrdering(equalsPartialOrdering[LinkStateNameSym])
    def lteq(x: LinkState, y: LinkState) = lsnpo.lteq(x.state, y.state)
    def tryCompare(x: LinkState, y: LinkState) =
      lsnpo.tryCompare(x.state, y.state)
  }
}

// trait KaSpaceContext extends LanguageContext {

//   // TODO: If we use an environment (aka symbol table), then these
//   // should be references to symbols (i.e. the AgentStateId?).  If
//   // e.g. Site states are optional, it should be an option of symbol
//   // reference, etc.

//   // TODO: Again, if we use a symbol table, then these should be
//   // references to symbols, probably including the geometry!  Are
//   // there any advantages to keeping geometry as non-symbols?  I.e. in
//   // the KaSpace paper, geometry is defined to be restricted to a
//   // finite subset of radii, positions and matrices, which suggests
//   // that we might allocate symbols for them.  Is there any reason why
//   // this should not be so?
//   type Radius = Double
//   case class Position(x: Double, y: Double, z: Double)
//   type Matrix = Vector[Vector[Double]] // dummy... at some later point we should use Scalala probably

//   // FIXME: should these be proper classes?
//   type AgentState = Pair[String, Option[Radius]]
//   type SiteState  = Pair[Option[String], Option[Position]]
//   type LinkState  = Pair[Option[String], Option[Matrix]]

//   val radiusPo = partialOrderingFromEquiv(Ordering.Double)
//   val posPo = partialOrderingFromEquiv(Equiv.universal[Position])
//   val orientationPo = partialOrderingFromEquiv(Equiv.universal[Matrix])

//   val apo = productPartialOrdering(KappaContext.apo, radiusPo)
//   val spo = productPartialOrdering(KappaContext.spo, posPo)
//   val lpo = productPartialOrdering(KappaContext.lpo, orientionPo)
// }

