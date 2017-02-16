name := "monzo-roundup"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  cache,
  ws,
  "org.reactivemongo" %% "play2-reactivemongo" % "0.12.1",
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test
)
