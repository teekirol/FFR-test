package example

import com.typesafe.scalalogging._
import scala.io.Source
import scala.io.BufferedSource
import scala.collection.mutable.ListBuffer
import java.io.FileNotFoundException
import org.joda.time.{DateTime, Period, PeriodType}
import org.joda.time.format.DateTimeFormat

object Main extends App {

    override def main(args: Array[String]) = {
        try {
            val lines = args.headOption.map(Source.fromFile(_).getLines).getOrElse(Iterator.empty);
            val readings = parseCSV(lines);
            val testReadings = readingsDuringTest(readings)
            new FFRAnalysis(testReadings).run
        } catch {
            case e: FileNotFoundException => println("Invalid data readings file")
        }
    }

    def parseCSV(data: Iterator[String]): Iterator[Reading] = {
        val DATE_FORMATTER = DateTimeFormat.forPattern("#YYYY-MM-dd HH:mm:ss:SSS")
        val TIMESTAMP_IDX = 0;
        val FREQUENCY_IDX = 1;
        val PHASE1_IDX = 6;
        val PHASE2_IDX = 7;
        val PHASE3_IDX = 8;
        val RELAY_STATUS_IDX = 11;
        try {
            data.map(r => {
                val readings = r.split(",");
                val ts = DateTime.parse(readings(TIMESTAMP_IDX).trim, DATE_FORMATTER);
                val freq = readings(FREQUENCY_IDX).toFloat;
                val energy = readings(PHASE1_IDX).toFloat + readings(PHASE2_IDX).toFloat + 
                    readings(PHASE3_IDX).toFloat;
                val relayStatus = readings(RELAY_STATUS_IDX);
                Reading(ts, freq, energy, relayStatus)
            });
        } catch {
            case e: ArrayIndexOutOfBoundsException => {
                println("Reading is missing a column")
                Iterator.empty
            }
        }
    }

    // Find the start of the test frequency profile
    // The start of the profile consists of 10 frequency readings of exactly 50.00 Hz, +/- 0.01 Hz
    // 49.99 to 50.01
    def readingsDuringTest(readings: Iterator[Reading]): Stream[Reading] = {

        val testReadings = new ListBuffer[Reading]();
        
        val testFrequencyReadingMin = 50 - 0.01;
        val testFrequencyReadingMax = 50 + 0.01;
        while(readings.hasNext && testReadings.length < 10) {
            val r = readings.next
            if(r.frequency >= testFrequencyReadingMin && r.frequency <= testFrequencyReadingMax) {
                testReadings.append(r)
            } else {
                testReadings.clear()
            }
        }

        // Find the end of the test frequency profile
        // 35 minutes after the start
        val testDurationMins = 35
        val testEnd = testReadings.headOption.map(_.ts.plusMinutes(testDurationMins))
        return testReadings.toStream #::: testEnd.map(te => readings.takeWhile(_.ts.isBefore(te)).toStream).getOrElse(Stream.Empty)

    }

}

case class Reading(ts: DateTime, frequency: Float, energy: Float, relayStatus: String) extends LazyLogging {
    val RELAY_ON = "on$"
    val RELAY_OFF = "off$"
    def relayOn = relayStatus.toLowerCase match {
        case RELAY_ON => Some(true)
        case RELAY_OFF => Some(false)
        case _ => logger.error("Malformed data at " + ts); None 
    }
}

case class ReadingWithNonCumulativeEnergy(ts: DateTime, energy: Float)

class FFRAnalysis(readings: Stream[Reading]) extends LazyLogging {  

    val testStart: Option[DateTime] = readings.headOption.map(_.ts)

    val frequencyDrop = readings.find(_.frequency < 49.7).map(_.ts)
    
    val relaySwitch: Option[DateTime] = readings.find(_.relayOn == Some(true)).map(_.ts)

    // Recompute readings with power as difference from last reading
    val readingsNonCumulative = readings.zip(readings.drop(1)).map {
        case (r1: Reading, r2: Reading) => {
            ReadingWithNonCumulativeEnergy(r2.ts, r2.energy - r1.energy)
        }
    }

    // From the frequency drop, how long until relay turns off
    def timeTilRelayOn: Option[Long] =
        for {
            start   <- frequencyDrop
            end     <- readings.find(_.relayOn == Some(true)).map(_.ts)
        } yield {
            end.getMillis - start.getMillis
        }

    // The trigger condition for initiating an FFR event is the supply frequency dropping below 49.7 Hz
    // The relay controlling the device must switch within 400ms of detecting this trigger condition
    def relaySwitchedInTime: Option[Boolean] = 
        relaySwitch.map(switch =>
            frequencyDrop.map(triggerTime => {
                switch.getMillis - triggerTime.getMillis <= 400
            }).getOrElse(false)
        )   

    // The device must shed the expected load within 30 seconds of the relay switching
    // Find the first reading after the 30s and check if it's zero
    def loadShedInTime: Option[Boolean] =
        for {
            switch  <- relaySwitch
            reading <- readingsNonCumulative.find(_.ts.isAfter(switch.plusSeconds(30)))
        } yield {
            println("\nReading after relay switch + 30s: " + reading)
            reading.energy <= 0
        }

    val offStart: Option[ReadingWithNonCumulativeEnergy] = relaySwitch.flatMap(switch => {
        val readingsDuringGracePeriod = readingsNonCumulative.filter(r => r.ts.isAfter(switch) && r.ts.isBefore(switch.plusSeconds(30)))
        readingsDuringGracePeriod.reverse.takeWhile(_.energy == 0).headOption
    })

    // The device must remain turned-down for 30 minutes
    // Find when the device turned off, then check if it remained off for 30 mins
    def offForRequiredTime: Option[Boolean] =
        offStart.flatMap(off => {
            val firstOn = readingsNonCumulative.find(r => r.ts.isAfter(off.ts) && r.energy > 0)
            println("\nOn again at: " + firstOn.getOrElse("--"))
            firstOn.map(_.ts.isAfter(off.ts.plusMinutes(30)))
        })

    // After 30 minutes, the device must start running again
    def onAfterRequiredtime: Option[Boolean] =       
         offStart.flatMap(off => {
            val requiredOnAgain = off.ts.plusMinutes(30)
            val firstReadingAfterThirtyMins = readingsNonCumulative.find(_.ts.isAfter(requiredOnAgain))
            println("\nFirst reading after 30 mins: " + firstReadingAfterThirtyMins.getOrElse("--"))
            firstReadingAfterThirtyMins.map(_.energy > 0)
        })

    def run = {
        println("\nTest started at " + testStart.getOrElse("--"))
        println("\nFrequency dropped at " + frequencyDrop.getOrElse("--"))
        println("\nRelay switched at " + relaySwitch.getOrElse("--"))
        println("\nTurned off at: " + offStart.getOrElse("--"))
        println("\nTest ended at " + readings.lastOption.map(_.ts).getOrElse("--"))
        println("\n============== FFR REQUIREMENTS ============")
        println("\nTime til relay switched: " + timeTilRelayOn.getOrElse("--") + "ms")
        println("\nRelay switched in time? " + relaySwitchedInTime.getOrElse("--"))
        println("\nLoad shed in time? " + loadShedInTime.getOrElse("--"))
        println("\nRemained off for required time? " + offForRequiredTime.getOrElse("--"))
        println("\nTurned on after required time? " + onAfterRequiredtime.getOrElse("--"))
    }

}