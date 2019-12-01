val chiselVersion = System.getProperty("chiselVersion", "3.2.0")
val scalaVer = System.getProperty("scalaVer", "2.11.12")

lazy val rosettaSettings = Seq (
  name := "rosetta_template",
  version := "0.1",
  scalaVersion := scalaVer,
  libraryDependencies ++= ( if (chiselVersion != "None" ) ("edu.berkeley.cs" %% "chisel3" % chiselVersion) :: Nil; else Nil),
  libraryDependencies += "com.novocode" % "junit-interface" % "0.10" % "test",
  libraryDependencies += "edu.berkeley.cs" %% "chisel-iotesters" % "1.3.0",
  libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVer
)


lazy val rosetta_template = (project in file(".")).settings(rosettaSettings: _*)