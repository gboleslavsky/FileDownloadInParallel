import java.io.File
import java.net.URL
import java.lang.System.currentTimeMillis
import java.util.concurrent.Executors.newFixedThreadPool
import java.util.concurrent.TimeUnit
import org.apache.commons.io.FileUtils
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util._

object AsyncDownload extends App{
  // Initialize parameterized values
  case class Config(numConcurrentThreads: Int = 10,
                    numRetries: Int = 4,
                    timeBetweenRetries: FiniteDuration = FiniteDuration(4, TimeUnit.SECONDS),
                    pathToInput: String = "images.csv",
                    pathToOutputFolder: String = "src/main/resources/downloaded-images/")
  val config = Config()

  //FixedThreadPool guarantees that no more than numConcurrentThreads run at any time
  implicit val ec = ExecutionContext.fromExecutor(newFixedThreadPool(config.numConcurrentThreads))

  //source must be closed
  val source = Source.fromResource(config.pathToInput)
  val lines = Try(source.getLines.toSet) match {
    case Success(lines) => source.close()
      lines

    case Failure(e) => Try(source.close()) match { //throws an exception if source is null
      case _ => println("sCould not open " + config.pathToInput + ". Encountered this Exception:")
        e.printStackTrace()
    }
      //return empty set so the rest of the code does nothing when there is no source
      Set[String]()
  }

  //find and filter to keep only valid URLs and report invalid ones
  //csv format is campaign_id,creative_id,creative_url
  val allUrlStrings = lines.map(s => s.split(',')(2))
  val validUrlStrings = allUrlStrings.filter(_.contains('/'))
  val invalidUrlStrings = allUrlStrings diff validUrlStrings
  val numOfInvalidUrls = invalidUrlStrings.size
  if (numOfInvalidUrls > 0) {
    println(s"These $numOfInvalidUrls urls are invalid")
    invalidUrlStrings foreach println
    println
    println
  }

  def fileName(path: String) = path.substring(path.lastIndexOf('/') + 1)

  val notSet = -1L
  case class FileStats(url: String, start: Long, end: Long = notSet) {
    def name = fileName(url)
    def completed = end != notSet
    def path = config.pathToOutputFolder + name
    def timeToDownload: Double =  if (completed) (end - start) //in milliseconds
    else notSet
    def size = if (completed) FileUtils.sizeOf(new File(path)) else notSet
    override def toString = {if (completed)  s"File $name took $timeToDownload milliseconds to download. " +
      s"Its size is $size bytes"
    else            s"Nothing was downloaded from $url"}
  }

  //global immutable map for reporting results when finished
  var statsByUrl = Map[String, FileStats]()

  def startDownload(url: String) = {
    statsByUrl += (url -> FileStats(url, start = currentTimeMillis))
    //return pair with URL as the second value so statsByName can be updated with results
    Future(FileUtils.copyURLToFile(new URL(s"https://$url"),
      new File(config.pathToOutputFolder + fileName(url))),
      url)
  }

  import akka.pattern.after
  import akka.actor.{ActorSystem, Scheduler}
  implicit val s = ActorSystem().scheduler
  def retryWithFuture[T](f: => Future[T], retries: Int, delay: FiniteDuration)
                        (implicit ec: ExecutionContext, s: Scheduler): Future[T] = {
    f.recoverWith { case _ if retries > 0 => after[T](delay, s)(retryWithFuture[T](f, retries - 1, delay))
    }
  }

  def downloadGroupOfURLs(urls: Set[String]) = {
    val futures = urls.map(startDownload(_)).toList
    futures.foreach(f => {
      f.onComplete {
        case Success(pair) => val url = pair._2
          statsByUrl += (url -> statsByUrl(url).copy(end = currentTimeMillis()))

        case Failure(e) => {
          val fAfterFailure = retryWithFuture(f, config.numRetries, config.timeBetweenRetries)
          fAfterFailure.onComplete {
            case Success(pair) => {
              val url = pair._2
              statsByUrl +=
                (url -> statsByUrl(url).copy(end = currentTimeMillis()))
            }
            case Failure(e)
            =>
          }
        }
      }
    })
    futures
  }

  val successes = Future.sequence(
                                  validUrlStrings.grouped(config.numConcurrentThreads).
                                  flatMap(downloadGroupOfURLs(_)).
                                  map(_.transform(Success(_)))
                                )

  def printResults()={
    val results = statsByUrl.values.partition(_.completed )
    val successes = results._1
    val failures = results._2
    successes foreach println
    failures foreach println
  }

  successes.onComplete{
    case_ => printResults(); System.exit(0)
  }
}

