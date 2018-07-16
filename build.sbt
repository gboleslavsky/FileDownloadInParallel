name := "DownloadFiles"

version := "0.1"

scalaVersion := "2.12.6"
fork in run := true
libraryDependencies ++= Seq(
  "com.typesafe.akka" %%  "akka-actor" % "2.5.12",
   "commons-io" % "commons-io" % "2.5"

)