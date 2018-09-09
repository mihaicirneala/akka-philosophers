package typed.basic

import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration._

object Chopstick {
  import Philosopher._

  sealed trait ChopstickProtocol
  final case class PutChopstick(seat: TableSeat) extends ChopstickProtocol
  final case class TakeChopstick(seat: TableSeat) extends ChopstickProtocol

  val available: Behavior[ChopstickProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case TakeChopstick(seat) =>
        seat.philosopher ! ChopstickTaken(ctx.self, seat)
        Chopstick.taken
      case _ => Behaviors.same
    }
  }

  val taken: Behavior[ChopstickProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case TakeChopstick(otherSeat) =>
        otherSeat.philosopher ! ChopstickBusy(ctx.self, otherSeat)
        Behaviors.same
      case PutChopstick(seat) => Chopstick.available
      case _ => Behaviors.same
    }
  }
}

object Philosopher {
  import Chopstick._

  case class TableSeat(philosopher: ActorRef[PhilosopherProtocol],
                       leftChopstick: ActorRef[ChopstickProtocol],
                       rightChopstick: ActorRef[ChopstickProtocol])

  sealed trait PhilosopherProtocol
  final case class Eat(seat: TableSeat) extends PhilosopherProtocol
  final case class Think(seat: TableSeat) extends PhilosopherProtocol
  final case class ChopstickBusy(chopstick: ActorRef[ChopstickProtocol], seat: TableSeat) extends PhilosopherProtocol
  final case class ChopstickTaken(chopstick: ActorRef[ChopstickProtocol], seat: TableSeat) extends PhilosopherProtocol

  //When a philosopher is thinking it can become hungry
  //and try to pick up its chopsticks and eat
  val thinking: Behavior[PhilosopherProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case Eat(seat) =>
//        println(s"${seat.philosopher.path.name} tries to pick up its chopsticks and eat")
        seat.leftChopstick ! TakeChopstick(seat)
        seat.rightChopstick ! TakeChopstick(seat)
        hungry
      case _ => Behaviors.same
    }
  }

  //When a philosopher is hungry it tries to pick up its chopsticks and eat
  //When it picks one up, it goes into wait for the other
  //If the philosophers first attempt at grabbing a chopstick fails,
  //it starts to wait for the response of the other grab
  val hungry: Behavior[PhilosopherProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case ChopstickTaken(chopstick, seat) =>
//        println(s"${seat.philosopher.path.name} succeeds in taking ${chopstick.path.name}")
        waitingFor
      case ChopstickBusy(chopstick, seat) =>
//        println(s"${seat.philosopher.path.name} finds out that the first ${chopstick.path.name} is busy")
        deniedChopstick
      case _ => Behaviors.same
    }
  }
  //When a philosopher is waiting for the last chopstick it can either obtain it
  //and start eating, or the other chopstick was busy, and the philosopher goes
  //back to think about how he should obtain his chopsticks :-)
  def waitingFor: Behavior[PhilosopherProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case ChopstickTaken(chopstickToWaitFor, seat) =>
        println("%s has picked up %s and %s and starts to eat".format(seat.philosopher.path.name, seat.leftChopstick.path.name, seat.rightChopstick.path.name))
        ctx.schedule(5.seconds, ctx.self, Think(seat))
        eating
      case ChopstickBusy(chopstick, seat) =>
        val otherChopstick = if (seat.leftChopstick == chopstick) seat.rightChopstick else seat.leftChopstick
        otherChopstick ! PutChopstick(seat)
//        println(s"${seat.philosopher.path.name} finds out that the second ${chopstick.path.name} is busy")
        ctx.schedule(10.milliseconds, ctx.self, Eat(seat))
        thinking
      case _ => Behaviors.same
    }
  }

  //When the results of the other grab comes back,
  //he needs to put it back if he got the other one.
  //Then go back and think and try to grab the chopsticks again
  def deniedChopstick: Behavior[PhilosopherProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case ChopstickTaken(chopstick, seat) =>
        chopstick ! PutChopstick(seat)
        ctx.schedule(10.milliseconds, ctx.self, Eat(seat))
        thinking
      case ChopstickBusy(chopstick, seat) =>
        ctx.schedule(10.milliseconds, ctx.self, Eat(seat))
        thinking
      case _ => Behaviors.same
    }
  }

  //When a philosopher is eating, he can decide to start to think,
  //then he puts down his chopsticks and starts to think
  def eating: Behavior[PhilosopherProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case Think(seat) =>
        seat.leftChopstick ! PutChopstick(seat)
        seat.rightChopstick ! PutChopstick(seat)
        println("%s puts down his chopsticks and starts to think".format(seat.philosopher.path.name))
        ctx.schedule(5.seconds, ctx.self, Eat(seat))
        thinking
      case _ => Behaviors.same
    }
  }

  def idle: Behavior[PhilosopherProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case Think(seat) =>
        println("%s starts to think".format(seat.philosopher.path.name))
        ctx.schedule(5.seconds, ctx.self, Eat(seat))
        thinking
      case _ => Behaviors.same
    }
  }

}

object DiningPhilosophers {

  final case class Start()

  def main(args: Array[String]): Unit = {
    val creator: Behavior[Start] = Behaviors.setup { context =>
      //Create 5 philosophers and assign them their left and right chopstick
      val chopsticks = for (i <- 1 to 5) yield context.spawn(Chopstick.available, "Chopstick-" + i)
      val philosophers = for (i <- 1 to 5) yield context.spawn(Philosopher.idle, "Philosopher-" + i)
      val seats = for (i <- 0 to 4) yield {
        Philosopher.TableSeat(philosophers(i), chopsticks(i), chopsticks((i + 1) % 5))
      }

      Behaviors.receiveMessage { _ =>
        seats.foreach { seat =>
          seat.philosopher ! Philosopher.Think(seat)
        }
        Behaviors.same
      }
    }

    val system: ActorSystem[Start] = ActorSystem(creator, "creator")
    system ! Start()
  }
}
