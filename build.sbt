name := "Akka Dining Hell"

organization := "mc"

version := "0.1-SNAPSHOT"

scalaVersion := "2.12.6"

scalacOptions ++= Seq("-feature", "-deprecation")

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases"

 libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.5.16",
    "com.typesafe.akka" %% "akka-actor-typed" % "2.5.16",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    "com.typesafe.akka" %% "akka-testkit" % "2.5.16",
    "joda-time" % "joda-time" % "2.10"
  )

