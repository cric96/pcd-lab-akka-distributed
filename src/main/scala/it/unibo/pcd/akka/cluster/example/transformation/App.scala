package it.unibo.pcd.akka.cluster.example.transformation

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.typed.Cluster
import com.typesafe.config.ConfigFactory

object App:
  object RootBehavior:
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      val cluster = Cluster(ctx.system)

      // We use roles, defined in config entry akka.cluster.roles, to help the cluster to organise work
      if (cluster.selfMember.hasRole("backend")) {
        val workersPerNode =
          ctx.system.settings.config.getInt("transformation.workers-per-node")
        (1 to workersPerNode).foreach { n =>
          ctx.spawn(Worker(), s"Worker$n")
        }
      }
      if (cluster.selfMember.hasRole("frontend")) {
        ctx.spawn(Frontend(), "Frontend")
      }
      Behaviors.empty
    }

  def startup(role: String, port: Int): Unit =
    // Override the configuration of the port and role
    val config = ConfigFactory
      .parseString(s"""
        akka.remote.artery.canonical.port=$port
        akka.cluster.roles = [$role]
        """)
      .withFallback(ConfigFactory.load("transformation"))

    ActorSystem[Nothing](RootBehavior(), "ClusterSystem", config)

  def main(args: Array[String]): Unit =
    // starting 2 frontend nodes and 3 backend nodes
    if (args.isEmpty) {
      startup("backend", 25251)
      startup("backend", 25252)
      startup("frontend", 0)
      startup("frontend", 0)
      startup("frontend", 0)
    } else {
      require(args.length == 2, "Usage: role port")
      startup(args(0), args(1).toInt)
    }
