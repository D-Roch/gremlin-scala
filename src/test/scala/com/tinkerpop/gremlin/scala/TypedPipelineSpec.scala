package com.tinkerpop.gremlin.scala

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers

import shapeless._
import syntax.std.traversable._
import ops.traversable.FromTraversable
import ops.hlist._

class TypedPipelineSpec extends FunSpec with ShouldMatchers {

  it("integrates with T3") {
    import com.tinkerpop.blueprints.Edge
    import com.tinkerpop.blueprints.Vertex
    import com.tinkerpop.gremlin.pipes.MapPipe
    import com.tinkerpop.gremlin.pipes.util.Holder
    import com.tinkerpop.gremlin.pipes.{Pipe ⇒ GremlinPipe, Pipeline ⇒ GremlinPipeline}
    import com.tinkerpop.blueprints.Direction
    import java.util.function.{Function ⇒ JFunction}

    val gremlinPipeline: GremlinPipeline[Nothing, Nothing] = ???
    // need to wrap GremlinPipe into Pipe for type variance of I
    implicit class Pipe[-I,O](pipe: GremlinPipe[I,O])

    // H is the out type of the _last_ pipe, i.e. the result type of the whole pipeline!
    case class Pipeline[H, T <: HList](pipes: Pipe[_, H] :: T) {
      type CurrentPipes = Pipe[_, H] :: T
    }
    implicit class EdgeSteps[H <: Edge, T <: HList](pipeline: Pipeline[H, T])
      extends Pipeline[H, T](pipeline.pipes) {

      def inV: Pipeline[Vertex, CurrentPipes] = {
        def func = new JFunction[Holder[Edge], Vertex] {
          override def apply(h: Holder[Edge]): Vertex = h.get.getVertex(Direction.IN)
        }
        val inVPipe: Pipe[Edge, Vertex] = new MapPipe[Edge, Vertex](gremlinPipeline, func)
        addPipe(pipes, inVPipe)
      }
    }



    def startPipeline[H,T](pipe: Pipe[H,T]) = Pipeline(pipe :: HNil)
    //H1: the old end type of the pipeline
    //H2: the new end type of the pipeline
    def addPipe[H2, H1, T <: HList](
      pipes: Pipe[_, H1] :: T, 
      next: Pipe[H1, H2]): Pipeline[H2, Pipe[_, H1] :: T] = Pipeline(next :: pipes)
    //def addPipe[H2, H1, T <: HList](
      //pipes: Pipe[_, H1] :: T,
      //pipeConstr: Iterator[H1] ⇒ Pipe[H1, H2]): Pipeline[H2, Pipe[_, H1] :: T] = {
        ////val next = pipeConstr(pipes.head.iter)
        //val next: Pipe[H1, H2] = ???
        //Pipeline(next :: pipes)
    //}


  }

  it("fifth try: pipes as hlist") {
    trait Element { def id: Int }
    case class Vertex(id: Int, outE: Iterator[Edge]) extends Element
    case class Edge(id: Int, label: String, inV: Vertex) extends Element

    trait Pipe[-I,O] { def iter: Iterator[O] }
    case class InVPipe(starts: Iterator[Edge]) extends Pipe[Edge, Vertex] {
      val iter = starts map { _.inV }
    }
    case class OutEPipe(starts: Iterator[Vertex]) extends Pipe[Vertex, Edge] {
      val iter = starts flatMap { _.outE }
    }
    case class LabelPipe(starts: Iterator[Edge]) extends Pipe[Edge, String] {
      val iter = starts map { _.label }
    }
    case class IdPipe(starts: Iterator[Element]) extends Pipe[Element, Int] {
      val iter = starts map { _.id }
    }

    // H is the out type of the _last_ pipe, i.e. the result type of the whole pipeline!
    case class Pipeline[H, T <: HList](pipes: Pipe[_, H] :: T) {
      type CurrentPipes = Pipe[_, H] :: T
      def toList: List[H] = pipes.head.iter.toList
    }
    implicit class EdgeSteps[H <: Edge, T <: HList](pipeline: Pipeline[H, T]) extends Pipeline[H, T](pipeline.pipes) {
      def inV: Pipeline[Vertex, CurrentPipes] = addPipe(pipes, InVPipe)
      def label: Pipeline[String, CurrentPipes] = addPipe(pipes, LabelPipe)
    }
    implicit class VertexSteps[H <: Vertex, T <: HList](pipeline: Pipeline[H, T]) extends Pipeline[H, T](pipeline.pipes) {
      def outE: Pipeline[Edge, Pipe[_, H] :: T] = addPipe(pipes, OutEPipe)
    }

    def startPipeline[H,T](pipe: Pipe[H,T]) = Pipeline(pipe :: HNil)
    //H1: the old end type of the pipeline
    //H2: the new end type of the pipeline
    def addPipe[H2, H1, T <: HList](
      pipes: Pipe[_, H1] :: T,
      pipeConstr: Iterator[H1] ⇒ Pipe[H1, H2]): Pipeline[H2, Pipe[_, H1] :: T] = {
        val next = pipeConstr(pipes.head.iter)
        Pipeline(next :: pipes)
    }


    /* test setup:
     *     |e1-|
     * v1->|   |->v2
     *     |e2-|
     */
    def v(id: Int): Vertex = id match {
      case 1 ⇒ Vertex(id, outE = List(e(1), e(2)).iterator)
      case 2 ⇒ Vertex(id, outE = Nil.iterator)
    }
    def e(id: Int): Edge = id match {
      case 1|2 ⇒ Edge(id, label = s"label:$id", inV = v(2))
    }
    def edgeStartPipe = new Pipe[Nothing, Edge] {
      val iter = List(e(1)).iterator
    }
    def vertexStartPipe = new Pipe[Nothing, Vertex] {
      val iter = List(v(1)).iterator
    }
    def edgePipeline = startPipeline(edgeStartPipe)
    def vertexPipeline = startPipeline(vertexStartPipe)

    // these compile
    edgePipeline.inV
    vertexPipeline.outE       
    vertexPipeline.outE.inV
    vertexPipeline.outE.label 

    //println(vertexPipeline.toList)
    //println(vertexPipeline.outE.toList)
    //println(vertexPipeline.outE.inV.toList)
    //println(vertexPipeline.outE.inV.outE.toList)

    // these don't compile - and they shouldn't ;)
    //vertexPipeline.inV
    //edgePipeline.inV.inV    
    //vertexPipeline.outE.outE
    //vertexPipeline.label
  }

  ignore("forth try: Pipeline as HList") {
    //case class Pipeline[H, T <: HList](pipes :Pipe[_, H]::T) {
      //def path: H::T = {
        //val iter1 = pipes(0).testObjects. :: HNil
        //combine iterators to streams:
        //???
      //}

      // testObjects is only passed in to simplify things for now
      //def out[E](testObjects: List[E]) = Pipeline(Pipe(testObjects) :: pipes)
      //def path: HList[A] = List(1, "one").toHList[A].get//OrElse(HNil)
      //def toList = pipes.head
      //def getHead1[B <: HList :IsHCons](pipes: B) = pipes.head
      //T3 semantics: Pipline extends Pipe
    //}

    //val emptyPipe = Pipe(Nil)
    //val start = Pipeline(emptyPipe :: HNil)
    //val strings = List[String]("one", "two")
    //val stringPipe = Pipe(strings)
    //val ints = List[Int](1,2)
    //val intPipe = Pipe(ints)
    //val pipeline = Pipeline(stringPipe :: HNil)
    //val pipeline = Pipeline(strings :: ints :: HNil)
    //val pipeline = start.out(strings)
    //val path: String :: HNil  = pipeline.path
    // val path1 = path.head
    // val path2 = path.tail.head
    // assert(path1 == strings(0) :: HNil)
    // assert(path2 == strings(1) :: HNil)
    // assert(path1 == strings(0) :: ints(0) :: HNil)
    // assert(path2 == strings(1) :: ints(1) :: HNil)

    //val headPipe: Pipe[String] = getHead(pipeline.pipes)
    //val headPipe2: Pipe[String] = pipeline.toList


    /** TODOs:
    path with one pipe: replace ??? with impl
    path with two pipes
      Pipe[Graph,Vertex] :: Pipe[Vertex,Edge] :: Pipe[Edge,Vertex]
    Pipe needs to hold two types: I/O
    out: make work again
    out: append to hlist, not prepend
    out: use peano types to stop compiler if types of pipes don't fit together
    let path produce the content lazily, not eagerly
      use sink and producer? iteratees? scalaz?
    zip pipes to get real path instead of dummy list
    reverse types and pipes on each step? flatten hlist type?
      https://groups.google.com/forum/#!searchin/shapeless-dev/append/shapeless-dev/gOXAbvGqEv8/hgqZmqmiLDAJ
    pipe: extend some tinkerpop type?
    have three types of pipelines? VertexPipeline (that contains outE etc.), EdgePipeline (that contains inV etc.) and Pipeline that contains common stuff like filter?
      shouldn't be necessary if the compiler detects that inV.inV doesn't work
    */

  }

  ignore("third try: nested Pipes") {
    //problem: cannot nest types other than vertex/edge easily as InVPipe and OutEPipe are explicitly bound to these types
    //easy: Filter, label, ...
    //hard: path, back, ...

    case class Vertex(id: Int, outE: Iterator[Edge])
    case class Edge(id: Int, inV: Vertex)

    trait Pipe[I,O] {
      //def starts: Iterator[I]
      def iter: Iterator[O]
      def next: O = {
        println(s"pipe.next: $iter.next")
        iter.next
      }
      def hasNext = {
        println(s"pipe.hasNext: $iter.hasNext")
        iter.hasNext
      }
    }

    case class InVPipe(prev: Pipe[_, Edge]) extends Pipe[Edge, Vertex] {
      val iter = prev.iter map { _.inV }
    }
    case class OutEPipe(prev: Pipe[_, Vertex]) extends Pipe[Vertex, Edge] {
      val iter = prev.iter flatMap { _.outE }
    }

    def v(id: Int): Vertex = id match {
      case 1 ⇒ Vertex(id, outE = List(e(1), e(2)).iterator)
      case 2 ⇒ Vertex(id, outE = Nil.iterator)
    }
    def e(id: Int): Edge = id match {
      case 1|2 ⇒ Edge(id, inV = v(2))
    }

    val startPipe = new Pipe[Nothing, Vertex] {
      val iter = List(v(1)).iterator
    }
    val outE = OutEPipe(startPipe)
    val inV = InVPipe(outE)

    //startPipe.iter foreach println
    //outE.iter foreach println
    //inV.iter foreach println
  }

  ignore("second try") {
    type Vertex = Int
    type Edge = Float
    type Graph = String

    trait Pipe[S,+E] {}

    trait Pipeline[+T] {
      type head <: Pipe[_,T]
      type tail <: Pipeline[T]
    }

    trait NilPipeline extends Pipeline[Any] {
      override def toString = "NilPipeline"
    }
    object NilPipeline extends NilPipeline

    trait StartPipe[E] extends Pipe[Graph, E] {
      override def toString = "StartPipe"
    }
    trait VertexPipe[S] extends Pipe[S, Vertex] {
      override def toString = "VertexPipe"
    }
    trait EdgePipe[S] extends Pipe[S, Edge] {
      override def toString = "EdgePipe"
    }

    case class ::[S, E](head: Pipe[S,E], tail: Pipeline[E]) extends Pipeline[S] {
      override def toString = s"$head :: $tail"
    }

    val startPipe = new StartPipe[Vertex]{} //Pipe[Graph, Vertex]
    val edgePipe = new EdgePipe[Vertex]{} //Pipe[Vertex, Edge]
    val p1 = ::(startPipe, NilPipeline)
    val p2 = ::(edgePipe, p1)
    println(p1)
    println(p2)

    // TODOs:
    //ensure cannot combine wrong pipes
    //dummy version of Pipe and Pipeline methods
}


  //ignore("first try") {
    //sealed trait Pipe {
      //type Head // <: Pipeline //idea: have Pipeline[Vertex]?
      //type Tail <: Pipe

      //def get: Head
    //}

    //final case class VertexPipe extends Pipe {
      //type Head = Vertex
      ////def out: TypedPipe[S, Vertex] = ???
      ////def outE: TypedPipe[S, Edge] = ???
      //override def get = ???
    //}

    //sealed class PipeNil extends Pipe{
      //type Head = Unit
      //type Tail = PipeNil

      //override def get = Unit
    //}

    //sealed trait HList {
      //type Head
      //type Tail <: HList
    //}

    //sealed class HNil extends HList {
      //type Head = Nothing
      //type Tail = HNil
      //def ::[T](v : T) = HCons(v, this)
    //}
    //case object HNil extends HNil

    //final case class HCons[H, T <: HList](head : H, tail : T) extends HList {
      //type Head = H
      //type Tail = T
      //def ::[T](v : T) = HCons(v, this)
      
      ////type Fun[T] = H => tail.Fun[T]
      ////def apply[T](f: Fun[T]): T = tail( f(head) )

      ////override def toString = head + " :: " + tail
    //}

    //val p = new VertexPipe
    //println(p)
  //}

}
