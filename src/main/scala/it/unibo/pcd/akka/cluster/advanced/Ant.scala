package it.unibo.pcd.akka.cluster.advanced

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.*
import akka.actor.typed.scaladsl.adapter.*
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import it.unibo.pcd.akka.Message
import it.unibo.pcd.akka.cluster.advanced.AntsRender.Render

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

/** A simple actor that randomly moves around an empty environment. For each move, it send a message to a sequence of
  * frontend interested in ant movements.
  */
object Ant:

  sealed trait Command extends Message // Enum needs an ad-hoc serializers...
  case object Stop extends Command
  private case class FrontendsRegistered(renders: List[ActorRef[AntsRender.Render]])
      extends Command // ad-hoc message to handle frontend
  private case object Move extends Command // and ADT enable also private messages
  val Key = ServiceKey[Stop.type]("Ant")

  def apply(
      position: (Int, Int),
      period: FiniteDuration,
      frontends: List[ActorRef[AntsRender.Render]] = List.empty
  )(using random: Random): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      ctx.spawnAnonymous(manageFrontend(ctx.self)) // spawn the logic that handles subscribes of new frontend.
      ctx.system.receptionist ! Receptionist.Register(Ant.Key, ctx.self)
      Behaviors.withTimers { timers =>
        timers.startTimerAtFixedRate(Move, period)
        antLogic(position, ctx, frontends)
      }
    }
  // Ad-hoc logic to handle subscribe
  def manageFrontend(sendReplyTo: ActorRef[Command]): Behavior[Receptionist.Listing] = {
    Behaviors.setup[Receptionist.Listing] { ctx =>
      ctx.system.receptionist ! Receptionist.Subscribe(AntsRender.Service, ctx.self)
      Behaviors.receiveMessage { case msg: Receptionist.Listing =>
        sendReplyTo ! FrontendsRegistered(msg.serviceInstances(AntsRender.Service).toList)
        Behaviors.same
      }
    }
  }
  // Main logic, each period the ant changes its position (using the random generator in the context)
  def antLogic(
      position: (Int, Int),
      ctx: ActorContext[Command],
      frontends: List[ActorRef[AntsRender.Render]] = List.empty
  )(using random: Random): Behavior[Command] = Behaviors.receiveMessage {
    case FrontendsRegistered(newRenders) =>
      ctx.log.info(s"New frontend! $newRenders")
      if (newRenders == frontends)
        Behaviors.same
      else
        antLogic(position, ctx, newRenders)
    case Move =>
      val (deltaX, deltaY) = ((random.nextGaussian * 5).toInt, (random.nextGaussian * 5).toInt)
      val (x, y) = position
      frontends.foreach(_ ! AntsRender.Render(x, y, ctx.self))
      //ctx.log.info(s"move from ${(x, y)}, ${ctx.self.path}")
      antLogic((x + deltaX, y + deltaY), ctx, frontends)
    case Stop => Behaviors.stopped
  }
