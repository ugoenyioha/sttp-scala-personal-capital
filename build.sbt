name := "sttp-scala-personal-capital"

version := "0.1"

scalaVersion := "2.12.10"

val Http4sVersion = "0.21.0-M5"
val CirceVersion = "0.12.3"
val Specs2Version = "4.1.0"
val LogbackVersion = "1.2.3"
val pureConfigVersion = "0.12.1"

libraryDependencies ++= Seq(
	"io.circe"        %% "circe-generic"       		% CirceVersion,
	"io.circe"        %% "circe-literal"       		% CirceVersion,
	"io.circe"        %% "circe-optics"       		% "0.12.0",
	"io.circe"        %% "circe-parser"       		% CirceVersion,
	"org.specs2"      %% "specs2-core"         		% Specs2Version % "test",
	"ch.qos.logback"  %  "logback-classic"     		% LogbackVersion,
	"com.github.pureconfig" %% "pureconfig" 			% pureConfigVersion,
	"com.github.pureconfig" %% "pureconfig-cats-effect" % pureConfigVersion,
	"org.typelevel" %% "cats-core" % "2.0.0",
	"com.softwaremill.sttp.client" %% "http4s-backend" % "2.0.0-RC5",
	"com.softwaremill.sttp.client" %% "akka-http-backend" % "2.0.0-RC5",
	"com.typesafe.akka" %% "akka-stream" % "2.5.11",
	"com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
)

addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.10.3")
addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")

