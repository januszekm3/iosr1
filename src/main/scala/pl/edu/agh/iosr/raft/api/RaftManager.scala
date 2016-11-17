package pl.edu.agh.iosr.raft.api

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, PoisonPill, Props}
import pl.edu.agh.iosr.raft.ClientActor
import pl.edu.agh.iosr.raft.ClientActor.SetStateToRandomNode
import pl.edu.agh.iosr.raft.structure.Messages._
import pl.edu.agh.iosr.raft.structure.ServerNode

/**
  * @author lewap
  * @since 17.11.16
  */
class RaftManager extends Actor with ActorLogging {

  import RaftManager._

  var nodes: List[ActorRef] = List()
  var paths: List[ActorPath] = List()
  var client: Option[ActorRef] = None

  override def receive: Receive = {

    case Initialize(nodesQuantity) =>
      if (nodes.isEmpty) {
        log.info(s"Initializing $nodesQuantity nodes")
        nodes = initializeNodes(nodesQuantity)
        paths = nodes.map(_.path)
        client = Some(context.actorOf(ClientActor.props(paths)))
      }

    case KillNode(number) =>
      if (number >= 0 && number < nodes.size) {
        log.info(s"Killing ${nodeNameFrom(number)}")
        val nodeToKill = nodes(number)
        nodeToKill ! PoisonPill
      }

    case StartNode(number) =>
      if (number >= 0 && number < nodes.size) {
        val nodeName = nodeNameFrom(number)
        log.info(s"Trying to create $nodeName")
        context.actorOf(ServerNode.props(), nodeName)
      }

    case msg@SetStateToRandomNode(number) =>
      client.foreach(_ ! msg)

  }

  private def initializeNodes(quantity: Int): List[ActorRef] = {
    val iterator = (1 to quantity).iterator
    val nodesList = List.fill(quantity) {
      context.system.actorOf(ServerNode.props(), nodeNameFrom(iterator.next()))
    }

    val nodesSet = nodesList.toSet
    nodesSet foreach { node =>
      val otherNodes = nodesSet - node
      node ! AddNodes(otherNodes.map(_.path))
    }

    nodesList
  }

}

object RaftManager {

  def props(): Props =
    Props(new RaftManager())

  def nodeNameFrom(number: Int): String =
    "node" + number

  case class Initialize(nodesQuantity: Int)

  case class KillNode(number: Int)

  case class StartNode(number: Int)

}